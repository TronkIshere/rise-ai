package com.riseai.avalanche.signer;

import com.riseai.avalanche.env.AvalancheEnvironment;
import com.riseai.avalanche.env.EnvironmentGuard;
import com.riseai.avalanche.env.EnvironmentGuardException;
import com.riseai.avalanche.startup.ValidatedEnvironment;
import org.web3j.crypto.exception.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.regex.Pattern;

/**
 * Builds the AVALANCHE_FUJI signers from two encrypted keystores that the operator created and holds. Testnet only (ADR-009):
 * every other environment is refused, each keystore must decrypt to the address the operator declared, and the operator and
 * approver must be different keys. The operator key also relays permissionless releases; no other role exists in this mode.
 */
public final class KeystoreSignerFactory {

    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");

    public record Keystore(String path, String passwordFile, String expectedAddress) {
    }

    private KeystoreSignerFactory() {
    }

    public static SignerRegistry createRegistry(ValidatedEnvironment validated, Keystore operator, Keystore approver) {
        if (validated == null) {
            throw new EnvironmentGuardException("ENVIRONMENT_MISSING", "a signer needs a validated environment");
        }
        EnvironmentGuard.requireKeystoreSignerEnvironment(validated.environment());
        EnvironmentGuard.checkChainId(validated.environment(), validated.chainId());
        Credentials operatorKey = load(operator, "operator");
        Credentials approverKey = load(approver, "approver");
        if (operatorKey.getAddress().equalsIgnoreCase(approverKey.getAddress())) {
            throw new EnvironmentGuardException("SIGNER_ROLES_MUST_DIFFER", "the operator and the approver must be different keys");
        }
        AvalancheEnvironment env = validated.environment();
        long chainId = validated.chainId();
        EnumMap<SignerRole, Signer> signers = new EnumMap<>(SignerRole.class);
        Signer operatorSigner = new KeystoreSigner(SignerRole.OPERATOR, env, chainId, operatorKey.getEcKeyPair());
        signers.put(SignerRole.OPERATOR, operatorSigner);
        signers.put(SignerRole.ANCHORER, operatorSigner);
        signers.put(SignerRole.RELAYER, operatorSigner);
        signers.put(SignerRole.APPROVER, new KeystoreSigner(SignerRole.APPROVER, env, chainId, approverKey.getEcKeyPair()));
        return new SignerRegistry(signers, SignerMode.KEYSTORE);
    }

    private static Credentials load(Keystore keystore, String name) {
        if (keystore == null || isBlank(keystore.path()) || isBlank(keystore.passwordFile()) || isBlank(keystore.expectedAddress())) {
            throw new EnvironmentGuardException("KEYSTORE_NOT_CONFIGURED", "the " + name + " keystore, password file and address are required");
        }
        if (!ADDRESS.matcher(keystore.expectedAddress().trim()).matches()) {
            throw new EnvironmentGuardException("KEYSTORE_NOT_CONFIGURED", "the " + name + " address must be a 20-byte hex address");
        }
        String password;
        try {
            password = Files.readString(Path.of(keystore.passwordFile().trim()), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new EnvironmentGuardException("KEYSTORE_UNREADABLE", "the " + name + " password file could not be read");
        }
        Credentials credentials;
        try {
            credentials = WalletUtils.loadCredentials(password, keystore.path().trim());
        } catch (CipherException e) {
            throw new EnvironmentGuardException("KEYSTORE_DECRYPTION_FAILED", "the " + name + " keystore could not be decrypted with its password file");
        } catch (IOException | RuntimeException e) {
            throw new EnvironmentGuardException("KEYSTORE_UNREADABLE", "the " + name + " keystore could not be read");
        }
        if (!credentials.getAddress().equalsIgnoreCase(keystore.expectedAddress().trim())) {
            throw new EnvironmentGuardException("KEYSTORE_ADDRESS_MISMATCH", "the " + name + " keystore holds a different address than declared");
        }
        return credentials;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
