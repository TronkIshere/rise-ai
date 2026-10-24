package com.riseai.admin;

import com.riseai.common.api.ResponseAPI;
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
    public ResponseAPI<AdminStatisticsService.SystemStatistics> statistics() {
        return ResponseAPI.ok(statisticsService.statistics());
    }
}
