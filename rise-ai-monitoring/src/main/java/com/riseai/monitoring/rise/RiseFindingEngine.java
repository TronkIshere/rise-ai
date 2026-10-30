package com.riseai.monitoring.rise;

import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.exception.ApplicationException;
import com.riseai.monitoring.exception.ErrorCode;
import com.riseai.monitoring.observability.MonitoringMetrics;
import com.riseai.monitoring.repository.RiseProjectionRepository;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RiseFindingEngine {
    private static final int ROW_ID_LENGTH = 36;
    private static final int DISPUTE_ID_LENGTH = 80;
    private static final Pattern STORABLE_ID = Pattern.compile("^[A-Za-z0-9._:-]+$");

    RiseEnvelopeReader reader;
    RiseProjectionRepository projection;
    List<RiseFindingRule> rules;
    MonitoringProperties properties;
    MonitoringMetrics metrics;
    Clock clock;

    public RiseFindingEngine(RiseEnvelopeReader reader, RiseProjectionRepository projection, List<RiseFindingRule> rules,
                             MonitoringProperties properties, MonitoringMetrics metrics, Clock clock) {
        Set<String> identifiers = new HashSet<>();
        for (RiseFindingRule rule : rules) {
            if (rule.getId() == null || !identifiers.add(rule.getId())) {
                throw new IllegalStateException("Rise finding rule identifiers must be unique and non-null: " + rule.getId());
            }
        }
        this.reader = reader;
        this.projection = projection;
        this.rules = rules.stream().sorted(Comparator.comparing(RiseFindingRule::getId)).toList();
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public List<RiseFindingRule> getRules() {
        return rules;
    }

    @Transactional
    public List<Finding> evaluate(MonitoringEvent event) {
        Optional<RiseEnvelope> read = reader.read(event);
        if (read.isEmpty()) {
            return List.of();
        }
        RiseEnvelope envelope = read.get();
        List<Finding> findings = new ArrayList<>();
        List<String> failedRules = new ArrayList<>();
        for (RiseFindingRule rule : rules) {
            try {
                findings.addAll(rule.evaluate(envelope));
            } catch (RuntimeException ex) {
                failedRules.add(rule.getId());
                metrics.riseRuleFailed(rule.getId());
                log.error("Rise finding rule {} failed for event {}", rule.getId(), envelope.eventId(), ex);
            }
        }
        if (!failedRules.isEmpty()) {
            throw new ApplicationException(ErrorCode.RULE_EVALUATION_FAILED, String.join(",", failedRules));
        }
        record(envelope);
        return List.copyOf(findings);
    }

    @Scheduled(cron = "${monitoring.rise.purge-cron:0 15 4 * * *}")
    @Transactional
    public void purgeExpiredProjection() {
        int deleted = projection.purgeOlderThan(clock.instant().minus(properties.getRise().getProjectionRetention()));
        if (deleted > 0) {
            log.info("Purged {} Rise projection rows past retention", deleted);
        }
    }

    private static String storable(String value, int maxLength) {
        return value != null && value.length() <= maxLength && STORABLE_ID.matcher(value).matches() ? value : null;
    }

    private void notRecorded(RiseEnvelope envelope) {
        log.warn("Rise event {} ({}) has a missing or unstorable identifier or hash and was not added to the projection; later events cannot be compared with it",
                envelope.eventId(), envelope.eventType());
    }

    private void record(RiseEnvelope envelope) {
        if (!projection.markProcessed(envelope)) {
            return;
        }
        switch (envelope.eventType()) {
            case RiseEventTypes.MILESTONE_EVIDENCE_SUBMITTED -> {
                String hash = envelope.fileHash();
                String evidenceId = storable(envelope.text("evidenceId"), ROW_ID_LENGTH);
                String milestoneId = storable(envelope.text("milestoneId"), ROW_ID_LENGTH);
                if (hash != null && evidenceId != null && milestoneId != null) {
                    projection.recordEvidenceHash(envelope, evidenceId, hash, milestoneId, storable(envelope.reference("campaignId"), ROW_ID_LENGTH));
                } else if (hash != null) {
                    notRecorded(envelope);
                }
            }
            case RiseEventTypes.DISBURSEMENT_REQUESTED, RiseEventTypes.DISBURSEMENT_APPROVED, RiseEventTypes.DISBURSEMENT_REJECTED,
                 RiseEventTypes.DISBURSEMENT_RELEASED -> {
                String requestId = storable(envelope.text("requestId"), ROW_ID_LENGTH);
                String milestoneId = storable(envelope.text("milestoneId"), ROW_ID_LENGTH);
                if (requestId != null && milestoneId != null) {
                    projection.recordDisbursementEvent(envelope, requestId, milestoneId, storable(envelope.reference("campaignId"), ROW_ID_LENGTH), envelope.decimal("amount"));
                } else {
                    notRecorded(envelope);
                }
            }
            case RiseEventTypes.DISPUTE_OPENED -> {
                String disputeId = storable(envelope.text("disputeId"), DISPUTE_ID_LENGTH);
                String milestoneId = storable(envelope.text("milestoneId"), DISPUTE_ID_LENGTH);
                if ("MISUSE_OF_FUNDS".equals(envelope.text("disputeType")) && disputeId != null && milestoneId != null) {
                    projection.recordDisputeEvent(envelope, disputeId, milestoneId, storable(envelope.reference("campaignId"), ROW_ID_LENGTH));
                } else if ("MISUSE_OF_FUNDS".equals(envelope.text("disputeType"))) {
                    notRecorded(envelope);
                }
            }
            default -> {
            }
        }
    }
}
