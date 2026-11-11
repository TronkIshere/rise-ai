package com.riseai.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Whether the stored histories still match what was anchored on-chain. Only hashes and counts: no record content is exposed.
 * status: NOT_ANCHORED, CONSISTENT, MISMATCH (rows were changed, removed or back-dated) or UNAVAILABLE (chain not reachable).
 */
public record TransparencyReport(boolean onChain, String anchorContract, List<Anchor> anchors) {

    public record Anchor(String kind, String status, String anchoredRoot, Long anchoredRecordCount, Instant anchoredAt, String recomputedRoot,
                         long currentRecordCount, String txHash, String explorerUrl) {
    }
}
