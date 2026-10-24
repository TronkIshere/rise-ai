package com.riseai.escrow;

import com.riseai.audit.AuditService;
import com.riseai.common.exception.ApplicationException;
import com.riseai.escrow.EscrowDtos.Balances;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LedgerInvariantPropertiesTest {
    enum Kind {
        CONTRIBUTE, RESERVE, CANCEL_RESERVATION, RELEASE, REFUND, RESERVE_REFUND, RELEASE_REFUND_RESERVATION, RECORD_RESERVED_REFUND,
        SETTLEMENT_REFUND, ADJUST_CREDIT, ADJUST_DEBIT, FREEZE, ACTIVATE, SETTLE, REPLAY
    }

    record Step(Kind kind, long cents, int pick) {
    }

    record Reservation(UUID id, BigDecimal amount) {
    }

    @Property(tries = 300)
    void everyOperationSequenceKeepsAggregatesConsistentWithTheLedger(@ForAll("steps") List<Step> steps) {
        Harness h = new Harness();
        for (Step step : steps) {
            Balances before = Balances.from(h.account);
            int entries = h.entries.size();
            try {
                h.apply(step);
            } catch (ApplicationException rejected) {
                assertThat(Balances.from(h.account)).as("rejected %s must not change balances", step).isEqualTo(before);
                assertThat(h.entries).as("rejected %s must not append entries", step).hasSize(entries);
            }
            assertThat(h.nonNegative()).as("no negative aggregate after %s", step).isTrue();
            assertThat(h.identityHolds()).as("balance identity after %s", step).isTrue();
            assertThat(Balances.fromLedger(h.sums()).matches(Balances.from(h.account))).as("ledger reconciles after %s", step).isTrue();
            assertThat(h.entries).allMatch(e -> e.getAmount().signum() > 0);
            assertThat(h.entries.stream().map(EscrowLedgerEntry::getIdempotencyKey).distinct().count()).isEqualTo(h.entries.size());
        }
    }

    @Provide
    Arbitrary<List<Step>> steps() {
        Arbitrary<Step> step = Combinators.combine(
                Arbitraries.of(Kind.class),
                Arbitraries.longs().between(1, 50_000_000L),
                Arbitraries.integers().between(0, 1_000)).as(Step::new);
        return step.list().ofMinSize(1).ofMaxSize(60);
    }

    private static final class Harness {
        final UUID campaignId = UUID.randomUUID();
        final UUID actor = UUID.randomUUID();
        final EscrowAccount account = new EscrowAccount();
        final List<EscrowLedgerEntry> entries = new ArrayList<>();
        final Set<String> keys = new HashSet<>();
        final List<Reservation> reservations = new ArrayList<>();
        final List<Reservation> refundReservations = new ArrayList<>();
        final SimulatedEscrowEngine engine;
        Runnable lastOperation;

        Harness() {
            account.setId(UUID.randomUUID());
            account.setCampaignId(campaignId);
            account.setCurrency("VND");
            account.setEscrowStatus(EscrowStatus.ACTIVE);
            EscrowAccountRepository accounts = mock(EscrowAccountRepository.class);
            EscrowLedgerRepository ledger = mock(EscrowLedgerRepository.class);
            when(accounts.lockByCampaignIdAndCurrency(campaignId, "VND")).thenReturn(Optional.of(account));
            when(ledger.existsByIdempotencyKey(anyString())).thenAnswer(i -> keys.contains(i.<String>getArgument(0)));
            when(ledger.save(any())).thenAnswer(i -> {
                EscrowLedgerEntry entry = i.getArgument(0);
                keys.add(entry.getIdempotencyKey());
                entries.add(entry);
                return entry;
            });
            engine = new SimulatedEscrowEngine(accounts, ledger, mock(AuditService.class));
        }

        void apply(Step step) {
            BigDecimal amount = BigDecimal.valueOf(step.cents(), 2);
            switch (step.kind()) {
                case CONTRIBUTE -> run(() -> engine.recordContribution(campaignId, "VND", UUID.randomUUID(), amount, actor));
                case RESERVE -> {
                    UUID request = UUID.randomUUID();
                    run(() -> engine.reserve(campaignId, "VND", UUID.randomUUID(), request, amount, actor));
                    reservations.add(new Reservation(request, amount));
                }
                case CANCEL_RESERVATION -> take(reservations, step.pick()).ifPresent(r -> {
                    run(() -> engine.cancelReservation(campaignId, "VND", UUID.randomUUID(), r.id(), r.amount(), actor));
                    reservations.remove(r);
                });
                case RELEASE -> take(reservations, step.pick()).ifPresent(r -> {
                    run(() -> engine.release(campaignId, "VND", UUID.randomUUID(), r.id(), r.amount(), actor));
                    reservations.remove(r);
                });
                case REFUND -> run(() -> engine.recordRefund(campaignId, "VND", UUID.randomUUID(), UUID.randomUUID(), amount, actor));
                case RESERVE_REFUND -> {
                    UUID refund = UUID.randomUUID();
                    run(() -> engine.reserveRefund(campaignId, "VND", UUID.randomUUID(), refund, amount, actor));
                    refundReservations.add(new Reservation(refund, amount));
                }
                case RELEASE_REFUND_RESERVATION -> take(refundReservations, step.pick()).ifPresent(r -> {
                    run(() -> engine.releaseRefundReservation(campaignId, "VND", null, r.id(), r.amount(), actor));
                    refundReservations.remove(r);
                });
                case RECORD_RESERVED_REFUND -> take(refundReservations, step.pick()).ifPresent(r -> {
                    engine.releaseRefundReservation(campaignId, "VND", null, r.id(), r.amount(), actor);
                    engine.recordRefund(campaignId, "VND", null, r.id(), r.amount(), actor);
                    refundReservations.remove(r);
                });
                case SETTLEMENT_REFUND -> run(() -> engine.recordSettlementRefund(campaignId, "VND", UUID.randomUUID(), UUID.randomUUID(), amount, actor));
                case ADJUST_CREDIT -> run(() -> engine.recordAdjustment(campaignId, "VND", UUID.randomUUID(), LedgerDirection.CREDIT, amount, actor));
                case ADJUST_DEBIT -> run(() -> engine.recordAdjustment(campaignId, "VND", UUID.randomUUID(), LedgerDirection.DEBIT, amount, actor));
                case FREEZE -> engine.changeStatus(campaignId, "VND", EscrowStatus.FROZEN, actor, "freeze");
                case ACTIVATE -> engine.changeStatus(campaignId, "VND", EscrowStatus.ACTIVE, actor, "activate");
                case SETTLE -> engine.changeStatus(campaignId, "VND", EscrowStatus.SETTLEMENT, actor, "settle");
                case REPLAY -> {
                    if (lastOperation != null) {
                        lastOperation.run();
                    }
                }
            }
        }

        void run(Runnable operation) {
            lastOperation = operation;
            operation.run();
        }

        Optional<Reservation> take(List<Reservation> open, int pick) {
            if (open.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(open.get(pick % open.size()));
        }

        boolean nonNegative() {
            Balances b = Balances.from(account);
            return List.of(b.confirmed(), b.reserved(), b.disbursed(), b.refunded(), b.available(), b.refundReserved(),
                    b.adjustmentCredits(), b.adjustmentDebits()).stream().allMatch(v -> v.signum() >= 0);
        }

        boolean identityHolds() {
            Balances b = Balances.from(account);
            BigDecimal expected = b.confirmed().add(b.adjustmentCredits()).subtract(b.adjustmentDebits()).subtract(b.reserved())
                    .subtract(b.refundReserved()).subtract(b.disbursed()).subtract(b.refunded());
            return expected.compareTo(b.available()) == 0;
        }

        Map<LedgerTransactionType, BigDecimal> sums() {
            Map<LedgerTransactionType, BigDecimal> sums = new EnumMap<>(LedgerTransactionType.class);
            for (EscrowLedgerEntry entry : entries) {
                sums.merge(entry.getTransactionType(), entry.getAmount(), BigDecimal::add);
            }
            return sums;
        }
    }
}
