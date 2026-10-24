package com.riseai.escrow;

import com.riseai.audit.AuditService;
import com.riseai.common.Codes;
import com.riseai.common.Money;
import com.riseai.common.exception.ApplicationException;
import com.riseai.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class SimulatedEscrowEngine implements EscrowEngine {
    private static final Set<EscrowStatus> ACTIVE_ONLY = EnumSet.of(EscrowStatus.ACTIVE);
    private static final Set<EscrowStatus> ANY = EnumSet.allOf(EscrowStatus.class);

    private final EscrowAccountRepository accountRepository;
    private final EscrowLedgerRepository ledgerRepository;
    private final AuditService auditService;

    @Override
    public EscrowAccount openAccount(UUID campaignId, String currency) {
        return accountRepository.findByCampaignIdAndCurrency(campaignId, currency).orElseGet(() -> {
            EscrowAccount account = new EscrowAccount();
            account.setCampaignId(campaignId);
            account.setCurrency(currency);
            account.setEscrowStatus(EscrowStatus.ACTIVE);
            accountRepository.save(account);
            auditService.transition(null, "ESCROW_ACCOUNT_OPENED", "ESCROW_ACCOUNT", account.getId(), null, EscrowStatus.ACTIVE, "Simulated escrow account opened");
            return account;
        });
    }

    @Override
    public void changeStatus(UUID campaignId, String currency, EscrowStatus status, UUID actorId, String reason) {
        accountRepository.lockByCampaignIdAndCurrency(campaignId, currency)
                .filter(account -> account.getEscrowStatus() != status)
                .ifPresent(account -> {
                    EscrowStatus previous = account.getEscrowStatus();
                    account.setEscrowStatus(status);
                    auditService.transition(actorId, "ESCROW_STATUS_CHANGED", "ESCROW_ACCOUNT", account.getId(), previous, status, reason);
                });
    }

    @Override
    public EscrowLedgerEntry recordContribution(UUID campaignId, String currency, UUID donationId, BigDecimal amount, UUID actorId) {
        BigDecimal value = Money.requirePositive(amount);
        String key = "CONTRIBUTION:" + donationId;
        EscrowAccount account = lockFor(campaignId, currency, ACTIVE_ONLY, key);
        account.setTotalConfirmedContributions(account.getTotalConfirmedContributions().add(value));
        account.setAvailableBalance(account.getAvailableBalance().add(value));
        return append(account, LedgerTransactionType.CONTRIBUTION_CONFIRMED, LedgerDirection.CREDIT, value, key,
                donationId, null, null, "DONATION", donationId, actorId, "Simulated contribution confirmed");
    }

    @Override
    public EscrowLedgerEntry reserve(UUID campaignId, String currency, UUID milestoneId, UUID disbursementId, BigDecimal amount, UUID actorId) {
        BigDecimal value = Money.requirePositive(amount);
        String key = "RESERVE:" + disbursementId;
        EscrowAccount account = lockFor(campaignId, currency, ACTIVE_ONLY, key);
        if (account.getAvailableBalance().compareTo(value) < 0) {
            throw new ApplicationException(ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE,
                    "Available balance " + account.getAvailableBalance().toPlainString() + " is lower than requested " + value.toPlainString());
        }
        account.setAvailableBalance(account.getAvailableBalance().subtract(value));
        account.setTotalReserved(account.getTotalReserved().add(value));
        return append(account, LedgerTransactionType.FUNDS_RESERVED, LedgerDirection.NEUTRAL, value, key,
                null, milestoneId, disbursementId, "DISBURSEMENT_REQUEST", disbursementId, actorId, "Funds reserved for disbursement request");
    }

    @Override
    public EscrowLedgerEntry cancelReservation(UUID campaignId, String currency, UUID milestoneId, UUID disbursementId, BigDecimal amount, UUID actorId) {
        BigDecimal value = Money.requirePositive(amount);
        String key = "RESERVATION_CANCEL:" + disbursementId;
        EscrowAccount account = lockFor(campaignId, currency, ANY, key);
        if (account.getTotalReserved().compareTo(value) < 0) {
            throw new IllegalStateException("Reserved balance is lower than the reservation being cancelled");
        }
        account.setTotalReserved(account.getTotalReserved().subtract(value));
        account.setAvailableBalance(account.getAvailableBalance().add(value));
        return append(account, LedgerTransactionType.RESERVATION_CANCELLED, LedgerDirection.NEUTRAL, value, key,
                null, milestoneId, disbursementId, "DISBURSEMENT_REQUEST", disbursementId, actorId, "Reservation returned to available balance");
    }

    @Override
    public EscrowLedgerEntry release(UUID campaignId, String currency, UUID milestoneId, UUID disbursementId, BigDecimal amount, UUID actorId) {
        BigDecimal value = Money.requirePositive(amount);
        String key = "RELEASE:" + disbursementId;
        EscrowAccount account = lockFor(campaignId, currency, ACTIVE_ONLY, key);
        if (account.getTotalReserved().compareTo(value) < 0) {
            throw new IllegalStateException("Reserved balance is lower than the amount being released");
        }
        account.setTotalReserved(account.getTotalReserved().subtract(value));
        account.setTotalDisbursed(account.getTotalDisbursed().add(value));
        return append(account, LedgerTransactionType.FUNDS_RELEASED, LedgerDirection.DEBIT, value, key,
                null, milestoneId, disbursementId, "DISBURSEMENT_REQUEST", disbursementId, actorId, "Simulated release; no real transfer was executed");
    }

    @Override
    public EscrowLedgerEntry recordRefund(UUID campaignId, String currency, UUID donationId, UUID refundRequestId, BigDecimal amount, UUID actorId) {
        BigDecimal value = Money.requirePositive(amount);
        String key = "REFUND:" + refundRequestId;
        EscrowAccount account = lockFor(campaignId, currency, ANY, key);
        if (account.getAvailableBalance().compareTo(value) < 0) {
            throw new ApplicationException(ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE, "Available balance is insufficient for this refund");
        }
        account.setAvailableBalance(account.getAvailableBalance().subtract(value));
        account.setTotalRefunded(account.getTotalRefunded().add(value));
        return append(account, LedgerTransactionType.REFUND_RECORDED, LedgerDirection.DEBIT, value, key,
                donationId, null, null, "REFUND_REQUEST", refundRequestId, actorId, "Simulated refund recorded; no external payment executed");
    }

    private EscrowAccount lockFor(UUID campaignId, String currency, Set<EscrowStatus> allowed, String idempotencyKey) {
        EscrowAccount account = accountRepository.lockByCampaignIdAndCurrency(campaignId, currency)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ESCROW_NOT_ACTIVE, "Escrow account has not been opened for this campaign"));
        if (!allowed.contains(account.getEscrowStatus())) {
            throw new ApplicationException(ErrorCode.ESCROW_NOT_ACTIVE, "Escrow account is " + account.getEscrowStatus());
        }
        if (ledgerRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new ApplicationException(ErrorCode.DUPLICATE_OPERATION, "Ledger operation has already been recorded");
        }
        return account;
    }

    private EscrowLedgerEntry append(EscrowAccount account, LedgerTransactionType type, LedgerDirection direction, BigDecimal amount,
                                     String key, UUID donationId, UUID milestoneId, UUID disbursementId,
                                     String referenceType, UUID referenceId, UUID actorId, String description) {
        verifyInvariants(account);
        EscrowLedgerEntry entry = new EscrowLedgerEntry();
        entry.setTransactionCode(Codes.generate("TXN"));
        entry.setCampaignId(account.getCampaignId());
        entry.setEscrowAccountId(account.getId());
        entry.setDonationId(donationId);
        entry.setMilestoneId(milestoneId);
        entry.setDisbursementId(disbursementId);
        entry.setTransactionType(type);
        entry.setAmount(amount);
        entry.setCurrency(account.getCurrency());
        entry.setDirection(direction);
        entry.setStatus(LedgerEntryStatus.COMPLETED);
        entry.setIdempotencyKey(key);
        entry.setReferenceType(referenceType);
        entry.setReferenceId(referenceId);
        entry.setDescription(description);
        entry.setCreatedBy(actorId);
        ledgerRepository.save(entry);
        auditService.record(actorId, "LEDGER_" + type.name(), "ESCROW_LEDGER_ENTRY", entry.getId(), null, null,
                AuditService.meta("transactionCode", entry.getTransactionCode(), "campaignId", account.getCampaignId(),
                        "amount", amount, "currency", account.getCurrency(), "referenceType", referenceType, "referenceId", referenceId));
        return entry;
    }

    static void verifyInvariants(EscrowAccount account) {
        boolean negative = Stream.of(account.getTotalConfirmedContributions(), account.getTotalReserved(), account.getTotalDisbursed(),
                account.getTotalRefunded(), account.getAvailableBalance()).anyMatch(v -> v.signum() < 0);
        BigDecimal expected = account.getTotalConfirmedContributions().subtract(account.getTotalReserved())
                .subtract(account.getTotalDisbursed()).subtract(account.getTotalRefunded());
        if (negative || expected.compareTo(account.getAvailableBalance()) != 0) {
            throw new IllegalStateException("Escrow invariant violated for account " + account.getId());
        }
    }
}
