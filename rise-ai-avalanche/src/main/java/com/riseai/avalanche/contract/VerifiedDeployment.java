package com.riseai.avalanche.contract;

import java.util.Set;

/** A manifest that passed every check against the live endpoint. The gateway may only send transactions to its escrow and anchor. */
public record VerifiedDeployment(DeploymentManifest manifest) {

    public Set<String> allowedTargets() {
        return Set.of(manifest.escrow(), manifest.anchor());
    }
}
