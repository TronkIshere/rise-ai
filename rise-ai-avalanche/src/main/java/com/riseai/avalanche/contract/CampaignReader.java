package com.riseai.avalanche.contract;

import com.riseai.avalanche.chain.ChainClient;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Reads CampaignEscrow state with eth_call. Every struct is fully static, so each answer is a run of consecutive words. */
public class CampaignReader {

    public static final List<String> ESCROW_STATUS = List.of("NONE", "ACTIVE", "FROZEN", "SETTLEMENT");
    public static final List<String> RESERVATION_STATUS = List.of("NONE", "RESERVED", "QUEUED", "RELEASED", "CANCELLED");

    public record Campaign(String status, String beneficiary, long releaseDelay, BigInteger confirmed, BigInteger available, BigInteger reserved,
                           BigInteger refundReserved, BigInteger disbursed, BigInteger refunded, BigInteger adjustmentCredits,
                           BigInteger adjustmentDebits) {
    }

    public record Milestone(BigInteger planned, BigInteger reserved, BigInteger released, int openDisputes, int acceptedEvidence) {
    }

    public record Reservation(String status, String milestoneId, BigInteger amount, String reservedBy, long releaseAt) {
    }

    private final ChainClient client;
    private final String contract;

    public CampaignReader(ChainClient client, String contractAddress) {
        this.client = client;
        this.contract = contractAddress;
    }

    public Campaign campaign(String campaignId) {
        String hex = read(CampaignCalls.getCampaign(campaignId), 11);
        return new Campaign(ESCROW_STATUS.get(number(hex, 0).intValueExact()), address(hex, 1), number(hex, 2).longValueExact(), number(hex, 3),
                number(hex, 4), number(hex, 5), number(hex, 6), number(hex, 7), number(hex, 8), number(hex, 9), number(hex, 10));
    }

    public Milestone milestone(String campaignId, String milestoneId) {
        String hex = read(CampaignCalls.getMilestone(campaignId, milestoneId), 5);
        return new Milestone(number(hex, 0), number(hex, 1), number(hex, 2), number(hex, 3).intValueExact(), number(hex, 4).intValueExact());
    }

    public Reservation reservation(String campaignId, String requestId) {
        String hex = read(CampaignCalls.getReservation(campaignId, requestId), 5);
        return new Reservation(RESERVATION_STATUS.get(number(hex, 0).intValueExact()), "0x" + word(hex, 1), number(hex, 2), address(hex, 3),
                number(hex, 4).longValueExact());
    }

    public record Anchor(long sequence, String root, long recordCount, long anchoredAt) {
    }

    /** The latest anchor of a kind on the RiseAnchor contract, or null when none exists yet. */
    public Anchor latestAnchor(String anchorContract, String kind) {
        long count = number(answer(anchorContract, CampaignCalls.anchorCount(kind)), 0).longValueExact();
        if (count == 0) {
            return null;
        }
        String hex = answer(anchorContract, CampaignCalls.anchorAt(kind, count - 1));
        if (hex.length() != 3 * 64) {
            throw new IllegalStateException("unexpected anchor answer length " + hex.length());
        }
        return new Anchor(count - 1, "0x" + word(hex, 0), number(hex, 1).longValueExact(), number(hex, 2).longValueExact());
    }

    public boolean paused() {
        String selector = Numeric.toHexString(Hash.sha3("paused()".getBytes(StandardCharsets.UTF_8)), 0, 4, true);
        return number(answer(selector), 0).signum() != 0;
    }

    /** ERC-20 balance in base units (two decimals) of an owner for a token. */
    public BigInteger tokenBalance(String token, String owner) {
        ChainClient.CallResult result = client.call(token, FunctionEncoder.encode(new Function("balanceOf", List.of(new Address(owner)), List.of())));
        if (result.reverted()) {
            throw new IllegalStateException("balanceOf reverted");
        }
        return new BigInteger(Numeric.cleanHexPrefix(result.data()), 16);
    }

    private String read(String data, int words) {
        String hex = answer(data);
        if (hex.length() != words * 64) {
            throw new IllegalStateException("unexpected answer length " + hex.length());
        }
        return hex;
    }

    private String answer(String data) {
        return answer(contract, data);
    }

    private String answer(String target, String data) {
        ChainClient.CallResult result = client.call(target, data);
        if (result.reverted()) {
            throw new IllegalStateException("the escrow read reverted");
        }
        return Numeric.cleanHexPrefix(result.data());
    }

    private static String word(String hex, int index) {
        return hex.substring(index * 64, index * 64 + 64);
    }

    private static BigInteger number(String hex, int index) {
        return new BigInteger(word(hex, index), 16);
    }

    private static String address(String hex, int index) {
        return "0x" + word(hex, index).substring(24).toLowerCase(Locale.ROOT);
    }
}
