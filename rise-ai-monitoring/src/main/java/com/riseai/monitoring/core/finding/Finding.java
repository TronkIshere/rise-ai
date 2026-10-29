package com.riseai.monitoring.core.finding;

import com.riseai.monitoring.core.event.Severity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Getter
@Builder(toBuilder = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Finding {
    public static final String CONTRACT_VERSION = "finding.v1";
    public static final String RULE_DETECTOR = "RULE";

    String findingId;
    String ruleId;
    String ruleVersion;
    FindingCategory category;
    Severity severity;
    String subjectType;
    String subjectId;

    @Builder.Default
    Map<String, String> references = Map.of();

    @Builder.Default
    List<String> sourceEventIds = List.of();

    @Builder.Default
    List<FindingFact> facts = List.of();

    String explanation;
    RecommendedAction recommendedAction;
    Instant observedAt;
    String aiNote;

    public static String idFor(String ruleId, String subjectType, String subjectId, String discriminator) {
        String canonical = ruleId + "|" + subjectType + "|" + subjectId + "|" + (discriminator == null ? "" : discriminator);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }
}
