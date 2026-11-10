package com.riseai.avalanche.signer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Holds one signer per role for the lifetime of the process. Exposes addresses only. */
public final class SignerRegistry {

    private final Map<SignerRole, Signer> signers;
    private final SignerMode mode;

    SignerRegistry(EnumMap<SignerRole, Signer> signers) {
        this(signers, SignerMode.DEV);
    }

    SignerRegistry(EnumMap<SignerRole, Signer> signers, SignerMode mode) {
        this.signers = Collections.unmodifiableMap(new EnumMap<>(signers));
        this.mode = mode;
    }

    public Signer get(SignerRole role) {
        Signer signer = signers.get(role);
        if (signer == null) {
            throw new IllegalArgumentException("no signer for role " + role);
        }
        return signer;
    }

    public SignerMode mode() {
        return mode;
    }

    public Map<SignerRole, String> addresses() {
        EnumMap<SignerRole, String> out = new EnumMap<>(SignerRole.class);
        signers.forEach((role, signer) -> out.put(role, signer.address()));
        return Collections.unmodifiableMap(out);
    }
}
