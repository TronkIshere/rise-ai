package com.riseai.service.impl;

import com.riseai.configuration.MonitoringRelayProperties;
import com.riseai.dto.response.AlertResponse;
import com.riseai.dto.response.AlertStatsResponse;
import com.riseai.dto.response.FindingDetailResponse;
import com.riseai.dto.response.FindingResponse;
import com.riseai.dto.response.OccurrenceResponse;
import com.riseai.dto.response.OutboxEventResponse;
import com.riseai.dto.response.OutboxStatsResponse;
import com.riseai.dto.response.RulePrecisionResponse;
import com.riseai.dto.response.StatusHistoryResponse;
import com.riseai.entity.AlertNotification;
import com.riseai.entity.MonitoringFinding;
import com.riseai.entity.OutboxEvent;
import com.riseai.enums.AlertStatus;
import com.riseai.enums.FindingReviewStatus;
import com.riseai.enums.FindingSeverity;
import com.riseai.enums.OutboxStatus;
import com.riseai.enums.Role;
import com.riseai.exception.ApplicationException;
import com.riseai.exception.ErrorCode;
import com.riseai.repository.AlertNotificationRepository;
import com.riseai.repository.CampaignRepository;
import com.riseai.repository.FindingOccurrenceRepository;
import com.riseai.repository.MonitoringFindingRepository;
import com.riseai.repository.OutboxEventRepository;
import com.riseai.security.AuthUser;
import com.riseai.service.AuditService;
import com.riseai.service.FindingIngestionService;
import com.riseai.service.FindingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FindingServiceImpl implements FindingService {
    private static final String OUTBOX_ENTITY = "OUTBOX_EVENT";
    private static final String ALERT_ENTITY = "ALERT_NOTIFICATION";

    private final MonitoringFindingRepository findingRepository;
    private final FindingOccurrenceRepository occurrenceRepository;
    private final CampaignRepository campaignRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AlertNotificationRepository alertRepository;
    private final AuditService auditService;
    private final MonitoringRelayProperties relayProperties;
    private final AlertWebhookClient alertWebhookClient;

    @Transactional(readOnly = true)
    @Override
    public Page<FindingResponse> search(FindingReviewStatus status, FindingSeverity severity, String category, UUID campaignId, Pageable pageable) {
        String normalizedCategory = category == null || category.isBlank() ? null : category.trim();
        return findingRepository.search(status, severity, normalizedCategory, campaignId, pageable).map(FindingResponse::from);
    }

    @Transactional(readOnly = true)
    @Override
    public FindingDetailResponse get(UUID id) {
        MonitoringFinding finding = load(id);
        List<OccurrenceResponse> occurrences = occurrenceRepository.findByFindingIdOrderByObservedAtAsc(id).stream()
                .map(OccurrenceResponse::from)
                .toList();
        return new FindingDetailResponse(FindingResponse.from(finding), occurrences);
    }

    @Transactional(readOnly = true)
    @Override
    public List<StatusHistoryResponse> history(UUID id) {
        load(id);
        return auditService.history(FindingIngestionService.ENTITY, id);
    }

    @Transactional
    @Override
    public FindingResponse review(AuthUser actor, UUID id, FindingReviewStatus target, String note) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        MonitoringFinding finding = findingRepository.findByIdForUpdate(id).orElseThrow(() -> ApplicationException.notFound("Monitoring finding"));
        if (!FindingReviewStatus.REVIEW_TARGETS.contains(target) || !finding.getReviewStatus().canMoveTo(target)) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Finding cannot move from " + finding.getReviewStatus() + " to " + target);
        }
        requireIndependentReviewer(actor, finding);
        FindingReviewStatus previous = finding.getReviewStatus();
        finding.setReviewStatus(target);
        finding.setReviewedBy(actor.id());
        finding.setReviewedAt(Instant.now());
        finding.setReviewNote(note.trim());
        finding.setNewOccurrencesSinceReview(0);
        auditService.transition(actor.id(), "MONITORING_FINDING_REVIEWED", FindingIngestionService.ENTITY, finding.getId(), previous, target, note.trim());
        return FindingResponse.from(finding);
    }

    @Transactional(readOnly = true)
    @Override
    public List<RulePrecisionResponse> rulePrecision() {
        Map<String, Map<FindingReviewStatus, Long>> byRule = new TreeMap<>();
        for (Object[] row : findingRepository.countByRuleAndReviewStatus()) {
            byRule.computeIfAbsent((String) row[0], key -> new EnumMap<>(FindingReviewStatus.class))
                    .put((FindingReviewStatus) row[1], (Long) row[2]);
        }
        return byRule.entrySet().stream().map(entry -> {
            Map<FindingReviewStatus, Long> counts = entry.getValue();
            long open = counts.getOrDefault(FindingReviewStatus.OPEN, 0L);
            long acknowledged = counts.getOrDefault(FindingReviewStatus.ACKNOWLEDGED, 0L);
            long confirmed = counts.getOrDefault(FindingReviewStatus.CONCERN_CONFIRMED, 0L);
            long dismissed = counts.getOrDefault(FindingReviewStatus.DISMISSED, 0L);
            long decided = confirmed + dismissed;
            BigDecimal precision = decided == 0 ? null
                    : BigDecimal.valueOf(confirmed).divide(BigDecimal.valueOf(decided), 4, RoundingMode.HALF_UP);
            return new RulePrecisionResponse(entry.getKey(), open + acknowledged + decided, open, acknowledged, confirmed, dismissed, precision);
        }).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public OutboxStatsResponse outboxStats() {
        Instant oldest = outboxEventRepository.oldestOccurredAt(List.of(OutboxStatus.PENDING, OutboxStatus.IN_FLIGHT)).orElse(null);
        Long age = oldest == null ? null : Duration.between(oldest, Instant.now()).toSeconds();
        return new OutboxStatsResponse(relayProperties.enabled(), outboxEventRepository.countByStatus(OutboxStatus.PENDING),
                outboxEventRepository.countByStatus(OutboxStatus.IN_FLIGHT), outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED),
                outboxEventRepository.countByStatus(OutboxStatus.FAILED), oldest, age);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OutboxEventResponse> outboxEvents(OutboxStatus status, Pageable pageable) {
        Page<OutboxEvent> page = status == null ? outboxEventRepository.findAll(pageable) : outboxEventRepository.findByStatus(status, pageable);
        return page.map(OutboxEventResponse::from);
    }

    @Transactional
    @Override
    public OutboxEventResponse requeue(AuthUser actor, UUID id, String reason) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        OutboxEvent event = outboxEventRepository.findById(id).orElseThrow(() -> ApplicationException.notFound("Outbox event"));
        if (event.getStatus() != OutboxStatus.FAILED) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Only FAILED outbox events can be requeued");
        }
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(Instant.now());
        event.setLeaseUntil(null);
        event.setLastError(null);
        auditService.transition(actor.id(), "OUTBOX_EVENT_REQUEUED", OUTBOX_ENTITY, event.getId(), OutboxStatus.FAILED, OutboxStatus.PENDING, reason);
        return OutboxEventResponse.from(event);
    }

    @Transactional(readOnly = true)
    @Override
    public AlertStatsResponse alertStats() {
        Instant oldest = alertRepository.oldestUndeliveredCreatedAt().orElse(null);
        Long age = oldest == null ? null : Duration.between(oldest, Instant.now()).toSeconds();
        return new AlertStatsResponse(alertWebhookClient.configured(), alertRepository.countByStatus(AlertStatus.PENDING),
                alertRepository.countByStatus(AlertStatus.RETRYING), alertRepository.countByStatus(AlertStatus.SENT),
                alertRepository.countByStatus(AlertStatus.FAILED), oldest, age);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<AlertResponse> alerts(AlertStatus status, Pageable pageable) {
        Page<AlertNotification> page = status == null ? alertRepository.findAll(pageable) : alertRepository.findByStatus(status, pageable);
        return page.map(AlertResponse::from);
    }

    @Transactional
    @Override
    public AlertResponse requeueAlert(AuthUser actor, UUID id, String reason) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        AlertNotification alert = alertRepository.lockById(id).orElseThrow(() -> ApplicationException.notFound("Alert notification"));
        if (alert.getStatus() != AlertStatus.FAILED) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Only FAILED alerts can be requeued");
        }
        alert.setStatus(AlertStatus.PENDING);
        alert.setAttempts(0);
        alert.setNextAttemptAt(Instant.now());
        alert.setFailedAt(null);
        alert.setLastError(null);
        auditService.transition(actor.id(), "ALERT_NOTIFICATION_REQUEUED", ALERT_ENTITY, alert.getId(), AlertStatus.FAILED, AlertStatus.PENDING, reason);
        return AlertResponse.from(alert);
    }

    private void requireIndependentReviewer(AuthUser actor, MonitoringFinding finding) {
        if (finding.getCampaignId() != null) {
            campaignRepository.findById(finding.getCampaignId())
                    .filter(campaign -> actor.is(campaign.getCreatorId()))
                    .ifPresent(campaign -> {
                        throw new ApplicationException(ErrorCode.CONFLICT_OF_INTEREST, "Campaign creators cannot review findings about their campaign");
                    });
        }
        if (occurrenceRepository.actorIds(finding.getId()).contains(actor.id())) {
            throw new ApplicationException(ErrorCode.CONFLICT_OF_INTEREST, "You performed an action this finding is about");
        }
    }

    private MonitoringFinding load(UUID id) {
        return findingRepository.findById(id).orElseThrow(() -> ApplicationException.notFound("Monitoring finding"));
    }
}
