package com.riseai.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LayeredArchitectureTest {
    private static final Path SOURCES = Path.of("src", "main", "java", "com", "riseai");
    private static final Pattern FINDING_TYPES = Pattern.compile(
            "\\b(MonitoringFinding|FindingOccurrence|MonitoringFindingRepository|FindingOccurrenceRepository|FindingService|FindingIngestionService)\\b");
    private static final Pattern BUSINESS_SERVICES = Pattern.compile(
            "\\b(Adjustment|Campaign|Disbursement|Dispute|Donation|Evidence|Milestone|Organization|Refund|Settlement|User|FundraisingClose|Reconciliation|EscrowQuery)Service\\b|\\bEscrowEngine\\b");
    private static final Set<String> FINDING_AWARE_IMPLEMENTATIONS = Set.of("FindingServiceImpl.java", "FindingIngestionServiceImpl.java",
            "OutboxRelayImpl.java");

    @Test
    void controllersDependOnServiceInterfacesOnly() throws IOException {
        for (Path file : javaFiles("controller")) {
            assertThat(read(file)).as("%s", file)
                    .doesNotContain("import com.riseai.repository.")
                    .doesNotContain("import com.riseai.entity.")
                    .doesNotContain("import com.riseai.service.impl.");
        }
    }

    @Test
    void repositoriesAndEntitiesDoNotDependOnUpperLayers() throws IOException {
        for (String layer : List.of("repository", "entity")) {
            for (Path file : javaFiles(layer)) {
                assertThat(read(file)).as("%s", file)
                        .doesNotContain("import com.riseai.service.")
                        .doesNotContain("import com.riseai.controller.");
            }
        }
    }

    @Test
    void noBusinessServiceDependsOnMonitoringFindings() throws IOException {
        for (Path file : javaFiles("service/impl")) {
            if (!FINDING_AWARE_IMPLEMENTATIONS.contains(file.getFileName().toString())) {
                assertThat(FINDING_TYPES.matcher(read(file)).find()).as("%s must not depend on monitoring findings", file).isFalse();
            }
        }
    }

    @Test
    void eventDeliveryDoesNotCallBusinessServices() throws IOException {
        List<Path> files = List.of(SOURCES.resolve("service/impl/OutboxRelayImpl.java"), SOURCES.resolve("service/impl/DomainEventPublisherImpl.java"),
                SOURCES.resolve("client/MonitoringClient.java"), SOURCES.resolve("mapper/RiseEvents.java"));
        for (Path file : files) {
            assertThat(BUSINESS_SERVICES.matcher(read(file)).find())
                    .as("%s must only read entities, never call business services or the escrow engine", file).isFalse();
        }
    }

    private static List<Path> javaFiles(String layer) throws IOException {
        Path directory = SOURCES.resolve(layer);
        assertThat(directory).isDirectory();
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> result = files.filter(path -> path.toString().endsWith(".java")).toList();
            assertThat(result).isNotEmpty();
            return result;
        }
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
