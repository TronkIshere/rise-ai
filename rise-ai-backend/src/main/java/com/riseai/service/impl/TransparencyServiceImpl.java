package com.riseai.service.impl;

import com.riseai.client.AvalancheGatewayClient;
import com.riseai.client.AvalancheGatewayClient.LatestAnchor;
import com.riseai.configuration.AvalancheEscrowProperties;
import com.riseai.dto.response.TransparencyReport;
import com.riseai.entity.ChainAnchor;
import com.riseai.enums.ChainCommandStatus;
import com.riseai.repository.ChainAnchorRepository;
import com.riseai.service.TransparencyService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Recomputes each anchored root from the database and compares it with the chain. Works without any login. */
@Service
public class TransparencyServiceImpl implements TransparencyService {
    private final AvalancheGatewayClient gateway;
    private final AnchorRecords records;
    private final ChainAnchorRepository anchors;
    private final AvalancheEscrowProperties properties;

    public TransparencyServiceImpl(ObjectProvider<AvalancheGatewayClient> gateway, AnchorRecords records, ChainAnchorRepository anchors,
                                   AvalancheEscrowProperties properties) {
        this.gateway = gateway.getIfAvailable();
        this.records = records;
        this.anchors = anchors;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    @Override
    public TransparencyReport anchors() {
        if (gateway == null) {
            return new TransparencyReport(false, null, List.of());
        }
        List<TransparencyReport.Anchor> result = new ArrayList<>();
        String contract = null;
        for (String kind : AnchorRecords.KINDS) {
            long current = records.countAll(kind);
            LatestAnchor latest;
            try {
                latest = gateway.latestAnchor(kind);
            } catch (RuntimeException ex) {
                result.add(new TransparencyReport.Anchor(kind, "UNAVAILABLE", null, null, null, null, current, null, null));
                continue;
            }
            contract = latest.anchorContract();
            if (latest.count() == 0 || latest.root() == null) {
                result.add(new TransparencyReport.Anchor(kind, "NOT_ANCHORED", null, null, null, null, current, null, null));
                continue;
            }
            String recomputed = records.root(kind, latest.recordCount());
            boolean consistent = latest.root().equalsIgnoreCase(recomputed);
            String txHash = anchors.findFirstByKindAndRootAndStatus(kind, latest.root(), ChainCommandStatus.CONFIRMED).map(ChainAnchor::getTxHash).orElse(null);
            result.add(new TransparencyReport.Anchor(kind, consistent ? "CONSISTENT" : "MISMATCH", latest.root(), latest.recordCount(),
                    latest.anchoredAt() == null ? null : Instant.ofEpochSecond(latest.anchoredAt()), recomputed, current, txHash,
                    txHash == null ? null : properties.explorerTxUrl() + txHash));
        }
        return new TransparencyReport(true, contract, result);
    }
}
