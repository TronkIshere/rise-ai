package com.riseai.avalanche.tx;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deploys the real compiled RiseVND, CampaignEscrow and RiseAnchor to the local Anvil with its unlocked account (test infrastructure only). */
final class ContractFixtures {

    static final String ANVIL_ACCOUNT_0 = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
    /** A view function callable as a transaction: any successful call to a manifest contract. */
    static final String PAUSED_SELECTOR = "0x5c975abb";
    static final long MIN_RELEASE_DELAY = 60;

    record Stack(String token, String escrow, String anchor) {
    }

    private ContractFixtures() {
    }

    static String resource(String name) {
        try (var in = ContractFixtures.class.getResourceAsStream("/contracts/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sendFromAdmin(String url, String to, String data) {
        String params = "{\"from\":\"" + ANVIL_ACCOUNT_0 + "\"" + (to == null ? "" : ",\"to\":\"" + to + "\"") + ",\"data\":\"" + data + "\",\"gas\":\"0x1c9c380\"}";
        Matcher m = Pattern.compile("\"result\":\"(0x[0-9a-fA-F]{64})\"").matcher(AnvilRpc.call(url, "eth_sendTransaction", params));
        if (!m.find()) {
            throw new IllegalStateException("transaction not accepted");
        }
        return m.group(1);
    }

    static String deploy(String url, String creationCode) {
        String hash = sendFromAdmin(url, null, creationCode);
        for (int i = 0; i < 80; i++) {
            Matcher m = Pattern.compile("\"contractAddress\":\"(0x[0-9a-fA-F]{40})\"").matcher(AnvilRpc.call(url, "eth_getTransactionReceipt", "\"" + hash + "\""));
            if (m.find()) {
                return m.group(1).toLowerCase(Locale.ROOT);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("deployment not mined");
    }

    /** The Anvil account is admin and treasury; the treasury is funded and has approved the escrow. */
    static Stack deployStack(String url) {
        Address admin = new Address(ANVIL_ACCOUNT_0);
        String token = deploy(url, resource("RiseVND.bin") + FunctionEncoder.encodeConstructor(List.of(admin)));
        String escrow = deploy(url, resource("CampaignEscrow.bin") + FunctionEncoder.encodeConstructor(List.of(admin, new Address(token), admin, new Uint64(MIN_RELEASE_DELAY))));
        String anchor = deploy(url, resource("RiseAnchor.bin") + FunctionEncoder.encodeConstructor(List.of(admin)));
        sendFromAdmin(url, token, FunctionEncoder.encode(new Function("mint", List.of(admin, new Uint256(BigInteger.valueOf(1_000_000_000_000L))), List.of())));
        sendFromAdmin(url, token, FunctionEncoder.encode(new Function("approve", List.of(new Address(escrow), new Uint256(BigInteger.valueOf(1_000_000_000_000L))), List.of())));
        return new Stack(token, escrow, anchor);
    }

    static Path writeManifest(Path file, String environment, long chainId, Stack stack, long startBlock) throws IOException {
        String json = "{\"schemaVersion\":2,\"environment\":\"" + environment + "\",\"chainId\":" + chainId + ",\"escrow\":\"" + stack.escrow()
                + "\",\"token\":\"" + stack.token() + "\",\"tokenKind\":\"RISE_VND\",\"anchor\":\"" + stack.anchor() + "\",\"admin\":\"" + ANVIL_ACCOUNT_0
                + "\",\"treasury\":\"" + ANVIL_ACCOUNT_0 + "\",\"operator\":\"" + ANVIL_ACCOUNT_0 + "\",\"approver\":\"" + ANVIL_ACCOUNT_0
                + "\",\"minReleaseDelay\":" + MIN_RELEASE_DELAY + ",\"contractVersion\":\"1\",\"startBlock\":" + startBlock + "}";
        Files.writeString(file, json);
        return file;
    }
}
