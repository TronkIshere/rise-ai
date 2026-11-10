package com.riseai.service.impl;

import com.riseai.client.AvalancheGatewayClient;
import com.riseai.client.AvalancheGatewayClient.DeploymentInfo;
import com.riseai.client.AvalancheGatewayClient.LatestAnchor;
import com.riseai.configuration.AvalancheEscrowProperties;
import com.riseai.dto.response.CampaignChainResponse;
import com.riseai.dto.response.ChainConfigResponse;
import com.riseai.dto.response.TransparencyReport;
import com.riseai.entity.ChainAnchor;
import com.riseai.entity.ChainCommand;
import com.riseai.entity.DisbursementRequest;
import com.riseai.enums.ChainCommandStatus;
import com.riseai.enums.DisbursementStatus;
import com.riseai.repository.ChainAnchorRepository;
import com.riseai.repository.ChainCommandRepository;
import com.riseai.repository.DisbursementRepository;
import com.riseai.security.AuthUser;
import com.riseai.service.CampaignService;
import com.riseai.service.TransparencyService;
import com.riseai.util.ChainValues;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Recomputes each anchored root from the database and compares it with the chain, and shows each campaign's mirror. Works without any login. */
@Service
public class TransparencyServiceImpl implements TransparencyService {
    private static final Duration CONFIG_CACHE = Duration.ofMinutes(1);
    private static final Set<String> RELEASE_FLOW = Set.of(ChainActions.RESERVE, ChainActions.CANCEL_RESERVATION, ChainActions.QUEUE_RELEASE,
            ChainActions.EXECUTE_RELEASE);

    private final AvalancheGatewayClient gateway;
    private final AnchorRecords records;
    private final ChainAnchorRepository anchors;
    private final ChainCommandRepository commands;
    private final DisbursementRepository disbursements;
    private final CampaignService campaignService;
    private final AvalancheEscrowProperties properties;
    private volatile CachedDeployment cachedDeployment;

    private record CachedDeployment(DeploymentInfo info, Instant fetchedAt) {
    }

