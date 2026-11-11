package com.riseai.avalanche.signer;

/** One distinct ephemeral key per role, for LOCAL_SIMULATION. Monitoring and AI never map to a role. */
public enum SignerRole {
    OPERATOR,
    APPROVER,
    ANCHORER,
    RELAYER,
    PAUSER,
    ADMIN,
    DEPLOYER
}
