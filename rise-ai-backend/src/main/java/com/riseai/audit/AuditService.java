package com.riseai.audit;

import com.riseai.common.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private static final int MAX_REASON = 2000;

    private final AuditLogRepository auditLogRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    @Transactional
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
        record(actorId, action, entityType, entityId, fromStatus, toStatus, meta("reason", trimmed));
    }

    @Transactional(readOnly = true)
    public Page<AuditDtos.AuditLogResponse> search(UUID actorId, String entityType, UUID entityId, String action, Pageable pageable) {
        return auditLogRepository.search(actorId, blankToNull(entityType), entityId, blankToNull(action), pageable)
                .map(a -> AuditDtos.AuditLogResponse.from(a, true));
    }

    @Transactional(readOnly = true)
    public Page<AuditDtos.AuditLogResponse> byActor(UUID actorId, Pageable pageable) {
        return auditLogRepository.findByActorUserId(actorId, pageable).map(a -> AuditDtos.AuditLogResponse.from(a, false));
    }

    @Transactional(readOnly = true)
    public List<AuditDtos.StatusHistoryResponse> history(String entityType, UUID entityId) {
        return statusHistoryRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType, entityId).stream()
                .map(AuditDtos.StatusHistoryResponse::from)
                .toList();
    }

    public static Map<String, Object> meta(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value != null) {
                map.put(String.valueOf(keyValues[i]), value instanceof BigDecimal b ? b.toPlainString() : value.toString());
            }
        }
        return map;
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
