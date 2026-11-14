package com.riseai.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The escrow's on-chain activity as indexed by the Avalanche Data API (source), checked against the platform's own command records.
 * signer: PLATFORM (an operator or approver key), GUARDIAN, BENEFICIARY or OTHER.
 * check: RECORDED (a platform transaction the backend recorded), INDEPENDENT (signed outside the platform: guardian, beneficiary or
 * anyone), REVERTED (refused by the contract, no effect), or UNRECORDED (a successful platform-signed transaction the backend has no
 * record of; requires review). available is false when the Data API cannot answer or does not index this chain.
 */
public record ChainActivityResponse(boolean onChain, boolean available, String source, String escrowContract, List<Item> items, String nextPageToken,
                                    long unrecordedCount) {

    public record Item(String txHash, String method, String signer, String from, Instant timestamp, long blockNumber, boolean success, String check,
                       BigDecimal rvndToBeneficiary, String explorerUrl) {
    }

    public static ChainActivityResponse offChain() {
        return new ChainActivityResponse(false, false, null, null, List.of(), null, 0);
    }

    public static ChainActivityResponse unavailable(String escrow) {
        return new ChainActivityResponse(true, false, "AVALANCHE_DATA_API", escrow, List.of(), null, 0);
    }
}
