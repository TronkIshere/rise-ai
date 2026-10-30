package com.riseai.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.entity.FindingOccurrence;
import com.riseai.entity.MonitoringFinding;
import com.riseai.entity.OutboxEvent;
import com.riseai.enums.FindingReviewStatus;
import com.riseai.enums.FindingSeverity;
import com.riseai.exception.FindingContractException;
import com.riseai.repository.FindingOccurrenceRepository;
import com.riseai.repository.MonitoringFindingRepository;
import com.riseai.service.AuditService;
import com.riseai.service.AlertNotifier;
import com.riseai.service.FindingIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FindingIngestionServiceImpl implements FindingIngestionService {
    private static final Pattern FINDING_KEY = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9._:-]{1,80}$");
    private static final Set<String> DETECTOR_TYPES = Set.of("RULE", "AI");
    private static final int MAX_FACTS = 20;
    private static final int MAX_TEXT = 2000;
    private static final int MAX_AI_NOTE = 400;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}\\s]+");

    private final MonitoringFindingRepository findingRepository;
    private final FindingOccurrenceRepository occurrenceRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AlertNotifier alertNotifier;

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public int ingest(OutboxEvent source, JsonNode findings) {
        if (findings == null || findings.isMissingNode() || findings.isNull()) {
            return 0;
        }
        if (!findings.isArray()) {
            throw new FindingContractException("findings must be an array");
        }
        List<IncomingFinding> parsed = new ArrayList<>();
        for (JsonNode node : findings) {
            parsed.add(parse(node));
        }
        int stored = 0;
        for (IncomingFinding finding : parsed) {
            if (upsert(source, finding)) {
                stored++;
            }
        }
        return stored;
    }

    private boolean upsert(OutboxEvent source, IncomingFinding incoming) {
        MonitoringFinding finding = findingRepository.findByFindingKeyForUpdate(incoming.findingKey()).orElse(null);
        if (finding == null) {
            finding = create(incoming);
        } else if (occurrenceRepository.existsByFindingIdAndSourceEventId(finding.getId(), source.getEventId())) {
            return false;
        }

        FindingSeverity previousSeverity = finding.getSeverity();
        boolean escalated = incoming.severity().compareTo(previousSeverity) > 0;
        boolean newCritical = incoming.severity() == FindingSeverity.CRITICAL && (finding.getOccurrenceCount() == 0 || escalated);
        if (escalated) {
            finding.setSeverity(incoming.severity());
            finding.setExplanation(incoming.explanation());
            finding.setFacts(incoming.facts());
            finding.setRecommendedAction(incoming.recommendedAction());
        }
        finding.setOccurrenceCount(finding.getOccurrenceCount() + 1);
        if (incoming.observedAt().isAfter(finding.getLastSeenAt())) {
            finding.setLastSeenAt(incoming.observedAt());
        }
        if (finding.getReviewStatus() != FindingReviewStatus.OPEN) {
            finding.setNewOccurrencesSinceReview(finding.getNewOccurrencesSinceReview() + 1);
        }
        findingRepository.saveAndFlush(finding);

        FindingOccurrence occurrence = new FindingOccurrence();
        occurrence.setFindingId(finding.getId());
        occurrence.setSourceEventId(source.getEventId());
        occurrence.setActorId(source.getActorId());
        occurrence.setSeverity(incoming.severity());
        occurrence.setObservedAt(incoming.observedAt());
        occurrence.setAiNote(incoming.aiNote());
        occurrenceRepository.save(occurrence);

        if (newCritical) {
            alertNotifier.alert("CRITICAL_FINDING", Map.of("findingId", finding.getId().toString(), "ruleId", incoming.ruleId(),
                    "category", incoming.category()));
        }
        if (escalated && finding.getReviewStatus() == FindingReviewStatus.DISMISSED) {
            finding.setReviewStatus(FindingReviewStatus.OPEN);
            auditService.transition(null, "MONITORING_FINDING_REOPENED", ENTITY, finding.getId(), FindingReviewStatus.DISMISSED,
                    FindingReviewStatus.OPEN, "Severity escalated from " + previousSeverity + " to " + incoming.severity());
        }
        return true;
    }

    private MonitoringFinding create(IncomingFinding incoming) {
        MonitoringFinding finding = new MonitoringFinding();
        finding.setFindingKey(incoming.findingKey());
        finding.setContractVersion(CONTRACT_VERSION);
        finding.setDetectorType(incoming.detectorType());
        finding.setRuleId(incoming.ruleId());
        finding.setRuleVersion(incoming.ruleVersion());
        finding.setCategory(incoming.category());
        finding.setSeverity(incoming.severity());
        finding.setSubjectType(incoming.subjectType());
        finding.setSubjectId(incoming.subjectId());
        finding.setCampaignId(incoming.campaignId());
        finding.setMilestoneId(incoming.milestoneId());
        finding.setDisbursementRequestId(incoming.disbursementRequestId());
        finding.setExplanation(incoming.explanation());
        finding.setRecommendedAction(incoming.recommendedAction());
        finding.setFacts(incoming.facts());
        finding.setReviewStatus(FindingReviewStatus.OPEN);
        finding.setOccurrenceCount(0);
        finding.setNewOccurrencesSinceReview(0);
        finding.setFirstSeenAt(incoming.observedAt());
        finding.setLastSeenAt(incoming.observedAt());
        return findingRepository.saveAndFlush(finding);
    }

    private IncomingFinding parse(JsonNode node) {
        if (!node.isObject()) {
            throw new FindingContractException("finding must be an object");
        }
        String contract = text(node, "contractVersion");
        if (!CONTRACT_VERSION.equals(contract)) {
            throw new FindingContractException("unsupported finding contract " + contract);
        }
        String key = text(node, "findingId");
        if (key == null || !FINDING_KEY.matcher(key).matches()) {
            throw new FindingContractException("findingId must be 64 lower-case hex characters");
        }
        JsonNode detector = node.path("detector");
        String detectorType = token(detector, "type");
        if (!DETECTOR_TYPES.contains(detectorType)) {
            throw new FindingContractException("unsupported detector type " + detectorType);
        }
        JsonNode subject = node.path("subject");
        JsonNode references = node.path("references");
        return new IncomingFinding(key, detectorType, token(detector, "id"), token(detector, "version"), token(node, "category"),
                severity(text(node, "severity")), token(subject, "type"), token(subject, "id"),
                uuid(references, "campaignId"), uuid(references, "milestoneId"), uuid(references, "disbursementRequestId"),
                limited(requiredText(node, "explanation")), token(node, "recommendedAction"), facts(node.path("facts")),
                instant(text(node, "observedAt")), note(text(node, "aiNote")));
    }

    private static String note(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = CONTROL_CHARACTERS.matcher(value).replaceAll(" ").strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > MAX_AI_NOTE ? cleaned.substring(0, MAX_AI_NOTE) : cleaned;
    }

    private List<Map<String, Object>> facts(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray() || node.size() > MAX_FACTS) {
            throw new FindingContractException("facts must be an array of at most " + MAX_FACTS + " items");
        }
        List<Map<String, Object>> facts = objectMapper.convertValue(node, new TypeReference<>() {
        });
        return facts;
    }

    private static FindingSeverity severity(String value) {
        try {
            return FindingSeverity.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new FindingContractException("unsupported severity " + value);
        }
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new FindingContractException("observedAt must be an ISO-8601 instant");
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new FindingContractException("references." + field + " must be a UUID");
        }
    }

    private static String token(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || !TOKEN.matcher(value).matches()) {
            throw new FindingContractException(field + " is missing or malformed");
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new FindingContractException(field + " is required");
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static String limited(String value) {
        return value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value;
    }

    private record IncomingFinding(String findingKey, String detectorType, String ruleId, String ruleVersion, String category,
                                   FindingSeverity severity, String subjectType, String subjectId, UUID campaignId, UUID milestoneId,
                                   UUID disbursementRequestId, String explanation, String recommendedAction,
                                   List<Map<String, Object>> facts, Instant observedAt, String aiNote) {
    }
}
