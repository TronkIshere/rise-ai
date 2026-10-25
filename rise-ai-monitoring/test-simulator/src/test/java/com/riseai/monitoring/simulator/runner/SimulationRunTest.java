package com.riseai.monitoring.simulator.runner;

import com.riseai.monitoring.simulator.configuration.SimulatorProperties;
import com.riseai.monitoring.simulator.testsupport.StubCoreServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationRunTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    @TempDir
    Path tempDir;

    @Test
    void stopsBeforeTheWorkloadWhenTheCoreIsUnreachable() throws Exception {
        SimulatorProperties properties = properties(URI.create("http://127.0.0.1:" + StubCoreServer.unusedPort()), 10);

        int exitCode = run(properties);

        assertThat(exitCode).isEqualTo(SimulationRun.EXIT_UNREACHABLE);
        assertThat(text()).contains("not reachable").contains("core-simulator.yml");
        assertThat(Files.list(tempDir).count()).isZero();
    }

    @Test
    void stopsWhenCalibrationShowsAMisconfiguredCoreAndFailFastIsEnabled() throws Exception {
        try (StubCoreServer server = StubCoreServer.start()) {
            int exitCode = run(properties(URI.create(server.getBaseUrl()), 10));

            assertThat(exitCode).isEqualTo(SimulationRun.EXIT_MISCONFIGURED);
            assertThat(text()).contains("Calibration failed: latencyMs=1001 is flagged by attribute-threshold");
            assertThat(server.getMonitorRequests()).isEqualTo(4);
        }
    }

    @Test
    void writesAllReportsForASmallRunAgainstAStubCore() throws Exception {
        try (StubCoreServer server = StubCoreServer.start()) {
            SimulatorProperties properties = properties(URI.create(server.getBaseUrl()), 60);
            properties.setFailFast(false);

            int exitCode = run(properties);

            assertThat(exitCode).isEqualTo(SimulationRun.EXIT_OK);
            Path runDirectory;
            try (Stream<Path> runs = Files.list(tempDir)) {
                runDirectory = runs.findFirst().orElseThrow();
            }
            for (String file : List.of("summary.json", "category-summary.csv", "results.csv", "failures.csv", "unscored.csv", "run-config.json")) {
                assertThat(runDirectory.resolve(file)).exists();
            }
            assertThat(Files.readAllLines(runDirectory.resolve("results.csv"), StandardCharsets.UTF_8)).hasSize(61);
            JsonNode summary = objectMapper.readTree(runDirectory.resolve("summary.json").toFile());
            assertThat(summary.path("scenarioCount").asInt()).isEqualTo(60);
            assertThat(summary.at("/counts/completed").asInt()).isEqualTo(60);
            JsonNode runConfig = objectMapper.readTree(runDirectory.resolve("run-config.json").toFile());
            assertThat(runConfig.at("/configuration/seed").asLong()).isEqualTo(42);
            assertThat(runConfig.at("/core/modelName").asText()).isEqualTo("test-model");
            assertThat(text()).contains("MONITORING CORE TEST REPORT").contains("Progress: 60 / 60");
        }
    }

    private SimulatorProperties properties(URI coreUrl, int scenarioCount) {
        SimulatorProperties properties = new SimulatorProperties();
        properties.setCoreUrl(coreUrl);
        properties.setScenarioCount(scenarioCount);
        properties.setConcurrency(3);
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.setReportDirectory(tempDir.toString());
        return properties;
    }

    private int run(SimulatorProperties properties) throws Exception {
        return new SimulationRun(properties, objectMapper, new PrintStream(output, true, StandardCharsets.UTF_8)).execute();
    }

    private String text() {
        return output.toString(StandardCharsets.UTF_8);
    }
}
