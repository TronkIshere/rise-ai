package com.riseai.monitoring.rise;

import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.finding.Finding;
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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RiseFindingEngine {
    RiseEnvelopeReader reader;
    RiseProjectionRepository projection;
    List<RiseFindingRule> rules;
    MonitoringProperties properties;
    Clock clock;

    public RiseFindingEngine(RiseEnvelopeReader reader, RiseProjectionRepository projection, List<RiseFindingRule> rules,
                             MonitoringProperties properties, Clock clock) {
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
        for (RiseFindingRule rule : rules) {
            try {
                findings.addAll(rule.evaluate(envelope));
            } catch (RuntimeException ex) {
                log.error("Rise finding rule {} failed for event {}", rule.getId(), envelope.eventId(), ex);
            }
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

    private void record(RiseEnvelope envelope) {
        if (!projection.markProcessed(envelope)) {
            return;
        }
        switch (envelope.eventType()) {
            case RiseEventTypes.MILESTONE_EVIDENCE_SUBMITTED -> {
                String hash = envelope.text("fileHash");
                String evidenceId = envelope.text("evidenceId");
                String milestoneId = envelope.text("milestoneId");
                if (hash != null && evidenceId != null && milestoneId != null) {
                    projection.recordEvidenceHash(envelope, evidenceId, hash.toLowerCase(Locale.ROOT), milestoneId, envelope.reference("campaignId"));
                }
            }
            case RiseEventTypes.DISBURSEMENT_REQUESTED, RiseEventTypes.DISBURSEMENT_APPROVED, RiseEventTypes.DISBURSEMENT_REJECTED,
                 RiseEventTypes.DISBURSEMENT_RELEASED -> {
                String requestId = envelope.text("requestId");
                String milestoneId = envelope.text("milestoneId");
                if (requestId != null && milestoneId != null) {
                    projection.recordDisbursementEvent(envelope, requestId, milestoneId, envelope.reference("campaignId"), envelope.decimal("amount"));
                }
            }
            default -> {
            }
        }
    }
}
