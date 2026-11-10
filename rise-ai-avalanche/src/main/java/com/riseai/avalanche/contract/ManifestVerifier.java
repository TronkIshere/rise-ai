package com.riseai.avalanche.contract;

import com.riseai.avalanche.chain.ChainClient;
import com.riseai.avalanche.env.EnvironmentGuardException;
import com.riseai.avalanche.startup.ValidatedEnvironment;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fail-closed check of a deployment manifest before the gateway uses it: environment and chain id equal the verified endpoint,
 * the asset is the simulated rVND and no deny-listed real token, the contracts have code, the escrow pins the manifest's token
 * and treasury, and the token reports 2 decimals and the rVND symbol.
 */
public final class ManifestVerifier {

    public static final String TOKEN_KIND = "RISE_VND";
    public static final int TOKEN_DECIMALS = 2;
    public static final String TOKEN_SYMBOL = "rVND";
    /** Real or bridged USDC on Avalanche: never accepted, the escrow only ever holds simulated value. */
    static final Set<String> DENY_LISTED = Set.of("0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e", "0xa7d7079b0fead91f3e65f86e8915cb59c1a4c664",
            "0x5425890298aed601595a70ab815c96711a31bc65");

    private ManifestVerifier() {
    }

    public static VerifiedDeployment verify(DeploymentManifest m, ValidatedEnvironment validated, ChainClient client) {
        checkStatic(m, validated);
        requireCode(client, m.escrow(), "escrow");
        requireCode(client, m.token(), "token");
        requireCode(client, m.anchor(), "anchor");
        if (!addressOf(call(client, m.escrow(), "token()")).equals(m.token())) {
            throw new EnvironmentGuardException("MANIFEST_TOKEN_MISMATCH", "the escrow pins a different token than the manifest");
        }
        if (!addressOf(call(client, m.escrow(), "treasury()")).equals(m.treasury())) {
            throw new EnvironmentGuardException("MANIFEST_TREASURY_MISMATCH", "the escrow pins a different treasury than the manifest");
        }
        if (number(call(client, m.escrow(), "minReleaseDelay()")).longValue() != m.minReleaseDelay()) {
            throw new EnvironmentGuardException("MANIFEST_DELAY_MISMATCH", "the escrow has a different release delay floor than the manifest");
        }
        if (number(call(client, m.token(), "decimals()")).intValue() != TOKEN_DECIMALS) {
            throw new EnvironmentGuardException("ASSET_DECIMALS", "the token must have " + TOKEN_DECIMALS + " decimals");
        }
        if (!TOKEN_SYMBOL.equals(stringOf(call(client, m.token(), "symbol()")))) {
            throw new EnvironmentGuardException("ASSET_SYMBOL", "the token symbol must be " + TOKEN_SYMBOL);
        }
        return new VerifiedDeployment(m);
    }

    /** The checks that need no network. */
    static void checkStatic(DeploymentManifest m, ValidatedEnvironment validated) {
        if (!m.environment().equals(validated.environment().name())) {
            throw new EnvironmentGuardException("MANIFEST_ENVIRONMENT_MISMATCH", "manifest " + m.environment() + " versus " + validated.environment());
        }
        if (m.chainId() != validated.chainId()) {
            throw new EnvironmentGuardException("MANIFEST_CHAIN_MISMATCH", "manifest chain " + m.chainId() + " versus " + validated.chainId());
        }
        if (DENY_LISTED.contains(m.token().toLowerCase(Locale.ROOT)) || DENY_LISTED.contains(m.escrow().toLowerCase(Locale.ROOT))) {
            throw new EnvironmentGuardException("ASSET_DENY_LISTED", "a deny-listed real token address");
        }
        if (!TOKEN_KIND.equals(m.tokenKind())) {
            throw new EnvironmentGuardException("ASSET_NOT_ALLOWED", "only the simulated rVND token is accepted");
        }
    }

    private static void requireCode(ChainClient client, String address, String name) {
        String code = client.code(address);
        if (code == null || Numeric.cleanHexPrefix(code).isEmpty()) {
            throw new EnvironmentGuardException("MANIFEST_CODE_MISSING", "no code at the " + name + " address");
        }
    }

    private static String call(ChainClient client, String to, String signature) {
        String selector = Numeric.toHexString(Hash.sha3(signature.getBytes(StandardCharsets.UTF_8)), 0, 4, true);
        ChainClient.CallResult result = client.call(to, selector);
        if (result.reverted() || result.data() == null || Numeric.cleanHexPrefix(result.data()).isEmpty()) {
            throw new EnvironmentGuardException("MANIFEST_CALL_FAILED", signature + " did not answer at " + to);
        }
        return result.data();
    }

    private static BigInteger number(String word) {
        return new BigInteger(Numeric.cleanHexPrefix(word), 16);
    }

    private static String addressOf(String word) {
        String hex = Numeric.cleanHexPrefix(word);
        return "0x" + hex.substring(hex.length() - 40).toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String stringOf(String data) {
        List<TypeReference<Type>> outputs = new java.util.ArrayList<>();
        outputs.add((TypeReference) new TypeReference<Utf8String>() { });
        List<Type> decoded = FunctionReturnDecoder.decode(data, outputs);
        return decoded.isEmpty() ? "" : ((Utf8String) decoded.get(0)).getValue();
    }
}
