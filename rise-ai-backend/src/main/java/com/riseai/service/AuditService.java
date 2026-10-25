package com.riseai.service;

import com.riseai.dto.response.AuditLogResponse;
import com.riseai.dto.response.StatusHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AuditService {
    void record(UUID actorId, String action, String entityType, UUID entityId, String previousState, String newState, Map<String, Object> metadata);

    void transition(UUID actorId, String action, String entityType, UUID entityId, Object from, Object to, String reason);

    Page<AuditLogResponse> search(UUID actorId, String entityType, UUID entityId, String action, Pageable pageable);

    Page<AuditLogResponse> byActor(UUID actorId, Pageable pageable);

    List<StatusHistoryResponse> history(String entityType, UUID entityId);

    static Map<String, Object> meta(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value != null) {
                map.put(String.valueOf(keyValues[i]), value instanceof BigDecimal b ? b.toPlainString() : value.toString());
            }
        }
        return map;
    }
}
