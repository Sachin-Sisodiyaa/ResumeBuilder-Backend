package com.resumeai.web.service;

import com.resumeai.web.model.WebOverview;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Web portal service that aggregates live data from the microservice APIs.
 *
 * <p>When an upstream service is unavailable the response falls back to
 * metadata so the web layer remains usable during local development.
 */
@Service
public class WebPortalServiceImpl implements WebPortalService {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new ParameterizedTypeReference<>() { };

    private final WebClient webClient;

    @Value("${app.services.auth:http://auth-service}")
    private String authServiceUrl = "http://auth-service";

    @Value("${app.services.resume:http://resume-service}")
    private String resumeServiceUrl = "http://resume-service";

    @Value("${app.services.section:http://section-service}")
    private String sectionServiceUrl = "http://section-service";

    @Value("${app.services.ai:http://ai-service}")
    private String aiServiceUrl = "http://ai-service";

    @Value("${app.services.template:http://template-service}")
    private String templateServiceUrl = "http://template-service";

    @Value("${app.services.export:http://export-service}")
    private String exportServiceUrl = "http://export-service";

    @Value("${app.services.jobmatch:http://jobmatch-service}")
    private String jobMatchServiceUrl = "http://jobmatch-service";

    @Value("${app.services.notification:http://notification-service}")
    private String notificationServiceUrl = "http://notification-service";

    WebPortalServiceImpl() {
        this(WebClient.builder());
    }

    public WebPortalServiceImpl(@Qualifier("loadBalancedWebClientBuilder") WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public WebOverview home() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("application", "ResumeAI");
        payload.put("version", "1.0.0");
        payload.put("tagline", "Build Smarter. Apply Faster. Land the Job.");
        payload.put("services", serviceRegistry());
        payload.put("serviceHealth", summarizeServiceHealth());
        payload.put("controllers", List.of("ResumeController", "BuilderController", "AdminController"));
        return WebOverview.of(payload);
    }

