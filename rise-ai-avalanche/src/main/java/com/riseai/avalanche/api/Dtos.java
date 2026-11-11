package com.riseai.avalanche.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Wire types. Responses always say which environment they come from and that the money is simulated. */
public final class Dtos {

    private Dtos() {
    }

    public record ConfigResponse(String environment, boolean isSimulated, long chainId, String escrow, String token, String tokenKind, String anchor,
                                 String contractVersion, boolean paused, boolean backendSignersAvailable, String operatorAddress,
                                 String approverAddress, String explorerBaseUrl) {
    }

    public record BalanceResponse(String environment, boolean isSimulated, String address, String token, String balanceBaseUnits, int decimals) {
    }

    public record AddressValidation(String address, boolean valid) {
    }

    public record CampaignResponse(String environment, boolean isSimulated, long chainId, String campaignId, String status, String beneficiary,
                                   long releaseDelay, String confirmed, String available, String reserved, String refundReserved,
                                   String disbursed, String refunded, String adjustmentCredits, String adjustmentDebits) {
    }

    public record MilestoneResponse(String environment, boolean isSimulated, String campaignId, String milestoneId, String planned, String reserved,
                                    String released, int openDisputes, int acceptedEvidence) {
    }

    public record ReservationResponse(String environment, boolean isSimulated, String campaignId, String requestId, String status, String milestoneId,
                                      String amount, long releaseAt) {
    }

    public record TransactionResponse(String environment, boolean isSimulated, long chainId, String operationKey, String state,
                                      String txHash, Long nonce, Long blockNumber, String errorClass, String signerRole) {
    }

    public record HashStatusResponse(String environment, boolean isSimulated, long chainId, String txHash, String state, Long blockNumber) {
    }

    public record AnchorResponse(String environment, boolean isSimulated, long chainId, String anchorContract, String kind, long count, Long sequence,
                                 String root, Long recordCount, Long anchoredAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AnchorRequest(String operationKey, String root, String previousRoot, Long recordCount) {
    }

    /** Unknown properties are rejected: a caller can never smuggle in a signer role, a key, an RPC URL or a chain id. */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CommandRequest(String operationKey, String beneficiary, String target, Long releaseDelay, List<String> milestoneIds,
                                 List<String> plannedAmounts, String status, String milestoneId, String donationId, String requestId,
                                 String refundId, String evidenceId, String disputeId, String settlementId, String adjustmentId,
                                 String contentHash, String amount, Boolean accepted, Boolean credit) {
    }
}
