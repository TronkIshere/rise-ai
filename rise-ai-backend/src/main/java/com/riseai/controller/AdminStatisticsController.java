package com.riseai.controller;

import com.riseai.dto.response.ResponseAPI;
import com.riseai.dto.response.SystemStatistics;
import com.riseai.service.AdminStatisticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/statistics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Statistics")
public class AdminStatisticsController {
    private final AdminStatisticsService statisticsService;

    @GetMapping
    public ResponseAPI<SystemStatistics> statistics() {
        return ResponseAPI.ok(statisticsService.statistics());
    }
}
