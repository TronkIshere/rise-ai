package com.riseai.monitoring.dto.response.monitoring;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.finding.FindingCategory;
import com.riseai.monitoring.core.finding.RecommendedAction;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FindingResponse {
    String findingId;
    String contractVersion;
    Detector detector;
    FindingCategory category;
    Severity severity;
    Subject subject;
    Map<String, String> references;
    List<String> sourceEventIds;
    List<Fact> facts;
    String explanation;
    RecommendedAction recommendedAction;
    Instant observedAt;
    String aiNote;

    public record Detector(String type, String id, String version) {
    }

    public record Subject(String type, String id) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Fact(String name, Object value, String sourceEventId) {
    }
}
