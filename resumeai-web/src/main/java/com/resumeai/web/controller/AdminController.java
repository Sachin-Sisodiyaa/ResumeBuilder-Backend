package com.resumeai.web.controller;

import com.resumeai.web.model.WebOverview;
import com.resumeai.web.service.WebPortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-facing web endpoints for analytics, users, audit logs, and broadcast. */
@RestController
@RequestMapping("/api/v1/web/admin")
@RequiredArgsConstructor
public class AdminController {

    private final WebPortalService webPortalService;

    @GetMapping("/dashboard")
    public WebOverview dashboard() {
        return webPortalService.adminDashboard();
    }

    @GetMapping("/analytics/platform")
    public WebOverview platformAnalytics() {
        return webPortalService.platformAnalytics();
    }

    @GetMapping("/analytics/ai")
    public WebOverview aiAnalytics() {
        return webPortalService.aiAnalytics();
    }

    @PostMapping("/broadcast")
    public WebOverview broadcast(
            @RequestParam(value = "tier", required = false) String tier) {
        return webPortalService.broadcast(tier);
    }

    @GetMapping("/audit-logs")
    public WebOverview auditLogs(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "entityType", required = false) String entityType) {
        return webPortalService.auditLogs(action, entityType);
    }

    @GetMapping("/users")
    public WebOverview users(
            @RequestParam(value = "plan", required = false) String plan,
            @RequestParam(value = "activeOnly", required = false) Boolean activeOnly) {
        return webPortalService.adminUsers(plan, activeOnly);
    }
}
