package com.riseai.service.impl;

import com.riseai.entity.EscrowAccount;
import com.riseai.entity.EscrowLedgerEntry;
import com.riseai.enums.EscrowStatus;
import com.riseai.enums.LedgerDirection;
import com.riseai.enums.LedgerTransactionType;
import com.riseai.exception.ApplicationException;
import com.riseai.exception.ErrorCode;
import com.riseai.repository.EscrowAccountRepository;
import com.riseai.repository.EscrowLedgerRepository;
import com.riseai.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulatedEscrowEngineTest {
    @Mock
    EscrowAccountRepository accountRepository;
    @Mock
    EscrowLedgerRepository ledgerRepository;
    @Mock
    AuditService auditService;
    @InjectMocks
    SimulatedEscrowEngine engine;

    private final UUID campaignId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private EscrowAccount account;

    @BeforeEach
    void setUp() {
        account = new EscrowAccount();
        account.setId(UUID.randomUUID());
        account.setCampaignId(campaignId);
        account.setCurrency("VND");
        account.setEscrowStatus(EscrowStatus.ACTIVE);
        lenient().when(accountRepository.lockByCampaignIdAndCurrency(campaignId, "VND")).thenReturn(Optional.of(account));
        lenient().when(ledgerRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        lenient().when(ledgerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void contributionIncreasesConfirmedAndAvailableAndWritesCreditEntry() {
        UUID donation = UUID.randomUUID();
        EscrowLedgerEntry entry = engine.recordContribution(campaignId, "VND", donation, vnd(1000), actor);

        assertThat(account.getTotalConfirmedContributions()).isEqualByComparingTo("1000");
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("1000");
        assertThat(entry.getTransactionType()).isEqualTo(LedgerTransactionType.CONTRIBUTION_CONFIRMED);
        assertThat(entry.getDirection()).isEqualTo(LedgerDirection.CREDIT);
        assertThat(entry.getIdempotencyKey()).isEqualTo("CONTRIBUTION:" + donation);
        assertThat(entry.getReferenceId()).isEqualTo(donation);
    }

    @Test
    void reserveMovesAvailableToReservedAndReleaseMovesReservedToDisbursed() {
        engine.recordContribution(campaignId, "VND", UUID.randomUUID(), vnd(1000), actor);
        UUID request = UUID.randomUUID();

        engine.reserve(campaignId, "VND", UUID.randomUUID(), request, vnd(600), actor);
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("400");
        assertThat(account.getTotalReserved()).isEqualByComparingTo("600");

        engine.release(campaignId, "VND", UUID.randomUUID(), request, vnd(600), actor);
        assertThat(account.getTotalReserved()).isEqualByComparingTo("0");
        assertThat(account.getTotalDisbursed()).isEqualByComparingTo("600");
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("400");
        assertThat(account.heldBalance()).isEqualByComparingTo("400");
    }

    @Test
    void cancelledReservationReturnsToAvailable() {
        engine.recordContribution(campaignId, "VND", UUID.randomUUID(), vnd(1000), actor);
        UUID request = UUID.randomUUID();
        engine.reserve(campaignId, "VND", UUID.randomUUID(), request, vnd(300), actor);
        engine.cancelReservation(campaignId, "VND", UUID.randomUUID(), request, vnd(300), actor);

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("1000");
        assertThat(account.getTotalReserved()).isEqualByComparingTo("0");
    }

    @Test
    void reservationAboveAvailableIsRejectedWithoutChangingBalances() {
        engine.recordContribution(campaignId, "VND", UUID.randomUUID(), vnd(100), actor);

        assertThatThrownBy(() -> engine.reserve(campaignId, "VND", UUID.randomUUID(), UUID.randomUUID(), vnd(101), actor))
                .isInstanceOf(ApplicationException.class).extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE);
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("100");
        assertThat(account.getTotalReserved()).isEqualByComparingTo("0");
    }

    @Test
    void refundCannotConsumeReservedFunds() {
        engine.recordContribution(campaignId, "VND", UUID.randomUUID(), vnd(100), actor);
        engine.reserve(campaignId, "VND", UUID.randomUUID(), UUID.randomUUID(), vnd(80), actor);

        assertThatThrownBy(() -> engine.recordRefund(campaignId, "VND", UUID.randomUUID(), UUID.randomUUID(), vnd(30), actor))
                .isInstanceOf(ApplicationException.class).extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE);
        engine.recordRefund(campaignId, "VND", UUID.randomUUID(), UUID.randomUUID(), vnd(20), actor);
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("0");
        assertThat(account.getTotalRefunded()).isEqualByComparingTo("20");
    }

    @Test
    void duplicateIdempotencyKeyIsRejectedBeforeAnyMutation() {
        UUID donation = UUID.randomUUID();
        when(ledgerRepository.existsByIdempotencyKey("CONTRIBUTION:" + donation)).thenReturn(true);

        assertThatThrownBy(() -> engine.recordContribution(campaignId, "VND", donation, vnd(50), actor))
                .isInstanceOf(ApplicationException.class).extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_OPERATION);
        assertThat(account.getTotalConfirmedContributions()).isEqualByComparingTo("0");
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void frozenAccountRejectsReservationButAllowsRefund() {
        engine.recordContribution(campaignId, "VND", UUID.randomUUID(), vnd(100), actor);
        account.setEscrowStatus(EscrowStatus.FROZEN);

        assertThatThrownBy(() -> engine.reserve(campaignId, "VND", UUID.randomUUID(), UUID.randomUUID(), vnd(10), actor))
                .isInstanceOf(ApplicationException.class).extracting("errorCode").isEqualTo(ErrorCode.ESCROW_NOT_ACTIVE);
        engine.recordRefund(campaignId, "VND", UUID.randomUUID(), UUID.randomUUID(), vnd(10), actor);
        assertThat(account.getTotalRefunded()).isEqualByComparingTo("10");
    }

    @Test
    void nonPositiveAndOverPreciseAmountsAreRejected() {
        assertThatThrownBy(() -> engine.recordContribution(campaignId, "VND", UUID.randomUUID(), BigDecimal.ZERO, actor))
                .isInstanceOf(ApplicationException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> engine.recordContribution(campaignId, "VND", UUID.randomUUID(), new BigDecimal("1.001"), actor))
                .isInstanceOf(ApplicationException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void releaseEntryIsDebitAndReferencesDisbursement() {
        engine.recordContribution(campaignId, "VND", UUID.randomUUID(), vnd(100), actor);
        UUID request = UUID.randomUUID();
        UUID milestone = UUID.randomUUID();
        engine.reserve(campaignId, "VND", milestone, request, vnd(100), actor);
        engine.release(campaignId, "VND", milestone, request, vnd(100), actor);

        ArgumentCaptor<EscrowLedgerEntry> captor = ArgumentCaptor.forClass(EscrowLedgerEntry.class);
        verify(ledgerRepository, times(3)).save(captor.capture());
        EscrowLedgerEntry release = captor.getAllValues().get(2);
        assertThat(release.getTransactionType()).isEqualTo(LedgerTransactionType.FUNDS_RELEASED);
        assertThat(release.getDirection()).isEqualTo(LedgerDirection.DEBIT);
        assertThat(release.getDisbursementId()).isEqualTo(request);
        assertThat(release.getMilestoneId()).isEqualTo(milestone);
    }

    @Test
    void invariantCheckDetectsInconsistentAggregates() {
        account.setTotalConfirmedContributions(vnd(100));
        account.setAvailableBalance(vnd(90));
        assertThatThrownBy(() -> SimulatedEscrowEngine.verifyInvariants(account)).isInstanceOf(IllegalStateException.class);
    }

    private static BigDecimal vnd(long amount) {
        return BigDecimal.valueOf(amount).setScale(2);
    }
}
