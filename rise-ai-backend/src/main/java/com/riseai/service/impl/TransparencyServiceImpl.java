package com.riseai.service.impl;

import com.riseai.client.AvalancheGatewayClient;
import com.riseai.client.AvalancheGatewayClient.DeploymentInfo;
import com.riseai.client.AvalancheGatewayClient.LatestAnchor;
import com.riseai.configuration.AvalancheEscrowProperties;
import com.riseai.dto.response.CampaignChainResponse;
import com.riseai.dto.response.ChainConfigResponse;
import com.riseai.dto.response.ReservesResponse;
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

import java.math.BigDecimal;
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
    private static final Set<String> RELEASE_FLOW = Set.of(ChainActions.RESERVE, ChainActions.CANCEL_RESERVATION, ChainActions.QUEUE_RELEASE,
            ChainActions.EXECUTE_RELEASE, ChainActions.RECORD_PAYOUT);
    private static final Set<String> HOLDABLE = Set.of("QUEUING", "IN_OBJECTION_WINDOW", "RESERVED_ON_CHAIN");

    private final AvalancheGatewayClient gateway;
    private final AnchorRecords records;
    private final ChainAnchorRepository anchors;
    private final ChainCommandRepository commands;
    private final DisbursementRepository disbursements;
    private final CampaignService campaignService;
    private final AvalancheEscrowProperties properties;
    private final GatewayDeploymentCache deployments;

    public TransparencyServiceImpl(ObjectProvider<AvalancheGatewayClient> gateway, AnchorRecords records, ChainAnchorRepository anchors,
                                   ChainCommandRepository commands, DisbursementRepository disbursements, CampaignService campaignService,
                                   AvalancheEscrowProperties properties, GatewayDeploymentCache deployments) {
        this.gateway = gateway.getIfAvailable();
        this.records = records;
        this.anchors = anchors;
        this.commands = commands;
        this.disbursements = disbursements;
        this.campaignService = campaignService;
        this.properties = properties;
        this.deployments = deployments;
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
        DeploymentInfo info = deployments.get();
        if (info == null) {
            return new ChainConfigResponse(true, false, null, null, null, null, null, null, null, null, properties.beneficiaryAddress(),
                    properties.explorerTxUrl(), addressUrl, null, null);
        }
        return new ChainConfigResponse(true, true, info.environment(), info.chainId(), info.escrow(), info.token(), info.tokenKind(), info.anchor(),
                info.contractVersion(), info.paused(), properties.beneficiaryAddress(), properties.explorerTxUrl(), addressUrl, info.guardian(),
                info.guardianHoldMaxSeconds());
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
        Map<UUID, Integer> holdsByMilestone = new HashMap<>();
        for (DisbursementRequest request : disbursements.findByCampaignIdOrderByCreatedAtAsc(campaignId)) {
            Map<String, ChainCommand> flow = releaseFlow.getOrDefault(request.getId(), Map.of());
            String chainStatus = chainStatus(flow, request.getRequestStatus());
            Integer holds = null;
            if (HOLDABLE.contains(chainStatus)) {
                holds = holdsByMilestone.computeIfAbsent(request.getMilestoneId(), milestone -> openHolds(campaignId, milestone));
                if (holds != null && holds > 0 && !"RESERVED_ON_CHAIN".equals(chainStatus)) {
                    chainStatus = "HELD_BY_GUARDIAN";
                }
            }
            Instant releaseAt = Set.of("IN_OBJECTION_WINDOW", "BLOCKED_BY_DISPUTE", "HELD_BY_GUARDIAN").contains(chainStatus)
                    ? releaseAt(campaignId, request.getId()) : null;
            String payoutTx = confirmedHash(flow.get(ChainActions.RECORD_PAYOUT));
            Instant receiptConfirmedAt = payoutTx == null ? null : receiptConfirmedAt(campaignId, request.getId());
            result.add(new CampaignChainResponse.Disbursement(request.getId(), request.getRequestCode(), request.getRequestStatus(), chainStatus, releaseAt,
                    confirmedHash(flow.get(ChainActions.RESERVE)), confirmedHash(flow.get(ChainActions.QUEUE_RELEASE)),
                    confirmedHash(flow.get(ChainActions.EXECUTE_RELEASE)), holds, payoutTx, receiptConfirmedAt));
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

    @Override
    public ReservesResponse reserves() {
        if (gateway == null) {
            return ReservesResponse.offChain();
        }
        AvalancheGatewayClient.Reserves r;
        try {
            r = gateway.reserves();
        } catch (RuntimeException ex) {
            return ReservesResponse.unavailable();
        }
        BigDecimal held = vnd(r.currentHeld());
        if (!r.attested()) {
            return new ReservesResponse(true, true, false, null, null, null, null, null, null, held, null);
        }
        return new ReservesResponse(true, true, true, r.period(), r.attestedAt() == null ? null : Instant.ofEpochSecond(r.attestedAt()), r.attester(),
                vnd(r.bankBalance()), vnd(r.heldAtAttestation()), vnd(r.gap()), held, r.statementHash());
    }

    /** rVND has the ledger's two decimals, so base units are hundredths of a VND. */
    private static BigDecimal vnd(String baseUnits) {
        return baseUnits == null ? null : new BigDecimal(baseUnits).movePointLeft(2);
    }

    private Integer openHolds(UUID campaignId, UUID milestoneId) {
        try {
            return gateway.openHolds(ChainValues.bytes32(campaignId), ChainValues.bytes32(milestoneId));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Instant receiptConfirmedAt(UUID campaignId, UUID requestId) {
        try {
            Long confirmedAt = gateway.payout(ChainValues.bytes32(campaignId), ChainValues.bytes32(requestId)).confirmedAt();
            return confirmedAt == null ? null : Instant.ofEpochSecond(confirmedAt);
        } catch (RuntimeException ex) {
            return null;
        }
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
