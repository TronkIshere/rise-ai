package com.riseai.avalanche.contract;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;

/** ABI encoding of CampaignEscrow calls (calldata only: signing and sending belong to the submission service). */
public final class CampaignCalls {

    private CampaignCalls() {
    }

    private static Bytes32 b32(String hex) {
        return new Bytes32(Numeric.hexStringToByteArray(hex));
    }

    private static String encode(String name, Type<?>... inputs) {
        return FunctionEncoder.encode(new Function(name, List.of(inputs), List.of()));
    }

    public static String openCampaign(String campaignId, String beneficiary, BigInteger target, long releaseDelay,
                                      List<String> milestoneIds, List<BigInteger> planned) {
        return encode("openCampaign", b32(campaignId), new Address(beneficiary), new Uint256(target), new Uint64(releaseDelay),
                new DynamicArray<>(Bytes32.class, milestoneIds.stream().map(CampaignCalls::b32).toList()),
                new DynamicArray<>(Uint256.class, planned.stream().map(Uint256::new).toList()));
    }

    public static String changeStatus(String campaignId, int status) {
        return encode("changeStatus", b32(campaignId), new Uint8(status));
    }

    public static String recordContribution(String campaignId, String donationId, BigInteger amount) {
        return encode("recordContribution", b32(campaignId), b32(donationId), new Uint256(amount));
    }

    public static String anchorEvidence(String campaignId, String milestoneId, String evidenceId, String contentHash) {
        return encode("anchorEvidence", b32(campaignId), b32(milestoneId), b32(evidenceId), b32(contentHash));
    }

    public static String decideEvidence(String campaignId, String evidenceId, boolean accepted) {
        return encode("decideEvidence", b32(campaignId), b32(evidenceId), new Bool(accepted));
    }

    public static String openDispute(String campaignId, String milestoneId, String disputeId) {
        return encode("openDispute", b32(campaignId), b32(milestoneId), b32(disputeId));
    }

    public static String closeDispute(String campaignId, String disputeId) {
        return encode("closeDispute", b32(campaignId), b32(disputeId));
    }

    public static String reserve(String campaignId, String milestoneId, String requestId, BigInteger amount) {
        return encode("reserve", b32(campaignId), b32(milestoneId), b32(requestId), new Uint256(amount));
    }

    public static String cancelReservation(String campaignId, String requestId) {
        return encode("cancelReservation", b32(campaignId), b32(requestId));
    }

    public static String queueRelease(String campaignId, String requestId) {
        return encode("queueRelease", b32(campaignId), b32(requestId));
    }

    public static String executeRelease(String campaignId, String requestId) {
        return encode("executeRelease", b32(campaignId), b32(requestId));
    }

    public static String reserveRefund(String campaignId, String donationId, String refundId, BigInteger amount) {
        return encode("reserveRefund", b32(campaignId), b32(donationId), b32(refundId), new Uint256(amount));
    }

    public static String releaseRefundReservation(String campaignId, String refundId) {
        return encode("releaseRefundReservation", b32(campaignId), b32(refundId));
    }

    public static String recordRefund(String campaignId, String refundId) {
        return encode("recordRefund", b32(campaignId), b32(refundId));
    }

    public static String recordSettlementRefund(String campaignId, String settlementId, String donationId, BigInteger amount) {
        return encode("recordSettlementRefund", b32(campaignId), b32(settlementId), b32(donationId), new Uint256(amount));
    }

    public static String recordAdjustment(String campaignId, String adjustmentId, boolean credit, BigInteger amount) {
        return encode("recordAdjustment", b32(campaignId), b32(adjustmentId), new Bool(credit), new Uint256(amount));
    }

    public static String recordPayout(String campaignId, String requestId, String bankReferenceHash) {
        return encode("recordPayout", b32(campaignId), b32(requestId), b32(bankReferenceHash));
    }

    public static String openHoldCount(String campaignId, String milestoneId) {
        return encode("openHoldCount", b32(campaignId), b32(milestoneId));
    }

    public static String getPayout(String campaignId, String requestId) {
        return encode("getPayout", b32(campaignId), b32(requestId));
    }

    public static String getReserveAttestation() {
        return encode("getReserveAttestation");
    }

    public static String totalHeld() {
        return encode("totalHeld");
    }

    public static String hasRole(String role, String account) {
        return encode("hasRole", b32(role), new Address(account));
    }

    public static String anchor(String kind, String root, String previousRoot, long recordCount) {
        return encode("anchor", b32(kind), b32(root), b32(previousRoot), new Uint64(recordCount));
    }

    public static String anchorCount(String kind) {
        return encode("anchorCount", b32(kind));
    }

    public static String anchorAt(String kind, long sequence) {
        return encode("anchorAt", b32(kind), new Uint256(BigInteger.valueOf(sequence)));
    }

    public static String getCampaign(String campaignId) {
        return encode("getCampaign", b32(campaignId));
    }

    public static String getMilestone(String campaignId, String milestoneId) {
        return encode("getMilestone", b32(campaignId), b32(milestoneId));
    }

    public static String getReservation(String campaignId, String requestId) {
        return encode("getReservation", b32(campaignId), b32(requestId));
    }
}
