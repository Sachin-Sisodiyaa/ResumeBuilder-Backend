package com.resumeai.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebPortalServiceImplTest {

    private final WebPortalServiceImpl webPortalService = new WebPortalServiceImpl();

    @Test
    void homeReturnsRegisteredServices() {
        var overview = webPortalService.home();
        assertTrue(overview.getPayload().containsKey("services"));
    }

    @Test
    void dashboardContainsRequestedUserId() {
        var overview = webPortalService.dashboard(11L);
        assertEquals(11L, overview.getPayload().get("userId"));
    }

    @Test
    void broadcastPointsToNotificationService() {
        var overview = webPortalService.broadcast();
        assertTrue(overview.getPayload().get("upstream").toString().contains("notification-service"));
    }

    @Test
    void broadcastWithTierSetsTargetTier() {
        var overview = webPortalService.broadcast("PREMIUM");
        assertEquals("PREMIUM", overview.getPayload().get("targetTier"));
    }

    @Test
    void galleryReturnsPublicGalleryUpstream() {
        var overview = webPortalService.gallery(null, null);
        assertTrue(overview.getPayload().get("upstream").toString().contains("publicOnly=true"));
    }

    @Test
    void unreadNotificationCountContainsUserId() {
        var overview = webPortalService.unreadNotificationCount(5L);
        assertTrue(overview.getPayload().get("upstream").toString().contains("5"));
    }

    @Test
    void platformAnalyticsContainsDataSources() {
        var overview = webPortalService.platformAnalytics();
        assertNotNull(overview.getPayload().get("dataSources"));
    }

    @Test
    void aiAnalyticsContainsModels() {
        var overview = webPortalService.aiAnalytics();
        assertNotNull(overview.getPayload().get("models"));
    }

    @Test
    void auditLogsContainsUpstream() {
        var overview = webPortalService.auditLogs(null, null);
        assertTrue(overview.getPayload().get("upstream").toString().contains("audit-logs"));
    }

    @Test
    void adminUsersContainsManagementActions() {
        var overview = webPortalService.adminUsers("FREE", true);
        assertTrue(overview.getPayload().containsKey("managementActions"));
    }

    @Test
    void templatesBuilderPreviewAiAndNotificationViewsReturnFallbackPayloads() {
        assertEquals("technical", webPortalService.templates("technical", "PREMIUM").getPayload().get("category"));
        assertTrue((Integer) webPortalService.templates(null, null).getPayload().get("totalTemplates") >= 0);

        assertTrue(webPortalService.builder(42L).getPayload().containsKey("resume"));
        assertTrue(webPortalService.preview(42L).getPayload().containsKey("renderModel"));
        assertEquals(42L, webPortalService.notifications(42L).getPayload().get("userId"));
        assertEquals(42L, webPortalService.aiQuota(42L).getPayload().get("userId"));
        assertEquals(42L, webPortalService.aiHistory(42L).getPayload().get("userId"));
    }

    @Test
    void adminDashboardAndFilteredViewsReturnExpectedKeys() {
        assertTrue(webPortalService.adminDashboard().getPayload().containsKey("platformAnalytics"));
        assertEquals(0, webPortalService.auditLogs("LOGIN", "USER").getPayload().get("count"));
        assertEquals(0, webPortalService.adminUsers(null, null).getPayload().get("totalUsers"));
        assertEquals(0, webPortalService.gallery("engineer", "modern").getPayload().get("totalPublicResumes"));
    }

    @Test
    void upstreamSuccessResponsesAreAggregatedAndFiltered() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/templates", exchange -> respond(exchange,
                "[{\"templateId\":1,\"name\":\"Modern\"}]"));
        server.createContext("/api/v1/resumes", exchange -> respond(exchange,
                "[{\"resumeId\":1,\"targetJobTitle\":\"Platform Engineer\",\"public\":true},"
                        + "{\"resumeId\":2,\"targetJobTitle\":\"Designer\",\"public\":false}]"));
        server.createContext("/api/v1/auth/users", exchange -> respond(exchange,
                "[{\"userId\":7,\"active\":true},{\"userId\":\"bad\",\"active\":false},{\"active\":false}]"));
        server.createContext("/api/v1/notifications/bulk", exchange -> respond(exchange,
                "[{\"notificationId\":\"n1\"}]"));
        server.start();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            setField(webPortalService, "templateServiceUrl", baseUrl);
            setField(webPortalService, "resumeServiceUrl", baseUrl);
            setField(webPortalService, "authServiceUrl", baseUrl);
            setField(webPortalService, "notificationServiceUrl", baseUrl);

            assertEquals(1, webPortalService.templates("tech", "FREE").getPayload().get("totalTemplates"));
            assertEquals(1, webPortalService.gallery("platform", null).getPayload().get("totalPublicResumes"));
            assertEquals(1, webPortalService.adminUsers(null, true).getPayload().get("totalUsers"));
            assertEquals(1, webPortalService.broadcast().getPayload().get("recipientCount"));
            assertEquals(1, webPortalService.broadcast().getPayload().get("notificationsSent"));
        } finally {
            server.stop(0);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
