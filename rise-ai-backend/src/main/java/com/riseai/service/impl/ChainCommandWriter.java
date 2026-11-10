package com.riseai.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.entity.ChainCommand;
import com.riseai.enums.ChainCommandStatus;
import com.riseai.repository.ChainCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Stores a chain command in the caller's business transaction: a rolled-back action leaves no command. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rise.escrow", name = "engine", havingValue = "avalanche")
@Transactional(propagation = Propagation.MANDATORY)
public class ChainCommandWriter {
    private final ChainCommandRepository repository;
    private final ObjectMapper objectMapper;

    public ChainCommand enqueue(UUID campaignId, String action, Map<String, ?> fields, UUID referenceId, UUID actorId, Instant notBefore) {
        ChainCommand command = new ChainCommand();
        command.setCampaignId(campaignId);
        command.setAction(action);
        try {
            command.setPayload(objectMapper.writeValueAsString(fields));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Chain command could not be serialized", ex);
        }
        command.setStatus(ChainCommandStatus.PENDING);
        command.setNextAttemptAt(notBefore == null ? Instant.now() : notBefore);
        command.setReferenceId(referenceId);
        command.setActorId(actorId);
        return repository.saveAndFlush(command);
    }

    public ChainCommand enqueue(UUID campaignId, String action, Map<String, ?> fields, UUID referenceId, UUID actorId) {
        return enqueue(campaignId, action, fields, referenceId, actorId, null);
    }

    public void dropPending(UUID referenceId, String action) {
        repository.deleteByReferenceIdAndActionAndStatus(referenceId, action, ChainCommandStatus.PENDING);
    }
}
