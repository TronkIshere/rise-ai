package com.riseai.avalanche.api;

import com.riseai.avalanche.api.Dtos.CommandRequest;
import com.riseai.avalanche.chain.ChainClient;
import com.riseai.avalanche.chain.TxReceipt;
import com.riseai.avalanche.config.TxProperties;
import com.riseai.avalanche.contract.CampaignCalls;
import com.riseai.avalanche.contract.CampaignReader;
import com.riseai.avalanche.contract.DeploymentHolder;
import com.riseai.avalanche.contract.VerifiedDeployment;
import com.riseai.avalanche.env.AvalancheEnvironment;
import com.riseai.avalanche.signer.SignerRegistry;
import com.riseai.avalanche.signer.SignerRole;
import com.riseai.avalanche.startup.ValidatedEnvironment;
import com.riseai.avalanche.tx.SubmissionService;
import com.riseai.avalanche.tx.SubmissionService.SubmitRequest;
import com.riseai.avalanche.tx.TxRecord;
import com.riseai.avalanche.tx.TxRepository;
import com.riseai.avalanche.tx.TxState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Turns one backend command into one CampaignEscrow transaction. The signer role is decided here from the action, never by the
 * caller, and money-moving actions always use the approver. Business authorization stays in the backend; the contract enforces
 * balances, milestone caps, the objection window and open disputes. Executes only where signers exist (LOCAL_SIMULATION).
 */
@Service
@ConditionalOnProperty(name = "avalanche.tx.enabled", havingValue = "true")
public class CampaignCommandService {

    private static final Pattern BYTES32 = Pattern.compile("0x[0-9a-fA-F]{64}");
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");
    private static final long GAS = 500_000;
    private static final long OPEN_BASE_GAS = 200_000;
    private static final long OPEN_GAS_PER_MILESTONE = 70_000;

    private record Call(SignerRole role, String data, long gas) {
    }

    private final ChainClient client;
    private final ValidatedEnvironment environment;
    private final DeploymentHolder deployment;
    private final SignerRegistry registry;
    private final SubmissionService submission;
    private final TxRepository repository;
    private final TxProperties txProperties;

    public CampaignCommandService(ChainClient client, ValidatedEnvironment environment, DeploymentHolder deployment,
                                  ObjectProvider<SignerRegistry> registry, ObjectProvider<SubmissionService> submission, TxRepository repository,
                                  TxProperties txProperties) {
        this.client = client;
        this.environment = environment;
        this.deployment = deployment;
        this.registry = registry.getIfAvailable();
        this.submission = submission.getIfAvailable();
        this.repository = repository;
        this.txProperties = txProperties;
    }

    public VerifiedDeployment deployment() {
        if (deployment.deployment() == null) {
            throw new ApiException(503, "DEPLOYMENT_NOT_CONFIGURED", "no verified deployment manifest is configured");
        }
        return deployment.deployment();
    }

    public ValidatedEnvironment environment() {
        return environment;
    }

    public boolean backendSignersAvailable() {
        return registry != null;
    }

    public String signerAddress(SignerRole role) {
        return registry == null ? null : registry.get(role).address();
    }

    public CampaignReader reader() {
        return new CampaignReader(client, deployment().manifest().escrow());
    }

