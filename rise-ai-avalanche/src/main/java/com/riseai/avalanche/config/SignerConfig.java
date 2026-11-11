package com.riseai.avalanche.config;

import com.riseai.avalanche.env.EnvironmentGuard;
import com.riseai.avalanche.env.EnvironmentGuardException;
import com.riseai.avalanche.signer.DevSignerFactory;
import com.riseai.avalanche.signer.KeystoreSignerFactory;
import com.riseai.avalanche.signer.SignerRegistry;
import com.riseai.avalanche.startup.EnvironmentStartupValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SignerConfig {

    private static final Logger log = LoggerFactory.getLogger(SignerConfig.class);

    /**
     * Exists only when explicitly enabled. Dev signers outside LOCAL_SIMULATION, or keystore signers outside AVALANCHE_FUJI,
     * abort startup before any key is loaded or generated.
     */
    @Bean
    @ConditionalOnProperty(name = "avalanche.signers.enabled", havingValue = "true")
    SignerRegistry signerRegistry(AvalancheProperties properties, EnvironmentStartupValidator validator) {
        AvalancheProperties.Signers signers = properties.signers();
        String mode = signers.mode() == null || signers.mode().isBlank() ? "dev" : signers.mode().trim();
        if (signers.keystoreMode()) {
            EnvironmentGuard.requireKeystoreSignerEnvironment(properties.environment());
            SignerRegistry registry = KeystoreSignerFactory.createRegistry(validator.validated(), keystore(signers.operator()), keystore(signers.approver()));
            log.info("Keystore signers loaded for AVALANCHE_FUJI (testnet only, keys held in memory): {}", registry.addresses());
            return registry;
        }
        if (!"dev".equalsIgnoreCase(mode)) {
            throw new EnvironmentGuardException("SIGNER_MODE_UNKNOWN", "avalanche.signers.mode must be dev or keystore");
        }
        EnvironmentGuard.requireSignerEnvironment(properties.environment());
        SignerRegistry registry = DevSignerFactory.createRegistry(validator.validated());
        log.info("Ephemeral development signers created for LOCAL_SIMULATION (in memory only, never persisted): {}", registry.addresses());
        return registry;
    }

    private static KeystoreSignerFactory.Keystore keystore(AvalancheProperties.Keystore value) {
        return value == null ? null : new KeystoreSignerFactory.Keystore(value.path(), value.passwordFile(), value.address());
    }
}
