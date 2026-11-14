package com.riseai.util;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Conversions between backend values and CampaignEscrow values. Amounts mirror exactly: rVND has the ledger's two decimals. */
public final class ChainValues {
    private ChainValues() {
    }

    public static String bytes32(UUID id) {
        return "0x" + "0".repeat(32) + id.toString().replace("-", "");
    }

    public static String baseUnits(BigDecimal amount) {
        return amount.movePointRight(2).toBigIntegerExact().toString();
    }

    /**
     * The bank reference recorded on-chain after a release. No real transfer exists in this simulation, so the reference is a hash of a
     * label that says so; a bank integration would put the hash of the real transfer reference here instead.
     */
    public static String simulatedPayoutReference(UUID disbursementId) {
        return sha256Hex("SIMULATED-PAYOUT|" + disbursementId);
    }

    public static String sha256Hex(String value) {
        try {
            return "0x" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
