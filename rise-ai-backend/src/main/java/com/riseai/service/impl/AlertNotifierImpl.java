package com.riseai.service.impl;

import com.riseai.entity.AlertNotification;
import com.riseai.enums.AlertStatus;
import com.riseai.repository.AlertNotificationRepository;
import com.riseai.service.AlertNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotifierImpl implements AlertNotifier {
    private final AlertNotificationRepository repository;

    @Override
    @Transactional
    public void alert(String kind, Map<String, String> details) {
        log.error("ALERT {} {}", kind, details);
        AlertNotification notification = new AlertNotification();
        notification.setKind(kind);
        notification.setDetails(new LinkedHashMap<>(details));
        notification.setStatus(AlertStatus.PENDING);
        notification.setNextAttemptAt(Instant.now());
        repository.save(notification);
    }
}