    @Override
    public WebOverview templates(String category, String plan) {
        Boolean premium = plan == null ? null : "PREMIUM".equalsIgnoreCase(plan);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("activeOnly", true);
        if (premium != null) {
            params.put("premium", premium);
        }
        if (category != null) {
            params.put("category", category);
        }
        String uri = buildUri(templateServiceUrl + "/api/v1/templates", params, null);
        List<Map<String, Object>> templates = fetchList(uri, List.of());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category == null ? "all" : category);
        payload.put("plan", plan == null ? "all" : plan);
        payload.put("upstream", uri);
        payload.put("templates", templates);
        payload.put("totalTemplates", templates.size());
        payload.put("description", "Browse all templates");
        return WebOverview.of(payload);
    }

    @Override
    public WebOverview gallery(String jobTitle, String templateCategory) {
        List<Map<String, Object>> resumes = fetchList(
                resumeServiceUrl + "/api/v1/resumes?publicOnly=true", List.of());
        List<Map<String, Object>> filtered = resumes.stream()
                .filter(resume -> {
                    if (jobTitle == null || jobTitle.isBlank()) {
                        return true;
                    }
                    Object target = resume.get("targetJobTitle");
                    return target != null && target.toString().toLowerCase().contains(jobTitle.toLowerCase());
                })
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobTitle", jobTitle == null ? "" : jobTitle);
        payload.put("templateCategory", templateCategory == null ? "" : templateCategory);
        payload.put("upstream", "resume-service:/api/v1/resumes?publicOnly=true");
        payload.put("sourceUri", resumeServiceUrl + "/api/v1/resumes?publicOnly=true");
        payload.put("resumes", filtered);
        payload.put("totalPublicResumes", filtered.size());
        return WebOverview.of(payload);
    }

    @Override
    public WebOverview dashboard(Long userId) {
        List<Map<String, Object>> resumes = fetchList(
                resumeServiceUrl + "/api/v1/resumes?userId=" + userId, List.of());
        List<Map<String, Object>> notifications = fetchList(
                notificationServiceUrl + "/api/v1/notifications?recipientId=" + userId, List.of());
        List<Map<String, Object>> jobMatches = fetchList(
                jobMatchServiceUrl + "/api/v1/job-matches?userId=" + userId, List.of());

        Map<String, Object> quota = aiQuota(userId).getPayload();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("resumeCount", resumes.size());
        payload.put("recentResumes", resumes.stream().limit(5).toList());
        payload.put("latestNotifications", notifications.stream().limit(5).toList());
        payload.put("topJobMatches", jobMatches.stream().limit(5).toList());
        payload.put("quota", quota);
        payload.put("quickActions", List.of(
                "create-resume",
                "check-ats",
                "generate-summary",
                "export-pdf",
                "browse-jobs"));
        return WebOverview.of(payload);
    }

    @Override
    public WebOverview notifications(Long userId) {
        List<Map<String, Object>> notifications = fetchList(
                notificationServiceUrl + "/api/v1/notifications?recipientId=" + userId, List.of());
        return WebOverview.of(Map.of(
                "userId", userId,
                "upstream", "notification-service:/api/v1/notifications?recipientId=" + userId,
                "sourceUri", notificationServiceUrl + "/api/v1/notifications?recipientId=" + userId,
                "notifications", notifications,
                "unreadCount", notifications.stream().filter(n -> !Boolean.TRUE.equals(n.get("read"))).count()));
    }

    @Override
    public WebOverview unreadNotificationCount(Long userId) {
        Map<String, Object> unread = fetchMap(
                notificationServiceUrl + "/api/v1/notifications/unread-count/" + userId,
                Map.of("unreadCount", 0));
        return WebOverview.of(Map.of(
                "userId", userId,
                "upstream", "notification-service:/api/v1/notifications/unread-count/" + userId,
                "sourceUri", notificationServiceUrl + "/api/v1/notifications/unread-count/" + userId,
                "unreadCount", unread.getOrDefault("unreadCount", 0)));
    }

    @Override
    public WebOverview builder(Long resumeId) {
        Map<String, Object> resume = fetchMap(
                resumeServiceUrl + "/api/v1/resumes/" + resumeId, Map.of("resumeId", resumeId));
        List<Map<String, Object>> sections = fetchList(
                sectionServiceUrl + "/api/v1/sections?resumeId=" + resumeId, List.of());
        Object templateId = resume.get("templateId");
        Map<String, Object> template = templateId == null
                ? Map.of()
                : fetchMap(templateServiceUrl + "/api/v1/templates/" + templateId, Map.of());

        return WebOverview.of(Map.of(
                "resume", resume,
                "sections", sections,
                "template", template,
                "builderCapabilities", List.of(
                        "section-crud",
                        "drag-and-drop-ordering",
                        "visibility-toggle",
                        "ai-summary",
                        "ai-bullets",
                        "ats-check",
                        "export")));
    }

    @Override
    public WebOverview preview(Long resumeId) {
        Map<String, Object> resume = fetchMap(
                resumeServiceUrl + "/api/v1/resumes/" + resumeId, Map.of("resumeId", resumeId));
        List<Map<String, Object>> sections = fetchList(
                sectionServiceUrl + "/api/v1/sections?resumeId=" + resumeId, List.of());
        Object templateId = resume.get("templateId");
        Map<String, Object> template = templateId == null
                ? Map.of()
                : fetchMap(templateServiceUrl + "/api/v1/templates/" + templateId, Map.of());

        return WebOverview.of(Map.of(
                "resume", resume,
                "sections", sections,
                "template", template,
                "renderModel", buildRenderModel(resume, sections, template)));
    }

    @Override
    public WebOverview aiQuota(Long userId) {
        Map<String, Object> user = fetchMap(authServiceUrl + "/api/v1/auth/profile/" + userId, Map.of());
        String plan = String.valueOf(user.getOrDefault("subscriptionPlan", "FREE"));
        Map<String, Object> quota = fetchMap(
                aiServiceUrl + "/api/v1/ai/quota/" + userId + "/" + plan,
                Map.of(
                        "subscriptionPlan", plan,
                        "remainingAiCalls", 5,
                        "remainingAtsChecks", 3));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("subscriptionPlan", plan);
        payload.putAll(quota);
        return WebOverview.of(payload);
    }

    @Override
    public WebOverview aiHistory(Long userId) {
        List<Map<String, Object>> history = fetchList(
                aiServiceUrl + "/api/v1/ai/history/" + userId, List.of());
        int totalTokens = history.stream()
                .map(entry -> asInt(entry.get("tokensUsed")))
                .reduce(0, Integer::sum);

        return WebOverview.of(Map.of(
                "userId", userId,
                "requests", history,
                "totalRequests", history.size(),
                "totalTokens", totalTokens));
    }

    @Override
    public WebOverview adminDashboard() {
        Map<String, Object> platformAnalytics = platformAnalytics().getPayload();
        Map<String, Object> aiAnalytics = aiAnalytics().getPayload();
        return WebOverview.of(Map.of(
                "platformAnalytics", platformAnalytics,
                "aiAnalytics", aiAnalytics,
                "managementAreas", List.of("users", "templates", "notifications", "audit-logs")));
    }

    @Override
    public WebOverview platformAnalytics() {
        Map<String, Object> userAnalytics = fetchMap(
                authServiceUrl + "/api/v1/auth/users/analytics", Map.of());
        List<Map<String, Object>> resumes = fetchList(
                resumeServiceUrl + "/api/v1/resumes?publicOnly=false", List.of());
        Map<String, Object> exportStats = fetchMap(
                exportServiceUrl + "/api/v1/exports/stats/summary", Map.of());
        List<Map<String, Object>> templates = fetchList(
                templateServiceUrl + "/api/v1/templates/popular", List.of());

        return WebOverview.of(Map.of(
                "users", userAnalytics,
                "totalResumes", resumes.size(),
                "publicResumes", resumes.stream().filter(r -> Boolean.TRUE.equals(r.get("public"))).count(),
                "exports", exportStats,
                "mostUsedTemplates", templates,
                "dataSources", serviceRegistry()));
    }

    @Override
    public WebOverview aiAnalytics() {
        List<Map<String, Object>> users = fetchList(authServiceUrl + "/api/v1/auth/users", List.of());
        List<Map<String, Object>> allRequests = new ArrayList<>();
        for (Map<String, Object> user : users) {
            Object userId = user.get("userId");
            if (userId != null) {
                allRequests.addAll(fetchList(aiServiceUrl + "/api/v1/ai/history/" + userId, List.of()));
            }
        }

        Map<String, Integer> callsByModel = new LinkedHashMap<>();
        Map<String, Integer> tokensByModel = new LinkedHashMap<>();
        Map<String, Integer> quotaByUser = new LinkedHashMap<>();

        for (Map<String, Object> request : allRequests) {
            String model = String.valueOf(request.getOrDefault("model", "unknown"));
            int tokens = asInt(request.get("tokensUsed"));
            String userId = String.valueOf(request.getOrDefault("userId", "unknown"));
            callsByModel.merge(model, 1, Integer::sum);
            tokensByModel.merge(model, tokens, Integer::sum);
            quotaByUser.merge(userId, 1, Integer::sum);
        }

        return WebOverview.of(Map.of(
                "totalRequests", allRequests.size(),
                "totalTokensConsumed", tokensByModel.values().stream().reduce(0, Integer::sum),
                "callsByModel", callsByModel,
                "tokensByModel", tokensByModel,
                "quotaUtilisationByUser", quotaByUser,
                "models", List.of("gpt-4o", "claude-3-5-sonnet")));
    }

    @Override
    public WebOverview broadcast() {
        return broadcast(null);
    }

    @Override
    public WebOverview broadcast(String tier) {
        List<Map<String, Object>> users = tier == null
                ? fetchList(authServiceUrl + "/api/v1/auth/users", List.of())
                : fetchList(authServiceUrl + "/api/v1/auth/users/by-plan?plan=" + tier, List.of());

        List<Long> recipientIds = users.stream()
                .map(user -> asLong(user.get("userId")))
                .filter(id -> id != null)
                .toList();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("recipientIds", recipientIds);
        request.put("type", "BROADCAST");
        request.put("title", "ResumeAI Platform Update");
        request.put("message", tier == null
                ? "New improvements are available across the ResumeAI platform."
                : "New improvements are available for " + tier.toUpperCase() + " subscribers.");
        request.put("channel", "ALL");
        request.put("relatedId", null);
        request.put("relatedType", "broadcast");
        request.put("actionUrl", "/dashboard");
        request.put("recipientEmail", null);

        List<Map<String, Object>> notifications = postForList(
                notificationServiceUrl + "/api/v1/notifications/bulk", request, List.of());
        return WebOverview.of(Map.of(
                "targetTier", tier == null ? "ALL" : tier.toUpperCase(),
                "upstream", "notification-service:/api/v1/notifications/bulk",
                "sourceUri", notificationServiceUrl + "/api/v1/notifications/bulk",
                "recipientCount", recipientIds.size(),
                "notificationsSent", notifications.size()));
    }

    @Override
    public WebOverview auditLogs(String action, String entityType) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("action", action);
        params.put("entityType", entityType);
        String uri = buildUri(authServiceUrl + "/api/v1/auth/audit-logs", params, null);
        List<Map<String, Object>> logs = fetchList(uri, List.of());
        return WebOverview.of(Map.of(
                "filters", Map.of(
                        "action", action == null ? "" : action,
                        "entityType", entityType == null ? "" : entityType),
                "upstream", "auth-service:/api/v1/auth/audit-logs",
                "sourceUri", uri,
                "logs", logs,
                "count", logs.size()));
    }

    @Override
    public WebOverview adminUsers(String plan, Boolean activeOnly) {
        String uri = plan == null
                ? authServiceUrl + "/api/v1/auth/users"
                : authServiceUrl + "/api/v1/auth/users/by-plan?plan=" + plan;
        List<Map<String, Object>> users = fetchList(uri, List.of());
        List<Map<String, Object>> filtered = users.stream()
                .filter(user -> !Boolean.TRUE.equals(activeOnly) || Boolean.TRUE.equals(user.get("active")))
                .toList();

        return WebOverview.of(Map.of(
                "users", filtered,
                "totalUsers", filtered.size(),
                "managementActions", List.of(
                        "update-subscription",
                        "deactivate-user",
                        "reactivate-user",
                        "delete-user")));
    }

    private Map<String, Object> serviceRegistry() {
        return Map.of(
                "auth-service", authServiceUrl,
                "resume-service", resumeServiceUrl,
                "section-service", sectionServiceUrl,
                "ai-service", aiServiceUrl,
                "template-service", templateServiceUrl,
                "export-service", exportServiceUrl,
                "jobmatch-service", jobMatchServiceUrl,
                "notification-service", notificationServiceUrl);
    }

    private Map<String, Object> summarizeServiceHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        serviceRegistry().forEach((name, url) -> health.put(name, ping(String.valueOf(url))));
        return health;
    }

    private String ping(String baseUrl) {
        try {
            webClient.get().uri(baseUrl + "/actuator/health").retrieve().toBodilessEntity().block();
            return "UP";
        } catch (Exception ex) {
            return "UNKNOWN";
        }
    }

    private Map<String, Object> buildRenderModel(Map<String, Object> resume,
                                                 List<Map<String, Object>> sections,
                                                 Map<String, Object> template) {
        List<Map<String, Object>> visibleSections = sections.stream()
                .filter(section -> !section.containsKey("visible") || Boolean.TRUE.equals(section.get("visible")))
                .sorted(Comparator.comparingInt(section -> asInt(section.get("displayOrder"))))
                .toList();
        return Map.of(
                "resumeId", nonNullValue(resume.get("resumeId")),
                "title", nonNullValue(resume.get("title")),
                "targetJobTitle", nonNullValue(resume.get("targetJobTitle")),
                "templateName", nonNullValue(template.get("name")),
                "templateCss", nonNullValue(template.get("cssStyles")),
                "templateHtml", nonNullValue(template.get("htmlLayout")),
                "sections", visibleSections);
    }

    private Object nonNullValue(Object value) {
        return value == null ? "" : value;
    }

    private List<Map<String, Object>> fetchList(String uri, List<Map<String, Object>> fallback) {
        try {
            List<Map<String, Object>> body = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(LIST_OF_MAPS)
                    .block();
            return body == null ? fallback : body;
        } catch (WebClientResponseException ex) {
            return fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private Map<String, Object> fetchMap(String uri, Map<String, Object> fallback) {
        try {
            Map<String, Object> body = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { })
                    .block();
            return body == null ? fallback : body;
        } catch (WebClientResponseException ex) {
            return fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private List<Map<String, Object>> postForList(String uri, Map<String, Object> body,
                                                  List<Map<String, Object>> fallback) {
        try {
            List<Map<String, Object>> response = webClient.post()
                    .uri(uri)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(LIST_OF_MAPS)
                    .block();
            return response == null ? fallback : response;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String buildUri(String baseUri, Map<String, ?> params, Map<String, ?> fallbackParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUri);
        Map<String, ?> effective = params == null || params.isEmpty() ? fallbackParams : params;
        if (effective != null) {
            effective.forEach((key, value) -> {
                if (value != null && !value.toString().isBlank()) {
                    builder.queryParam(key, value);
                }
            });
        }
        return builder.toUriString();
    }

    private Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

}
