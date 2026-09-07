package com.erp.demo.overview;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/dashboard")
public class OverviewController {
    private final OverviewService overviewService;
    public OverviewController(OverviewService overviewService) { this.overviewService = overviewService; }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('report:view')")
    public OverviewResponse overview(@RequestParam(required = false) String range,
                                     @RequestParam(required = false) Integer riskLimit,
                                     @RequestParam(required = false) Integer activityLimit) {
        return overviewService.overview(range, riskLimit, activityLimit);
    }
}
