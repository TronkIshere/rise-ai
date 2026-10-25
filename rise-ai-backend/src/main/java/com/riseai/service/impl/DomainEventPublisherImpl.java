package com.riseai.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.dto.event.DomainEvent;
import com.riseai.entity.OutboxEvent;
import com.riseai.enums.OutboxStatus;
import com.riseai.repository.OutboxEventRepository;
import com.riseai.service.DomainEventPublisher;
import com.riseai.util.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DomainEventPublisherImpl implements DomainEventPublisher {
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public OutboxEvent publish(DomainEvent event) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String correlationId = RequestContext.correlationId();

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", event.eventType());
        envelope.put("schemaVersion", DomainEvent.SCHEMA_VERSION);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("producer", DomainEvent.PRODUCER);
        envelope.put("aggregateType", event.aggregateType());
        envelope.put("aggregateId", event.aggregateId().toString());
        if (event.actorId() != null) {
            envelope.put("actorId", event.actorId().toString());
        }
        envelope.put("actorRole", event.actorRole() == null ? DomainEvent.SYSTEM_ROLE : event.actorRole());
        if (correlationId != null) {
            envelope.put("correlationId", correlationId);
        }
        envelope.put("references", event.references());
        envelope.put("payload", event.payload());

        OutboxEvent outbox = new OutboxEvent();
        outbox.setEventId(eventId);
        outbox.setEventType(event.eventType());
        outbox.setSchemaVersion(DomainEvent.SCHEMA_VERSION);
        outbox.setAggregateType(event.aggregateType());
        outbox.setAggregateId(event.aggregateId());
        outbox.setActorId(event.actorId());
        outbox.setCorrelationId(correlationId);
        outbox.setEnvelope(serialize(envelope));
        outbox.setOccurredAt(occurredAt);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(occurredAt);
        return outboxEventRepository.save(outbox);
    }

    private String serialize(Map<String, Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Domain event could not be serialized", ex);
        }
    }
}
