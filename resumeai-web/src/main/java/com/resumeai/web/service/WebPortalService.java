package com.resumeai.web.service;

import com.resumeai.web.model.WebOverview;

/**
 * Web portal service interface — all methods return {@link WebOverview} payloads
 * that the frontend/MVC layer uses to discover upstream service URLs and
 * render views.
 */
public interface WebPortalService {

    // ── Public / Guest ──────────────────────────────────────────────────────
    WebOverview home();
    WebOverview templates(String category, String plan);
    WebOverview gallery(String jobTitle, String templateCategory);

    // ── Authenticated User ───────────────────────────────────────────────────
    WebOverview dashboard(Long userId);
    WebOverview notifications(Long userId);
    WebOverview unreadNotificationCount(Long userId);
    WebOverview builder(Long resumeId);
    WebOverview preview(Long resumeId);
    WebOverview aiQuota(Long userId);
    WebOverview aiHistory(Long userId);

    // ── Admin ────────────────────────────────────────────────────────────────
    WebOverview adminDashboard();
    WebOverview platformAnalytics();
    WebOverview aiAnalytics();
    WebOverview broadcast();
    WebOverview broadcast(String tier);
    WebOverview auditLogs(String action, String entityType);
    WebOverview adminUsers(String plan, Boolean activeOnly);
}
