package com.riseai.avalanche.api;

import com.riseai.avalanche.api.Dtos.AddressValidation;
import com.riseai.avalanche.api.Dtos.BalanceResponse;
import com.riseai.avalanche.api.Dtos.CampaignResponse;
import com.riseai.avalanche.api.Dtos.CommandRequest;
import com.riseai.avalanche.api.Dtos.ConfigResponse;
import com.riseai.avalanche.api.Dtos.HashStatusResponse;
import com.riseai.avalanche.api.Dtos.MilestoneResponse;
import com.riseai.avalanche.api.Dtos.ReservationResponse;
import com.riseai.avalanche.api.Dtos.TransactionResponse;
import com.riseai.avalanche.contract.CampaignReader;
import com.riseai.avalanche.contract.DeploymentManifest;
import com.riseai.avalanche.contract.ManifestVerifier;
import com.riseai.avalanche.env.AvalancheEnvironment;
import com.riseai.avalanche.signer.SignerRole;
import com.riseai.avalanche.tx.TxRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The gateway API. Every response names its environment and says the money is simulated; POSTs need the internal key. */
@RestController
@RequestMapping("/api/v1/avalanche")
@ConditionalOnProperty(name = "avalanche.tx.enabled", havingValue = "true")
public class AvalancheController {

    private static final String FUJI_EXPLORER = "https://explorer-test.avax.network/c-chain";

    private final CampaignCommandService service;

    public AvalancheController(CampaignCommandService service) {
        this.service = service;
    }

    private String envName() {
        return service.environment().environment().name();
    }

    private long chainId() {
        return service.environment().chainId();
    }

    @GetMapping("/config")
    public ConfigResponse config() {
        DeploymentManifest m = service.deployment().manifest();
        return new ConfigResponse(envName(), true, chainId(), m.escrow(), m.token(), m.tokenKind(), m.anchor(), m.contractVersion(), service.reader().paused(),
                service.backendSignersAvailable(), service.signerAddress(SignerRole.OPERATOR), service.signerAddress(SignerRole.APPROVER),
                service.environment().environment() == AvalancheEnvironment.AVALANCHE_FUJI ? FUJI_EXPLORER : null);
    }

    @GetMapping("/addresses/validate")
    public AddressValidation validate(@RequestParam("address") String address) {
        return new AddressValidation(address, address != null && address.matches("0x[0-9a-fA-F]{40}"));
    }

    @GetMapping("/assets/balance")
    public BalanceResponse balance(@RequestParam("address") String address) {
        String owner = CampaignCommandService.requireAddress(address);
        String token = service.deployment().manifest().token();
        return new BalanceResponse(envName(), true, owner, token, service.reader().tokenBalance(token, owner).toString(), ManifestVerifier.TOKEN_DECIMALS);
    }

    @GetMapping("/campaigns/{campaignId}")
    public CampaignResponse campaign(@PathVariable("campaignId") String campaignId) {
        String id = CampaignCommandService.requireId(campaignId, "campaignId");
        CampaignReader.Campaign c = service.reader().campaign(id);
        if ("NONE".equals(c.status())) {
            throw new ApiException(404, "CAMPAIGN_NOT_FOUND", "no escrow for this campaign on this chain");
        }
        return new CampaignResponse(envName(), true, chainId(), id, c.status(), c.beneficiary(), c.releaseDelay(), c.confirmed().toString(), c.available().toString(),
                c.reserved().toString(), c.refundReserved().toString(), c.disbursed().toString(), c.refunded().toString(),
                c.adjustmentCredits().toString(), c.adjustmentDebits().toString());
    }

    @GetMapping("/campaigns/{campaignId}/milestones/{milestoneId}")
    public MilestoneResponse milestone(@PathVariable("campaignId") String campaignId, @PathVariable("milestoneId") String milestoneId) {
        String id = CampaignCommandService.requireId(campaignId, "campaignId");
        String milestone = CampaignCommandService.requireId(milestoneId, "milestoneId");
        CampaignReader.Milestone m = service.reader().milestone(id, milestone);
        return new MilestoneResponse(envName(), true, id, milestone, m.planned().toString(), m.reserved().toString(), m.released().toString(),
                m.openDisputes(), m.acceptedEvidence());
    }

    @GetMapping("/campaigns/{campaignId}/reservations/{requestId}")
    public ReservationResponse reservation(@PathVariable("campaignId") String campaignId, @PathVariable("requestId") String requestId) {
        String id = CampaignCommandService.requireId(campaignId, "campaignId");
        String request = CampaignCommandService.requireId(requestId, "requestId");
        CampaignReader.Reservation r = service.reader().reservation(id, request);
        return new ReservationResponse(envName(), true, id, request, r.status(), r.milestoneId(), r.amount().toString(), r.releaseAt());
    }

    @PostMapping("/campaigns/{campaignId}/actions/{action}/execute")
    public TransactionResponse execute(@PathVariable("campaignId") String campaignId, @PathVariable("action") String action, @RequestBody CommandRequest request) {
        return toResponse(service.execute(campaignId, action, request));
    }

    @GetMapping("/transactions/{operationKey}")
    public TransactionResponse transaction(@PathVariable("operationKey") String operationKey) {
        return service.transaction(operationKey).map(this::toResponse)
                .orElseThrow(() -> new ApiException(404, "TRANSACTION_NOT_FOUND", "no transaction with this operation key"));
    }

    @GetMapping("/transactions/by-hash/{txHash}")
    public HashStatusResponse byHash(@PathVariable("txHash") String txHash) {
        return service.statusByHash(txHash);
    }

    private TransactionResponse toResponse(TxRecord r) {
        return new TransactionResponse(envName(), true, chainId(), r.operationKey(), r.state().name(), r.txHash(), r.nonce(), r.blockNumber(),
                r.lastErrorClass(), r.signerRole());
    }
}
