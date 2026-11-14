package com.riseai.service.impl;

import com.riseai.client.AvalancheGatewayClient;
import com.riseai.client.AvalancheGatewayClient.Result;
import com.riseai.configuration.AvalancheEscrowProperties;
import com.riseai.entity.ChainCommand;
import com.riseai.entity.DisbursementRequest;
import com.riseai.enums.ChainCommandStatus;
import com.riseai.enums.DisbursementStatus;
import com.riseai.repository.ChainCommandRepository;
import com.riseai.repository.DisbursementRepository;
import com.riseai.service.AlertNotifier;
import com.riseai.service.DisbursementService;
import com.riseai.util.ChainValues;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Sends queued commands to the gateway, outside any business transaction. Commands of one campaign run strictly in order. An
 * execute-release waits out the objection window on its own, so a long window never holds up that campaign's other commands.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rise.escrow", name = "engine", havingValue = "avalanche")
public class ChainCommandRelay {
    private static final int MAX_ERROR = 500;

    private final ChainCommandRepository repository;
    private final DisbursementRepository disbursements;
    private final AvalancheGatewayClient gateway;
    private final ChainCommandWriter writer;
    private final DisbursementService disbursementService;
    private final AlertNotifier alertNotifier;
    private final AvalancheEscrowProperties properties;
    private final TransactionTemplate transactions;

    public ChainCommandRelay(ChainCommandRepository repository, DisbursementRepository disbursements, AvalancheGatewayClient gateway, ChainCommandWriter writer,
                             DisbursementService disbursementService, AlertNotifier alertNotifier, AvalancheEscrowProperties properties,
                             PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.disbursements = disbursements;
        this.gateway = gateway;
        this.writer = writer;
        this.disbursementService = disbursementService;
        this.alertNotifier = alertNotifier;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** @return how many commands were sent or checked in this pass */
    public int relayOnce() {
        Set<UUID> headSeen = new HashSet<>();
        Instant now = Instant.now();
        int handled = 0;
        List<String> open = repository.findOpenIdsInOrder(properties.batchSize());
        for (String id : open) {
            ChainCommand command = repository.findById(UUID.fromString(id)).orElse(null);
            if (command == null) {
                continue;
            }
            boolean inOrder = !ChainActions.EXECUTE_RELEASE.equals(command.getAction());
            if (inOrder && !headSeen.add(command.getCampaignId())) {
                continue;
            }
            if (!command.getNextAttemptAt().isAfter(now)) {
                handle(command);
                handled++;
            }
        }
        return handled;
    }

    private void handle(ChainCommand command) {
        try {
            if (command.getStatus() == ChainCommandStatus.SUBMITTED) {
                check(command);
            } else {
                send(command);
            }
        } catch (RuntimeException ex) {
            log.error("Chain command {} ({}) could not be processed; it will be retried", command.getId(), command.getAction(), ex);
        }
    }

    private void send(ChainCommand command) {
        if (ChainActions.EXECUTE_RELEASE.equals(command.getAction()) && !releaseStillWanted(command)) {
            return;
        }
        Result result = gateway.execute(ChainValues.bytes32(command.getCampaignId()), command.getAction(), command.getPayload(), command.operationKey());
        switch (result.outcome()) {
            case ACCEPTED -> update(command.getId(), c -> {
                c.setStatus(ChainCommandStatus.SUBMITTED);
                c.setTxHash(result.txHash());
                c.setLastError(null);
            });
            case RETRY -> retryLater(command.getId(), result.error());
            default -> failOrRearm(command, result);
        }
    }

    private void check(ChainCommand command) {
        Result result = gateway.transaction(command.operationKey());
        switch (result.outcome()) {
            case SUCCEEDED -> confirm(command.getId());
            case REVERTED, REJECTED -> failOrRearm(command, result);
            case RETRY -> retryLater(command.getId(), result.error());
            default -> update(command.getId(), c -> c.setNextAttemptAt(Instant.now()));
        }
    }

    /** A cancelled request no longer needs its release; a disputed one waits, because the dispute may be dismissed. */
    private boolean releaseStillWanted(ChainCommand command) {
        DisbursementRequest request = disbursements.findById(command.getReferenceId()).orElse(null);
        DisbursementStatus status = request == null ? null : request.getRequestStatus();
        if (status == DisbursementStatus.DISPUTED) {
            retryLater(command.getId(), "Waiting for the open dispute");
            return false;
        }
        if (status != DisbursementStatus.APPROVED) {
            update(command.getId(), c -> {
                c.setStatus(ChainCommandStatus.CANCELLED);
                c.setLastError("Request is no longer approved");
            });
            return false;
        }
        return true;
    }

    private void confirm(UUID id) {
        transactions.executeWithoutResult(status -> {
            ChainCommand command = repository.findById(id).orElseThrow();
            command.setStatus(ChainCommandStatus.CONFIRMED);
            command.setLastError(null);
            if (ChainActions.QUEUE_RELEASE.equals(command.getAction())) {
                writer.enqueue(command.getCampaignId(), ChainActions.EXECUTE_RELEASE, Map.of("requestId", ChainValues.bytes32(command.getReferenceId())),
                        command.getReferenceId(), command.getActorId(), Instant.now().plus(properties.releaseDelay()));
            } else if (ChainActions.EXECUTE_RELEASE.equals(command.getAction())) {
                disbursementService.completeChainRelease(command.getReferenceId(), command.getActorId());
                writer.enqueue(command.getCampaignId(), ChainActions.RECORD_PAYOUT, Map.of("requestId", ChainValues.bytes32(command.getReferenceId()),
                        "bankReferenceHash", ChainValues.simulatedPayoutReference(command.getReferenceId())), command.getReferenceId(), command.getActorId());
            }
        });
    }

    /** An execute-release reverts while the window is open or a dispute is open, which is expected, so it tries again later. */
    private void failOrRearm(ChainCommand command, Result result) {
        if (ChainActions.EXECUTE_RELEASE.equals(command.getAction())) {
            update(command.getId(), c -> {
                c.setStatus(ChainCommandStatus.PENDING);
                c.setAttempt(c.getAttempt() + 1);
                c.setTxHash(null);
                c.setNextAttemptAt(Instant.now().plus(properties.retryInterval()));
                c.setLastError(truncate(result.error()));
            });
            return;
        }
        transactions.executeWithoutResult(status -> {
            ChainCommand current = repository.findById(command.getId()).orElseThrow();
            current.setStatus(ChainCommandStatus.FAILED);
            current.setLastError(truncate(result.error()));
            alertNotifier.alert("CHAIN_COMMAND_FAILED", Map.of("commandId", current.getId().toString(), "action", current.getAction(),
                    "campaignId", current.getCampaignId().toString(), "error", String.valueOf(current.getLastError())));
        });
    }

    private void retryLater(UUID id, String error) {
        update(id, c -> {
            c.setNextAttemptAt(Instant.now().plus(properties.retryInterval()));
            c.setLastError(truncate(error));
        });
    }

    private void update(UUID id, Consumer<ChainCommand> change) {
        transactions.executeWithoutResult(status -> change.accept(repository.findById(id).orElseThrow()));
    }

    private static String truncate(String value) {
        return value != null && value.length() > MAX_ERROR ? value.substring(0, MAX_ERROR) : value;
    }
}
