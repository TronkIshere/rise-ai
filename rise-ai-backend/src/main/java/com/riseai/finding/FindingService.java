package com.riseai.finding;

import com.riseai.audit.AuditDtos.StatusHistoryResponse;
import com.riseai.audit.AuditService;
import com.riseai.campaign.CampaignRepository;
import com.riseai.common.exception.ApplicationException;
import com.riseai.common.exception.ErrorCode;
import com.riseai.finding.FindingDtos.FindingDetailResponse;
import com.riseai.finding.FindingDtos.FindingResponse;
import com.riseai.finding.FindingDtos.OccurrenceResponse;
import com.riseai.finding.FindingDtos.OutboxEventResponse;
import com.riseai.finding.FindingDtos.OutboxStatsResponse;
import com.riseai.outbox.MonitoringRelayProperties;
import com.riseai.outbox.OutboxEvent;
import com.riseai.outbox.OutboxEventRepository;
import com.riseai.outbox.OutboxStatus;
import com.riseai.security.AuthUser;
import com.riseai.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FindingService {
    private static final String OUTBOX_ENTITY = "OUTBOX_EVENT";

    private final MonitoringFindingRepository findingRepository;
    private final FindingOccurrenceRepository occurrenceRepository;
    private final CampaignRepository campaignRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditService auditService;
    private final MonitoringRelayProperties relayProperties;

    @Transactional(readOnly = true)
    public Page<FindingResponse> search(FindingReviewStatus status, FindingSeverity severity, String category, UUID campaignId, Pageable pageable) {
        String normalizedCategory = category == null || category.isBlank() ? null : category.trim();
        return findingRepository.search(status, severity, normalizedCategory, campaignId, pageable).map(FindingResponse::from);
    }

    @Transactional(readOnly = true)
    public FindingDetailResponse get(UUID id) {
        MonitoringFinding finding = load(id);
        List<OccurrenceResponse> occurrences = occurrenceRepository.findByFindingIdOrderByObservedAtAsc(id).stream()
                .map(OccurrenceResponse::from)
                .toList();
        return new FindingDetailResponse(FindingResponse.from(finding), occurrences);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> history(UUID id) {
        load(id);
        return auditService.history(FindingIngestionService.ENTITY, id);
    }

    @Transactional
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
    public OutboxStatsResponse outboxStats() {
        Instant oldest = outboxEventRepository.oldestOccurredAt(List.of(OutboxStatus.PENDING, OutboxStatus.IN_FLIGHT)).orElse(null);
        Long age = oldest == null ? null : Duration.between(oldest, Instant.now()).toSeconds();
        return new OutboxStatsResponse(relayProperties.enabled(), outboxEventRepository.countByStatus(OutboxStatus.PENDING),
                outboxEventRepository.countByStatus(OutboxStatus.IN_FLIGHT), outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED),
                outboxEventRepository.countByStatus(OutboxStatus.FAILED), oldest, age);
    }

    @Transactional(readOnly = true)
    public Page<OutboxEventResponse> outboxEvents(OutboxStatus status, Pageable pageable) {
        Page<OutboxEvent> page = status == null ? outboxEventRepository.findAll(pageable) : outboxEventRepository.findByStatus(status, pageable);
        return page.map(OutboxEventResponse::from);
    }

    @Transactional
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
