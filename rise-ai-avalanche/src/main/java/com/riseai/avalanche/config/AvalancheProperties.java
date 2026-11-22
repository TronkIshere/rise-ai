package com.riseai.avalanche.config;

import com.riseai.avalanche.env.AvalancheEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** No default for the environment and no property that can add a chain id or enable another environment. */
@ConfigurationProperties(prefix = "avalanche")
public record AvalancheProperties(AvalancheEnvironment environment, Rpc rpc, Signers signers, String deploymentManifest,
                                  String internalApiKey, boolean readOnly) {

    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public AvalancheProperties { }

    /** Existing callers retain the original write-enabled default. */
    public AvalancheProperties(AvalancheEnvironment environment, Rpc rpc, Signers signers, String deploymentManifest, String internalApiKey) {
        this(environment, rpc, signers, deploymentManifest, internalApiKey, false);
    }

    /**
     * Signers are opt-in. Mode "dev" (default): ephemeral keys, LOCAL_SIMULATION only. Mode "keystore": two operator-held
     * encrypted testnet keystores, AVALANCHE_FUJI only (ADR-009). Any other combination fails startup.
     */
    public record Signers(boolean enabled, String mode, Keystore operator, Keystore approver) {

        public boolean keystoreMode() {
            return "keystore".equalsIgnoreCase(mode == null ? "" : mode.trim());
        }
    }

    public record Keystore(String path, String passwordFile, String address) {
    }

    public boolean signersEnabled() {
        return signers != null && signers.enabled();
    }

    public record Rpc(Provider primary, Provider secondary, int timeoutMs, int minGapMs) {
    }

    public record Provider(String url) {
    }

    public String primaryUrl() {
        return rpc == null || rpc.primary() == null ? null : rpc.primary().url();
    }

    public String secondaryUrl() {
        String url = rpc == null || rpc.secondary() == null ? null : rpc.secondary().url();
        return url == null || url.isBlank() ? null : url;
    }

    public int timeoutMs() {
        return rpc == null || rpc.timeoutMs() <= 0 ? 10_000 : rpc.timeoutMs();
    }

    public int minGapMs() {
        return rpc == null || rpc.minGapMs() < 0 ? 1_000 : rpc.minGapMs();
    }
}
