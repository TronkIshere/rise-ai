package com.riseai.service.impl;

import com.riseai.configuration.AvalancheEscrowProperties;
import com.riseai.entity.EscrowAccount;
import com.riseai.entity.EscrowLedgerEntry;
import com.riseai.entity.Milestone;
import com.riseai.enums.EscrowStatus;
import com.riseai.enums.LedgerDirection;
import com.riseai.repository.EscrowAccountRepository;
import com.riseai.repository.MilestoneRepository;
import com.riseai.service.EscrowEngine;
import com.riseai.util.ChainValues;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the MySQL ledger and every business rule of the simulated engine, and queues the matching CampaignEscrow transaction in the
 * same database transaction. {@link #release} is the exception: it runs only after the chain has released the funds, so the
 * command that starts a release goes through ChainEscrowRecorder instead.
 */
@Service
@Primary
@ConditionalOnProperty(prefix = "rise.escrow", name = "engine", havingValue = "avalanche")
public class AvalancheEscrowEngine implements EscrowEngine {
    private static final String ADDRESS = "0x[0-9a-fA-F]{40}";

    private final SimulatedEscrowEngine ledger;
    private final ChainCommandWriter commands;
    private final MilestoneRepository milestones;
    private final EscrowAccountRepository accounts;
    private final AvalancheEscrowProperties properties;

    public AvalancheEscrowEngine(SimulatedEscrowEngine ledger, ChainCommandWriter commands, MilestoneRepository milestones,
                                 EscrowAccountRepository accounts, AvalancheEscrowProperties properties) {
        if (properties.beneficiaryAddress() == null || !properties.beneficiaryAddress().matches(ADDRESS)) {
            throw new IllegalStateException("rise.avalanche.beneficiary-address must be a 20-byte hex address");
        }
        if (properties.internalApiKey() == null || properties.internalApiKey().isBlank()) {
            throw new IllegalStateException("rise.avalanche.internal-api-key is required with the avalanche engine");
        }
        this.ledger = ledger;
        this.commands = commands;
        this.milestones = milestones;
        this.accounts = accounts;
        this.properties = properties;
    }

    @Override
    public EscrowAccount openAccount(UUID campaignId, String currency) {
        boolean alreadyOpen = accounts.findByCampaignIdAndCurrency(campaignId, currency).isPresent();
        EscrowAccount account = ledger.openAccount(campaignId, currency);
        if (!alreadyOpen) {
            List<Milestone> planned = milestones.findByCampaignIdOrderBySequenceNumberAsc(campaignId);
            BigDecimal target = planned.stream().map(Milestone::getPlannedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            commands.enqueue(campaignId, ChainActions.OPEN, Map.of(
                    "beneficiary", properties.beneficiaryAddress(),
                    "target", ChainValues.baseUnits(target),
                    "releaseDelay", properties.releaseDelay().toSeconds(),
                    "milestoneIds", planned.stream().map(m -> ChainValues.bytes32(m.getId())).toList(),
                    "plannedAmounts", planned.stream().map(m -> ChainValues.baseUnits(m.getPlannedAmount())).toList()), campaignId, null);
        }
        return account;
    }

    @Override
    public void changeStatus(UUID campaignId, String currency, EscrowStatus status, UUID actorId, String reason) {
        boolean changes = accounts.findByCampaignIdAndCurrency(campaignId, currency).filter(a -> a.getEscrowStatus() != status).isPresent();
        ledger.changeStatus(campaignId, currency, status, actorId, reason);
        if (changes) {
            commands.enqueue(campaignId, ChainActions.STATUS, Map.of("status", status.name()), campaignId, actorId);
        }
    }

    @Override
    public EscrowLedgerEntry recordContribution(UUID campaignId, String currency, UUID donationId, BigDecimal amount, UUID actorId) {
        EscrowLedgerEntry entry = ledger.recordContribution(campaignId, currency, donationId, amount, actorId);
        commands.enqueue(campaignId, ChainActions.CONTRIBUTION, Map.of("donationId", ChainValues.bytes32(donationId), "amount", ChainValues.baseUnits(amount)), donationId, actorId);
        return entry;
    }

    @Override
    public EscrowLedgerEntry reserve(UUID campaignId, String currency, UUID milestoneId, UUID disbursementId, BigDecimal amount, UUID actorId) {
        EscrowLedgerEntry entry = ledger.reserve(campaignId, currency, milestoneId, disbursementId, amount, actorId);
        commands.enqueue(campaignId, ChainActions.RESERVE, Map.of("milestoneId", ChainValues.bytes32(milestoneId), "requestId", ChainValues.bytes32(disbursementId),
                "amount", ChainValues.baseUnits(amount)), disbursementId, actorId);
        return entry;
    }

    @Override
    public EscrowLedgerEntry cancelReservation(UUID campaignId, String currency, UUID milestoneId, UUID disbursementId, BigDecimal amount, UUID actorId) {
        EscrowLedgerEntry entry = ledger.cancelReservation(campaignId, currency, milestoneId, disbursementId, amount, actorId);
        commands.enqueue(campaignId, ChainActions.CANCEL_RESERVATION, Map.of("requestId", ChainValues.bytes32(disbursementId)), disbursementId, actorId);
        return entry;
    }

    /** Called once the chain has executed the release, so only the MySQL mirror is written here. */
    @Override
    public EscrowLedgerEntry release(UUID campaignId, String currency, UUID milestoneId, UUID disbursementId, BigDecimal amount, UUID actorId) {
        return ledger.release(campaignId, currency, milestoneId, disbursementId, amount, actorId);
    }

    /** On-chain, recording consumes the refund reservation, so the release queued just before it in this transaction is dropped. */
    @Override
    public EscrowLedgerEntry recordRefund(UUID campaignId, String currency, UUID donationId, UUID refundRequestId, BigDecimal amount, UUID actorId) {
        EscrowLedgerEntry entry = ledger.recordRefund(campaignId, currency, donationId, refundRequestId, amount, actorId);
        commands.dropPending(refundRequestId, ChainActions.RELEASE_REFUND_RESERVATION);
        commands.enqueue(campaignId, ChainActions.RECORD_REFUND, Map.of("refundId", ChainValues.bytes32(refundRequestId)), refundRequestId, actorId);
        return entry;
    }

    @Override
    public EscrowLedgerEntry reserveRefund(UUID campaignId, String currency, UUID donationId, UUID refundRequestId, BigDecimal amount, UUID actorId) {
        EscrowLedgerEntry entry = ledger.reserveRefund(campaignId, currency, donationId, refundRequestId, amount, actorId);
        commands.enqueue(campaignId, ChainActions.RESERVE_REFUND, Map.of("donationId", ChainValues.bytes32(donationId), "refundId", ChainValues.bytes32(refundRequestId),
                "amount", ChainValues.baseUnits(amount)), refundRequestId, actorId);
        return entry;
    }

    @Override
    public EscrowLedgerEntry releaseRefundReservation(UUID campaignId, String currency, UUID donationId, UUID refundRequestId, BigDecimal amount, UUID actorId) {
        EscrowLedgerEntry entry = ledger.releaseRefundReservation(campaignId, currency, donationId, refundRequestId, amount, actorId);
        commands.enqueue(campaignId, ChainActions.RELEASE_REFUND_RESERVATION, Map.of("refundId", ChainValues.bytes32(refundRequestId)), refundRequestId, actorId);
        return entry;
    }

    @Override
    public EscrowLedgerEntry recordSettlementRefund(UUID campaignId, String currency, UUID settlementId, UUID donationId, BigDecimal amount, UUID actorId) {
        EscrowLedgerEntry entry = ledger.recordSettlementRefund(campaignId, currency, settlementId, donationId, amount, actorId);
        commands.enqueue(campaignId, ChainActions.SETTLEMENT_REFUND, Map.of("settlementId", ChainValues.bytes32(settlementId), "donationId", ChainValues.bytes32(donationId),
                "amount", ChainValues.baseUnits(amount)), settlementId, actorId);
        return entry;
    }

    @Override
    public EscrowLedgerEntry recordAdjustment(UUID campaignId, String currency, UUID adjustmentId, LedgerDirection direction, BigDecimal amount, UUID actorId) {
        EscrowLedgerEntry entry = ledger.recordAdjustment(campaignId, currency, adjustmentId, direction, amount, actorId);
        commands.enqueue(campaignId, ChainActions.ADJUSTMENT, Map.of("adjustmentId", ChainValues.bytes32(adjustmentId), "credit", direction == LedgerDirection.CREDIT,
                "amount", ChainValues.baseUnits(amount)), adjustmentId, actorId);
        return entry;
    }
}
