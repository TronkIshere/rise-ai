package com.riseai.avalanche.contract;

import com.riseai.avalanche.scan.EventDecoder;
import org.web3j.crypto.Hash;

import java.util.List;

/** The CampaignEscrow events: signatures, topic0 values (computed, never copied) and registration for the scanner. */
public final class CampaignEvents {

    public static final List<String> SIGNATURES = List.of(
            "CampaignOpened(bytes32,address,uint256,uint64,uint256)",
            "EscrowStatusChanged(bytes32,uint8,uint8)",
            "ContributionConfirmed(bytes32,bytes32,uint256)",
            "FundsReserved(bytes32,bytes32,bytes32,uint256)",
            "ReservationCancelled(bytes32,bytes32,bytes32,uint256)",
            "ReleaseQueued(bytes32,bytes32,bytes32,uint256,uint64)",
            "FundsReleased(bytes32,bytes32,bytes32,uint256,address)",
            "RefundReserved(bytes32,bytes32,bytes32,uint256)",
            "RefundReservationReleased(bytes32,bytes32,bytes32,uint256)",
            "RefundRecorded(bytes32,bytes32,bytes32,uint256)",
            "SettlementRefundRecorded(bytes32,bytes32,bytes32,uint256)",
            "AdjustmentApplied(bytes32,bytes32,bool,uint256)",
            "DisputeOpened(bytes32,bytes32,bytes32)",
            "DisputeClosed(bytes32,bytes32,bytes32)",
            "EvidenceAnchored(bytes32,bytes32,bytes32,bytes32,bytes32)",
            "EvidenceDecided(bytes32,bytes32,bytes32,bool)",
            "GuardianHoldPlaced(bytes32,bytes32,bytes32,address,bytes32,uint64)",
            "GuardianHoldLifted(bytes32,bytes32,bytes32,address,bool)",
            "ReservesAttested(uint64,address,uint256,uint256,bytes32)",
            "PayoutRecorded(bytes32,bytes32,bytes32)",
            "ReceiptConfirmed(bytes32,bytes32,address)");

    private CampaignEvents() {
    }

    public static String topic0(String signature) {
        return Hash.sha3String(signature);
    }

    public static String nameOf(String signature) {
        return signature.substring(0, signature.indexOf('('));
    }

    /** Registers every escrow event with the scanner's decoder so only these logs are accepted. */
    public static EventDecoder registerAll(EventDecoder decoder) {
        for (String signature : SIGNATURES) {
            decoder.register(topic0(signature), nameOf(signature));
        }
        return decoder;
    }
}
