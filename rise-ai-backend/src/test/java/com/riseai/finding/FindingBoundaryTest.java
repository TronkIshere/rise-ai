package com.riseai.finding;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FindingBoundaryTest {
    private static final Path SOURCES = Path.of("src", "main", "java", "com", "riseai");
    private static final List<String> BUSINESS_PACKAGES = List.of("adjustment", "campaign", "disbursement", "dispute", "donation", "escrow",
            "evidence", "milestone", "organization", "refund", "settlement", "user");

    @Test
    void noBusinessPackageDependsOnMonitoringFindings() throws IOException {
        for (String businessPackage : BUSINESS_PACKAGES) {
            Path directory = SOURCES.resolve(businessPackage);
            assertThat(directory).isDirectory();
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    assertThat(Files.readString(file, StandardCharsets.UTF_8))
                            .as("%s must not depend on monitoring findings", file)
                            .doesNotContain("com.riseai.finding");
                }
            }
        }
    }

    @Test
    void outboxDoesNotCallBusinessServices() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES.resolve("outbox"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file, StandardCharsets.UTF_8))
                        .as("%s must only read entities, never call business services or the escrow engine", file)
                        .doesNotContainPattern("import com\\.riseai\\.(?!finding\\.)[a-z]+\\.[A-Za-z]*Service;")
                        .doesNotContain("EscrowEngine;");
            }
        }
    }
}
