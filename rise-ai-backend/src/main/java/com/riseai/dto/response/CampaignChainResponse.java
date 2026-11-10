package com.riseai.dto.response;

import com.riseai.enums.DisbursementStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The on-chain mirror of one campaign. Each entry is one CampaignEscrow transaction; error details stay internal.
 * openStatus: NOT_ON_CHAIN, PENDING, SUBMITTED, CONFIRMED or FAILED (the campaign's open transaction).
 * chainStatus of a disbursement: NOT_ON_CHAIN, RESERVING, RESERVED_ON_CHAIN, QUEUING, IN_OBJECTION_WINDOW, BLOCKED_BY_DISPUTE,
 * RELEASED_ON_CHAIN, CANCELLING, CANCELLED_ON_CHAIN or FAILED. releaseAt is when the queued release may execute, when the chain answers.
 */
public record CampaignChainResponse(boolean onChain, UUID campaignId, String chainCampaignId, String openStatus, String explorerTxUrl,
                                    List<Disbursement> disbursements, List<Transaction> transactions) {

    public record Disbursement(UUID disbursementId, String requestCode, DisbursementStatus requestStatus, String chainStatus, Instant releaseAt,
                               String reserveTxHash, String queueTxHash, String releaseTxHash) {
    }

    public record Transaction(String action, UUID referenceId, String status, String txHash, String explorerUrl, Instant recordedAt) {
    }
}
