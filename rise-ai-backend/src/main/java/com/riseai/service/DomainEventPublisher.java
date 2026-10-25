package com.riseai.service;

import com.riseai.dto.event.DomainEvent;
import com.riseai.entity.OutboxEvent;

public interface DomainEventPublisher {
    OutboxEvent publish(DomainEvent event);
}
