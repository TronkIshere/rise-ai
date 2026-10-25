package com.riseai.service.impl;

import com.riseai.dto.response.AuditLogResponse;
import com.riseai.dto.response.StatusHistoryResponse;
import com.riseai.entity.AuditLog;
import com.riseai.entity.StatusHistory;
import com.riseai.repository.AuditLogRepository;
import com.riseai.repository.StatusHistoryRepository;
import com.riseai.service.AuditService;
import com.riseai.util.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {
    private static final int MAX_REASON = 2000;

    private final AuditLogRepository auditLogRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    @Transactional
    @Override
    public void record(UUID actorId, String action, String entityType, UUID entityId, String previousState, String newState, Map<String, Object> metadata) {
        AuditLog log = new AuditLog();
        log.setActorUserId(actorId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setPreviousState(previousState);
        log.setNewState(newState);
        log.setCorrelationId(RequestContext.correlationId());
        log.setIpAddress(RequestContext.clientIp());
        log.setMetadata(metadata == null ? Map.of() : metadata);
        auditLogRepository.save(log);
    }

    @Transactional
    @Override
    public void transition(UUID actorId, String action, String entityType, UUID entityId, Object from, Object to, String reason) {
        String fromStatus = from == null ? null : from.toString();
        String toStatus = to.toString();
        String trimmed = truncate(reason);
        StatusHistory history = new StatusHistory();
        history.setEntityType(entityType);
        history.setEntityId(entityId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(actorId);
        history.setReason(trimmed);
        statusHistoryRepository.save(history);
        record(actorId, action, entityType, entityId, fromStatus, toStatus, AuditService.meta("reason", trimmed));
    }

    @Transactional(readOnly = true)
    @Override
    public Page<AuditLogResponse> search(UUID actorId, String entityType, UUID entityId, String action, Pageable pageable) {
        return auditLogRepository.search(actorId, blankToNull(entityType), entityId, blankToNull(action), pageable)
                .map(a -> AuditLogResponse.from(a, true));
    }

    @Transactional(readOnly = true)
    @Override
    public Page<AuditLogResponse> byActor(UUID actorId, Pageable pageable) {
        return auditLogRepository.findByActorUserId(actorId, pageable).map(a -> AuditLogResponse.from(a, false));
    }

    @Transactional(readOnly = true)
    @Override
    public List<StatusHistoryResponse> history(String entityType, UUID entityId) {
        return statusHistoryRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType, entityId).stream()
                .map(StatusHistoryResponse::from)
                .toList();
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_REASON ? value.substring(0, MAX_REASON) : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
