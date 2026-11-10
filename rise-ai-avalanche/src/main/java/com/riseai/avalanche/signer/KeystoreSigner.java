package com.riseai.avalanche.signer;

import com.riseai.avalanche.env.AvalancheEnvironment;
import com.riseai.avalanche.env.EnvironmentGuard;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

/**
 * A testnet key decrypted from an operator-held keystore. Package-private: it can only be built through
 * {@link KeystoreSignerFactory}. The key lives only in this object's memory; it is never written, logged or exposed.
 */
final class KeystoreSigner implements Signer {

    private static final BigInteger HALF_CURVE_ORDER = CustomNamedCurves.getByName("secp256k1").getN().shiftRight(1);

    private final SignerRole role;
    private final AvalancheEnvironment environment;
    private final long chainId;
    private final ECKeyPair keyPair;
    private final String address;

    KeystoreSigner(SignerRole role, AvalancheEnvironment environment, long chainId, ECKeyPair keyPair) {
        EnvironmentGuard.requireKeystoreSignerEnvironment(environment);
        EnvironmentGuard.checkChainId(environment, chainId);
        this.role = role;
        this.environment = environment;
        this.chainId = chainId;
        this.keyPair = keyPair;
        this.address = Keys.toChecksumAddress(Keys.getAddress(keyPair.getPublicKey()));
    }

    @Override
    public SignerRole role() {
        return role;
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public AvalancheEnvironment environment() {
        return environment;
    }

    @Override
    public long chainId() {
        return chainId;
    }

    @Override
    public Signature sign(byte[] digest32) {
        EnvironmentGuard.requireKeystoreSignerEnvironment(environment);
        EnvironmentGuard.checkChainId(environment, chainId);
        if (digest32 == null || digest32.length != 32) {
            throw new IllegalArgumentException("a signer signs exactly one 32-byte digest");
        }
        Sign.SignatureData data = Sign.signMessage(digest32, keyPair, false);
        if (Numeric.toBigInt(data.getS()).compareTo(HALF_CURVE_ORDER) > 0) {
            throw new IllegalStateException("produced a high-s signature, which contracts reject");
        }
        return new Signature(data.getR(), data.getS(), data.getV()[0] & 0xff);
    }

    @Override
    public String toString() {
        return "KeystoreSigner[role=" + role + ", address=" + address + ", environment=" + environment + "]";
    }
}
