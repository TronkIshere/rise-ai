package com.riseai.controller;

import com.riseai.dto.response.CampaignChainResponse;
import com.riseai.dto.response.ChainActivityResponse;
import com.riseai.dto.response.ChainConfigResponse;
import com.riseai.dto.response.ReservesResponse;
import com.riseai.dto.response.ResponseAPI;
import com.riseai.dto.response.TransparencyReport;
import com.riseai.security.AuthUser;
import com.riseai.service.ChainActivityService;
import com.riseai.service.TransparencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transparency")
@RequiredArgsConstructor
@Tag(name = "Transparency (on-chain anchors)")
public class TransparencyController {
    private final TransparencyService transparencyService;
    private final ChainActivityService chainActivityService;

    @GetMapping("/anchors")
    @Operation(summary = "Compare the stored ledger, audit log and monitoring occurrences with their on-chain anchors")
    public ResponseAPI<TransparencyReport> anchors() {
        return ResponseAPI.ok(transparencyService.anchors());
    }

    @GetMapping("/chain")
    @Operation(summary = "The chain, contract addresses and explorer the escrow mirror uses (onChain=false with the simulated engine)")
    public ResponseAPI<ChainConfigResponse> chain() {
        return ResponseAPI.ok(transparencyService.chainConfig());
    }

    @GetMapping("/activity")
    @Operation(summary = "The escrow's transactions as indexed by the Avalanche Data API, checked against the platform's own records")
    public ResponseAPI<ChainActivityResponse> activity(@RequestParam(required = false) String pageToken) {
        return ResponseAPI.ok(chainActivityService.activity(pageToken));
    }

    @GetMapping("/reserves")
    @Operation(summary = "Proof of reserves: the independent guardian's latest bank-balance attestation next to what the escrow holds (VND, simulated)")
    public ResponseAPI<ReservesResponse> reserves() {
        return ResponseAPI.ok(transparencyService.reserves());
    }

    @GetMapping("/campaigns/{campaignId}")
    @Operation(summary = "A campaign's on-chain transactions and the chain status of each disbursement request")
    public ResponseAPI<CampaignChainResponse> campaign(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID campaignId) {
        return ResponseAPI.ok(transparencyService.campaignChain(actor, campaignId));
    }
}
