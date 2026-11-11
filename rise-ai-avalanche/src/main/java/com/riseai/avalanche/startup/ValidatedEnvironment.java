package com.riseai.avalanche.startup;

import com.riseai.avalanche.env.AvalancheEnvironment;

public record ValidatedEnvironment(AvalancheEnvironment environment, long chainId, String clientFamily, String endpointHost) {
}
