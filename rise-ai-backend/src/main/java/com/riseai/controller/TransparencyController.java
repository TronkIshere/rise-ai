package com.riseai.controller;

import com.riseai.dto.response.ResponseAPI;
import com.riseai.dto.response.TransparencyReport;
import com.riseai.service.TransparencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transparency")
@RequiredArgsConstructor
@Tag(name = "Transparency (on-chain anchors)")
public class TransparencyController {
    private final TransparencyService transparencyService;

    @GetMapping("/anchors")
    @Operation(summary = "Compare the stored ledger, audit log and monitoring occurrences with their on-chain anchors")
    public ResponseAPI<TransparencyReport> anchors() {
        return ResponseAPI.ok(transparencyService.anchors());
    }
}
