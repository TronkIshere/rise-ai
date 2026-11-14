package com.riseai.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Proof of reserves: what the independent guardian attested about the escrow bank account, next to what the escrow held at that moment.
 * Amounts are VND (simulated). gap = heldAtAttestation - bankBalance; a positive gap means the bank held less than the escrow promised.
 * attested is false until the guardian's first attestation; available is false when the chain cannot be reached.
 */
public record ReservesResponse(boolean onChain, boolean available, boolean attested, Long period, Instant attestedAt, String attester,
                               BigDecimal bankBalance, BigDecimal heldAtAttestation, BigDecimal gap, BigDecimal currentHeld, String statementHash) {

    public static ReservesResponse offChain() {
        return new ReservesResponse(false, false, false, null, null, null, null, null, null, null, null);
    }

    public static ReservesResponse unavailable() {
        return new ReservesResponse(true, false, false, null, null, null, null, null, null, null, null);
    }
}
