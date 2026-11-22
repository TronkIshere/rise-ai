package com.riseai.service.impl;

import com.riseai.client.AvalancheGatewayClient;
import com.riseai.client.AvalancheGatewayClient.LatestAnchor;
import com.riseai.client.AvalancheGatewayClient.Result;
import com.riseai.configuration.AvalancheEscrowProperties;
import com.riseai.entity.ChainAnchor;
import com.riseai.enums.ChainCommandStatus;
import com.riseai.repository.ChainAnchorRepository;
import com.riseai.service.AlertNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Anchors each append-only history on-chain when it has grown. Each anchor names the previous on-chain root, so the contract
 * refuses a forked history; a refused anchor is marked FAILED and the next pass starts again from the current chain state.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rise.escrow", name = "engine", havingValue = "avalanche")
public class ChainAnchorRelay {
    static final String ZERO_ROOT = "0x" + "0".repeat(64);
    private static final List<ChainCommandStatus> OPEN = ChainCommandStatus.OPEN;

    private final ChainAnchorRepository repository;
    private final AnchorRecords records;
    private final AvalancheGatewayClient gateway;
    private final AlertNotifier alertNotifier;
    private final AvalancheEscrowProperties properties;
    private final TransactionTemplate transactions;
    @Value("${rise.avalanche.read-only:false}")
    private boolean readOnly;

    public ChainAnchorRelay(ChainAnchorRepository repository, AnchorRecords records, AvalancheGatewayClient gateway, AlertNotifier alertNotifier,
                            AvalancheEscrowProperties properties, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.records = records;
        this.gateway = gateway;
        this.alertNotifier = alertNotifier;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void anchorOnce() {
        if (readOnly) return;
        for (String kind : AnchorRecords.KINDS) {
            try {
                anchor(kind);
            } catch (RuntimeException ex) {
                log.warn("Anchoring {} skipped this pass: {}", kind, ex.getMessage());
            }
        }
    }

    private void anchor(String kind) {
        Optional<ChainAnchor> open = repository.findFirstByKindAndStatusInOrderByCreatedAtDesc(kind, OPEN);
        if (open.isPresent()) {
            advance(open.get());
            return;
        }
        LatestAnchor latest = gateway.latestAnchor(kind);
        long count = records.countOlderThan(kind, properties.anchorLag());
        long anchored = latest.recordCount() == null ? 0 : latest.recordCount();
        if (count == 0 || count <= anchored) {
            return;
        }
        String root = records.root(kind, count);
        ChainAnchor created = transactions.execute(status -> {
            ChainAnchor anchor = new ChainAnchor();
            anchor.setKind(kind);
            anchor.setRecordCount(count);
            anchor.setRoot(root);
            anchor.setPreviousRoot(latest.root() == null ? ZERO_ROOT : latest.root());
            anchor.setStatus(ChainCommandStatus.PENDING);
            return repository.saveAndFlush(anchor);
        });
        advance(created);
    }

    private void advance(ChainAnchor anchor) {
        if (anchor.getStatus() == ChainCommandStatus.PENDING) {
            Result result = gateway.anchor(anchor.getKind(), anchor.getRoot(), anchor.getPreviousRoot(), anchor.getRecordCount(), anchor.operationKey());
            switch (result.outcome()) {
                case ACCEPTED -> update(anchor.getId(), a -> {
                    a.setStatus(ChainCommandStatus.SUBMITTED);
                    a.setTxHash(result.txHash());
                });
                case RETRY -> update(anchor.getId(), a -> a.setLastError(result.error()));
                default -> fail(anchor.getId(), result.error());
            }
            return;
        }
        Result result = gateway.transaction(anchor.operationKey());
        switch (result.outcome()) {
            case SUCCEEDED -> update(anchor.getId(), a -> {
                a.setStatus(ChainCommandStatus.CONFIRMED);
                a.setLastError(null);
            });
            case REVERTED, REJECTED -> fail(anchor.getId(), result.error());
            default -> {
            }
        }
    }

    private void fail(UUID id, String error) {
        transactions.executeWithoutResult(status -> {
            ChainAnchor anchor = repository.findById(id).orElseThrow();
            anchor.setStatus(ChainCommandStatus.FAILED);
            anchor.setLastError(error == null || error.length() <= 500 ? error : error.substring(0, 500));
            alertNotifier.alert("CHAIN_ANCHOR_FAILED", Map.of("anchorId", id.toString(), "kind", anchor.getKind(), "error", String.valueOf(anchor.getLastError())));
        });
    }

    private void update(UUID id, Consumer<ChainAnchor> change) {
        transactions.executeWithoutResult(status -> change.accept(repository.findById(id).orElseThrow()));
    }
}