    public TransparencyServiceImpl(ObjectProvider<AvalancheGatewayClient> gateway, AnchorRecords records, ChainAnchorRepository anchors,
                                   ChainCommandRepository commands, DisbursementRepository disbursements, CampaignService campaignService,
                                   AvalancheEscrowProperties properties) {
        this.gateway = gateway.getIfAvailable();
        this.records = records;
        this.anchors = anchors;
        this.commands = commands;
        this.disbursements = disbursements;
        this.campaignService = campaignService;
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
                    latest.anchoredAt() == null ? null : Instant.ofEpochSecond(latest.anchoredAt()), recomputed, current, txHash, explorerTx(txHash)));
        }
        return new TransparencyReport(true, contract, result);
    }

    @Override
    public ChainConfigResponse chainConfig() {
        if (gateway == null) {
            return ChainConfigResponse.offChain();
        }
        String addressUrl = properties.explorerTxUrl().replaceFirst("/tx/?$", "/address/");
        DeploymentInfo info = deployment();
        if (info == null) {
            return new ChainConfigResponse(true, false, null, null, null, null, null, null, null, null, properties.beneficiaryAddress(),
                    properties.explorerTxUrl(), addressUrl);
        }
        return new ChainConfigResponse(true, true, info.environment(), info.chainId(), info.escrow(), info.token(), info.tokenKind(), info.anchor(),
                info.contractVersion(), info.paused(), properties.beneficiaryAddress(), properties.explorerTxUrl(), addressUrl);
    }

    private DeploymentInfo deployment() {
        CachedDeployment cached = cachedDeployment;
        if (cached != null && cached.fetchedAt().plus(CONFIG_CACHE).isAfter(Instant.now())) {
            return cached.info();
        }
        try {
            DeploymentInfo info = gateway.deployment();
            cachedDeployment = new CachedDeployment(info, Instant.now());
            return info;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    @Override
    public CampaignChainResponse campaignChain(AuthUser actor, UUID campaignId) {
        campaignService.loadVisible(campaignId, actor);
        if (gateway == null) {
            return new CampaignChainResponse(false, campaignId, null, "NOT_ON_CHAIN", null, List.of(), List.of());
        }
        List<ChainCommand> all = commands.findByCampaignInOrder(campaignId.toString());
        String openStatus = "NOT_ON_CHAIN";
        List<CampaignChainResponse.Transaction> transactions = new ArrayList<>();
        Map<UUID, Map<String, ChainCommand>> releaseFlow = new HashMap<>();
        for (ChainCommand command : all) {
            if (ChainActions.OPEN.equals(command.getAction())) {
                openStatus = command.getStatus().name();
            }
            if (RELEASE_FLOW.contains(command.getAction()) && command.getReferenceId() != null) {
                releaseFlow.computeIfAbsent(command.getReferenceId(), id -> new HashMap<>()).put(command.getAction(), command);
            }
            if (command.getStatus() != ChainCommandStatus.CANCELLED) {
                String txHash = command.getStatus() == ChainCommandStatus.PENDING ? null : command.getTxHash();
                transactions.add(new CampaignChainResponse.Transaction(command.getAction(), command.getReferenceId(), command.getStatus().name(), txHash,
                        explorerTx(txHash), command.getUpdatedAt()));
            }
        }
        List<CampaignChainResponse.Disbursement> result = new ArrayList<>();
        for (DisbursementRequest request : disbursements.findByCampaignIdOrderByCreatedAtAsc(campaignId)) {
            Map<String, ChainCommand> flow = releaseFlow.getOrDefault(request.getId(), Map.of());
            String chainStatus = chainStatus(flow, request.getRequestStatus());
            Instant releaseAt = "IN_OBJECTION_WINDOW".equals(chainStatus) || "BLOCKED_BY_DISPUTE".equals(chainStatus) ? releaseAt(campaignId, request.getId()) : null;
            result.add(new CampaignChainResponse.Disbursement(request.getId(), request.getRequestCode(), request.getRequestStatus(), chainStatus, releaseAt,
                    confirmedHash(flow.get(ChainActions.RESERVE)), confirmedHash(flow.get(ChainActions.QUEUE_RELEASE)),
                    confirmedHash(flow.get(ChainActions.EXECUTE_RELEASE))));
        }
        return new CampaignChainResponse(true, campaignId, ChainValues.bytes32(campaignId), openStatus, properties.explorerTxUrl(), result, transactions);
    }

    private static String chainStatus(Map<String, ChainCommand> flow, DisbursementStatus requestStatus) {
        ChainCommand reserve = flow.get(ChainActions.RESERVE);
        ChainCommand cancel = flow.get(ChainActions.CANCEL_RESERVATION);
        ChainCommand queue = flow.get(ChainActions.QUEUE_RELEASE);
        ChainCommand execute = flow.get(ChainActions.EXECUTE_RELEASE);
        if (is(execute, ChainCommandStatus.CONFIRMED)) {
            return "RELEASED_ON_CHAIN";
        }
        if (is(cancel, ChainCommandStatus.CONFIRMED)) {
            return "CANCELLED_ON_CHAIN";
        }
        if (is(reserve, ChainCommandStatus.FAILED) || is(queue, ChainCommandStatus.FAILED) || is(cancel, ChainCommandStatus.FAILED)) {
            return "FAILED";
        }
        if (open(cancel)) {
            return "CANCELLING";
        }
        if (open(execute)) {
            return requestStatus == DisbursementStatus.DISPUTED ? "BLOCKED_BY_DISPUTE" : "IN_OBJECTION_WINDOW";
        }
        if (open(queue)) {
            return "QUEUING";
        }
        if (is(reserve, ChainCommandStatus.CONFIRMED)) {
            return "RESERVED_ON_CHAIN";
        }
        return open(reserve) ? "RESERVING" : "NOT_ON_CHAIN";
    }

    private static boolean is(ChainCommand command, ChainCommandStatus status) {
        return command != null && command.getStatus() == status;
    }

    private static boolean open(ChainCommand command) {
        return command != null && ChainCommandStatus.OPEN.contains(command.getStatus());
    }

    private static String confirmedHash(ChainCommand command) {
        return is(command, ChainCommandStatus.CONFIRMED) ? command.getTxHash() : null;
    }

    private Instant releaseAt(UUID campaignId, UUID requestId) {
        try {
            Long seconds = gateway.releaseAt(ChainValues.bytes32(campaignId), ChainValues.bytes32(requestId));
            return seconds == null ? null : Instant.ofEpochSecond(seconds);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String explorerTx(String txHash) {
        return txHash == null ? null : properties.explorerTxUrl() + txHash;
    }
}
