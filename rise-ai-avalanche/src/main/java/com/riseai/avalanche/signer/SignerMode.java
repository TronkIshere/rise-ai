package com.riseai.avalanche.signer;

/** DEV: ephemeral keys, LOCAL_SIMULATION only. KEYSTORE: operator-held encrypted testnet keys, AVALANCHE_FUJI only. */
public enum SignerMode {
    DEV,
    KEYSTORE
}