    public static String requireAddress(String value) {
        if (value == null || !ADDRESS.matcher(value).matches()) {
            throw new ApiException(400, "INVALID_ADDRESS", "a 20-byte hex address is required");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    public static String requireId(String value, String field) {
        if (value == null || !BYTES32.matcher(value).matches()) {
            throw new ApiException(400, "INVALID_REQUEST", field + " must be a 32-byte hex value");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new ApiException(400, "INVALID_REQUEST", field + " is required");
        }
        return value;
    }

    private static BigInteger amount(String value, String field) {
        try {
            BigInteger parsed = new BigInteger(required(value, field));
            if (parsed.signum() <= 0) {
                throw new ApiException(400, "INVALID_REQUEST", field + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new ApiException(400, "INVALID_REQUEST", field + " must be a whole number of base units");
        }
    }

    public TxRecord execute(String rawCampaignId, String action, CommandRequest r) {
        if (submission == null || registry == null) {
            throw new ApiException(409, "SIGNER_UNAVAILABLE", "no backend signer exists in " + environment.environment());
        }
        String campaignId = requireId(rawCampaignId, "campaignId");
        String operationKey = required(r.operationKey(), "operationKey");
        Call call = build(campaignId, action, r);
        return submission.submit(new SubmitRequest(operationKey, call.role(), deployment().manifest().escrow(), call.data(), call.gas()));
    }

    private Call build(String id, String action, CommandRequest r) {
        return switch (action) {
            case "open" -> {
                List<String> milestones = required(r.milestoneIds(), "milestoneIds").stream().map(m -> requireId(m, "milestoneIds")).toList();
                List<BigInteger> planned = required(r.plannedAmounts(), "plannedAmounts").stream().map(a -> amount(a, "plannedAmounts")).toList();
                long gas = OPEN_BASE_GAS + OPEN_GAS_PER_MILESTONE * milestones.size();
                yield new Call(SignerRole.OPERATOR, CampaignCalls.openCampaign(id, requireAddress(r.beneficiary()), amount(r.target(), "target"),
                        required(r.releaseDelay(), "releaseDelay"), milestones, planned), gas);
            }
            case "status" -> operator(CampaignCalls.changeStatus(id, statusCode(r.status())));
            case "contribution" -> operator(CampaignCalls.recordContribution(id, requireId(r.donationId(), "donationId"), amount(r.amount(), "amount")));
            case "anchor-evidence" -> operator(CampaignCalls.anchorEvidence(id, requireId(r.milestoneId(), "milestoneId"),
                    requireId(r.evidenceId(), "evidenceId"), requireId(r.contentHash(), "contentHash")));
            case "decide-evidence" -> operator(CampaignCalls.decideEvidence(id, requireId(r.evidenceId(), "evidenceId"), required(r.accepted(), "accepted")));
            case "open-dispute" -> operator(CampaignCalls.openDispute(id, requireId(r.milestoneId(), "milestoneId"), requireId(r.disputeId(), "disputeId")));
            case "close-dispute" -> operator(CampaignCalls.closeDispute(id, requireId(r.disputeId(), "disputeId")));
            case "reserve" -> operator(CampaignCalls.reserve(id, requireId(r.milestoneId(), "milestoneId"), requireId(r.requestId(), "requestId"), amount(r.amount(), "amount")));
            case "cancel-reservation" -> operator(CampaignCalls.cancelReservation(id, requireId(r.requestId(), "requestId")));
            case "reserve-refund" -> operator(CampaignCalls.reserveRefund(id, requireId(r.donationId(), "donationId"), requireId(r.refundId(), "refundId"), amount(r.amount(), "amount")));
            case "release-refund-reservation" -> operator(CampaignCalls.releaseRefundReservation(id, requireId(r.refundId(), "refundId")));
            case "queue-release" -> approver(CampaignCalls.queueRelease(id, requireId(r.requestId(), "requestId")));
            case "record-refund" -> approver(CampaignCalls.recordRefund(id, requireId(r.refundId(), "refundId")));
            case "settlement-refund" -> approver(CampaignCalls.recordSettlementRefund(id, requireId(r.settlementId(), "settlementId"), requireId(r.donationId(), "donationId"), amount(r.amount(), "amount")));
            case "adjustment" -> approver(CampaignCalls.recordAdjustment(id, requireId(r.adjustmentId(), "adjustmentId"), required(r.credit(), "credit"), amount(r.amount(), "amount")));
            case "execute-release" -> new Call(SignerRole.RELAYER, CampaignCalls.executeRelease(id, requireId(r.requestId(), "requestId")), GAS);
            default -> throw new ApiException(400, "UNKNOWN_ACTION", "unknown action " + action);
        };
    }

    private static Call operator(String data) {
        return new Call(SignerRole.OPERATOR, data, GAS);
    }

    private static Call approver(String data) {
        return new Call(SignerRole.APPROVER, data, GAS);
    }

    private static int statusCode(String status) {
        int code = CampaignReader.ESCROW_STATUS.indexOf(required(status, "status"));
        if (code < 1) {
            throw new ApiException(400, "INVALID_REQUEST", "status must be ACTIVE, FROZEN or SETTLEMENT");
        }
        return code;
    }

    /** The anchored record sets. The contract hashes nothing; the name only selects which history a root belongs to. */
    public static final List<String> ANCHOR_KINDS = List.of("LEDGER", "AUDIT", "FINDINGS");

    public static String anchorKind(String kind) {
        if (kind == null || !ANCHOR_KINDS.contains(kind)) {
            throw new ApiException(400, "INVALID_ANCHOR_KIND", "kind must be one of " + ANCHOR_KINDS);
        }
        return org.web3j.crypto.Hash.sha3String(kind);
    }

    public TxRecord anchor(String kind, Dtos.AnchorRequest r) {
        if (submission == null || registry == null) {
            throw new ApiException(409, "SIGNER_UNAVAILABLE", "no backend signer exists in " + environment.environment());
        }
        String kindId = anchorKind(kind);
        String root = requireId(r.root(), "root");
        String previous = requireId(r.previousRoot(), "previousRoot");
        long count = required(r.recordCount(), "recordCount");
        if (count <= 0) {
            throw new ApiException(400, "INVALID_REQUEST", "recordCount must be positive");
        }
        return submission.submit(new SubmitRequest(required(r.operationKey(), "operationKey"), SignerRole.ANCHORER, deployment().manifest().anchor(),
                CampaignCalls.anchor(kindId, root, previous, count), GAS));
    }

    public CampaignReader.Anchor latestAnchor(String kind) {
        return reader().latestAnchor(deployment().manifest().anchor(), anchorKind(kind));
    }

    public Optional<TxRecord> transaction(String operationKey) {
        return repository.find(environment.environment().name(), environment.chainId(), operationKey);
    }

    /** Status of any transaction by hash, derived live from the chain. A receipt counts only when it targets a manifest contract. */
    public Dtos.HashStatusResponse statusByHash(String txHash) {
        if (txHash == null || !BYTES32.matcher(txHash).matches()) {
            throw new ApiException(400, "INVALID_TX_HASH", "a 32-byte transaction hash is required");
        }
        Optional<TxReceipt> receipt = client.receipt(txHash);
        String state;
        Long block = null;
        if (receipt.isPresent() && !isManifestTarget(receipt.get().to())) {
            state = TxState.UNKNOWN.name();
        } else if (receipt.isPresent()) {
            block = receipt.get().blockNumber();
            if (!receipt.get().success()) {
                state = TxState.REVERTED.name();
            } else {
                boolean finalized = environment.environment() == AvalancheEnvironment.LOCAL_SIMULATION
                        ? client.latestBlockNumber() >= block + txProperties.confirmations()
                        : client.finalizedBlockNumber() >= block;
                state = (finalized ? TxState.FINALIZED : TxState.CONFIRMED).name();
            }
        } else {
            state = client.transactionExists(txHash) ? TxState.PENDING.name() : TxState.UNKNOWN.name();
        }
        return new Dtos.HashStatusResponse(environment.environment().name(), true, environment.chainId(), txHash.toLowerCase(Locale.ROOT), state, block);
    }

    private boolean isManifestTarget(String to) {
        return to != null && deployment().allowedTargets().stream().anyMatch(to::equalsIgnoreCase);
    }
}
