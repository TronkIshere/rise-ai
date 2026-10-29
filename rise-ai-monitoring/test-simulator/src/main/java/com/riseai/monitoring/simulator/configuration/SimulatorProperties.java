package com.riseai.monitoring.simulator.configuration;

import com.riseai.monitoring.simulator.scenario.Category;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "simulator")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SimulatorProperties {

    @NotNull
    URI coreUrl = URI.create("http://localhost:8081");

    @Min(1)
    @Max(1000000)
    int scenarioCount = 100000;

    long seed = 42L;

    @Min(1)
    @Max(64)
    int concurrency = 4;

    @NotNull
    Duration requestTimeout = Duration.ofSeconds(30);

    @NotBlank
    String reportDirectory = "reports";

    boolean includeAiScenarios = true;

    boolean failFast = true;

    @NotBlank
    String suite = "generic";

    String serviceKey;

    @NotNull
    java.math.BigDecimal riseApprovalAmount = new java.math.BigDecimal("50000000");

    @NotNull
    java.math.BigDecimal riseAdjustmentAmount = new java.math.BigDecimal("10000000");

    @Min(2)
    int riseMaxRequestsPerMilestone = 3;

    @NotNull
    Map<Category, Integer> distribution = new LinkedHashMap<>(Category.defaultDistribution());
}
