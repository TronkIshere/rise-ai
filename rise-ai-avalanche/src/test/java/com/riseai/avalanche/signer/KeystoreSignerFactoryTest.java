package com.riseai.avalanche.signer;

import com.riseai.avalanche.env.AvalancheEnvironment;
import com.riseai.avalanche.env.EnvironmentGuard;
import com.riseai.avalanche.env.EnvironmentGuardException;
import com.riseai.avalanche.startup.ValidatedEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.crypto.WalletUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Keystore signers with throwaway keys created in a temporary folder for this test only; nothing touches a network. */
class KeystoreSignerFactoryTest {

    private static final ValidatedEnvironment FUJI = new ValidatedEnvironment(AvalancheEnvironment.AVALANCHE_FUJI, 43113, "avalanchego", "api.avax-test.network");
    private static final ValidatedEnvironment LOCAL = new ValidatedEnvironment(AvalancheEnvironment.LOCAL_SIMULATION, 31337, "anvil", "127.0.0.1");

    @TempDir
    static Path dir;
    private static KeystoreSignerFactory.Keystore operator;
    private static KeystoreSignerFactory.Keystore approver;

    @BeforeAll
    static void createThrowawayKeystores() throws Exception {
        operator = keystore("operator-pass");
        approver = keystore("approver-pass");
    }

    private static KeystoreSignerFactory.Keystore keystore(String password) throws Exception {
        Path passwordFile = Files.writeString(dir.resolve(password + ".txt"), password + "\n");
        String file = WalletUtils.generateLightNewWalletFile(password, dir.toFile());
        String address = "0x" + new com.fasterxml.jackson.databind.ObjectMapper().readTree(dir.resolve(file).toFile()).path("address").asText();
        return new KeystoreSignerFactory.Keystore(dir.resolve(file).toString(), passwordFile.toString(), address);
    }

    private static String code(Runnable call) {
        return assertThrows(EnvironmentGuardException.class, call::run).code();
    }

    @Test
    void fujiLoadsBothKeysAndMapsTheRolesWithoutExposingThem() {
        SignerRegistry registry = KeystoreSignerFactory.createRegistry(FUJI, operator, approver);
        assertEquals(SignerMode.KEYSTORE, registry.mode());
        assertEquals(operator.expectedAddress().toLowerCase(), registry.get(SignerRole.OPERATOR).address().toLowerCase());
        assertEquals(approver.expectedAddress().toLowerCase(), registry.get(SignerRole.APPROVER).address().toLowerCase());
        assertEquals(registry.get(SignerRole.OPERATOR).address(), registry.get(SignerRole.RELAYER).address());
        assertThrows(IllegalArgumentException.class, () -> registry.get(SignerRole.ADMIN), "no admin or deployer key exists in this mode");
        assertFalse(registry.get(SignerRole.APPROVER).toString().contains("pass"));
        byte[] digest = org.web3j.crypto.Hash.sha3(new byte[]{1, 2, 3});
        Signature signature = registry.get(SignerRole.APPROVER).sign(digest);
        String recovered = "0x" + org.web3j.crypto.Keys.getAddress(org.web3j.crypto.Sign.recoverFromSignature(signature.v() - 27,
                new org.web3j.crypto.ECDSASignature(new java.math.BigInteger(1, signature.r()), new java.math.BigInteger(1, signature.s())), digest));
        assertEquals(approver.expectedAddress().toLowerCase(), recovered, "the approver key signs as the approver address");
    }

    @Test
    void everyEnvironmentOtherThanFujiIsRefused() {
        assertEquals("KEYSTORE_FORBIDDEN_ENVIRONMENT", code(() -> KeystoreSignerFactory.createRegistry(LOCAL, operator, approver)));
        assertEquals("KEYSTORE_FORBIDDEN_ENVIRONMENT", code(() -> EnvironmentGuard.requireSubmissionEnvironment(AvalancheEnvironment.LOCAL_SIMULATION, true)));
        assertEquals("SIGNER_FORBIDDEN_ENVIRONMENT", code(() -> EnvironmentGuard.requireSubmissionEnvironment(AvalancheEnvironment.AVALANCHE_FUJI, false)));
    }

    @Test
    void aKeystoreThatDoesNotHoldTheDeclaredAddressIsRefused() {
        KeystoreSignerFactory.Keystore wrong = new KeystoreSignerFactory.Keystore(operator.path(), operator.passwordFile(), approver.expectedAddress());
        assertEquals("KEYSTORE_ADDRESS_MISMATCH", code(() -> KeystoreSignerFactory.createRegistry(FUJI, wrong, approver)));
    }

    @Test
    void aWrongPasswordFailsWithoutRevealingIt() {
        KeystoreSignerFactory.Keystore wrong = new KeystoreSignerFactory.Keystore(operator.path(), approver.passwordFile(), operator.expectedAddress());
        EnvironmentGuardException e = assertThrows(EnvironmentGuardException.class, () -> KeystoreSignerFactory.createRegistry(FUJI, wrong, approver));
        assertEquals("KEYSTORE_DECRYPTION_FAILED", e.code());
        assertFalse(e.getMessage().contains("approver-pass"));
    }

    @Test
    void theOperatorAndTheApproverMustBeDifferentKeys() {
        assertEquals("SIGNER_ROLES_MUST_DIFFER", code(() -> KeystoreSignerFactory.createRegistry(FUJI, operator, operator)));
    }

    @Test
    void missingConfigurationIsRefusedBeforeAnythingIsRead() {
        assertEquals("KEYSTORE_NOT_CONFIGURED", code(() -> KeystoreSignerFactory.createRegistry(FUJI, null, approver)));
        assertEquals("KEYSTORE_NOT_CONFIGURED", code(() -> KeystoreSignerFactory.createRegistry(FUJI,
                new KeystoreSignerFactory.Keystore(operator.path(), operator.passwordFile(), "0x12"), approver)));
        assertEquals("KEYSTORE_UNREADABLE", code(() -> KeystoreSignerFactory.createRegistry(FUJI,
                new KeystoreSignerFactory.Keystore(operator.path(), dir.resolve("missing.txt").toString(), operator.expectedAddress()), approver)));
    }
}
