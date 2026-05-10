 package com.resumeai.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resumeai.ai.dto.AiDtos.AtsRequest;
import com.resumeai.ai.dto.AiDtos.ContentRequest;
import com.resumeai.ai.dto.AiDtos.TailorRequest;
import com.resumeai.ai.model.AiRequest;
import com.resumeai.ai.repository.AiRequestRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiServiceImplTest {

    @Mock
    private AiRequestRepository aiRequestRepository;

    private AiServiceImpl aiService;

    @BeforeEach
    void setUp() throws Exception {
        aiService = new AiServiceImpl(aiRequestRepository);
        setField(aiService, "primaryModel",   "llama-3.3-70b-versatile");
        setField(aiService, "secondaryModel", "llama-3.1-8b-instant");
        setField(aiService, "mockEnabled", true);

        // Default: save returns the passed AiRequest unchanged
        when(aiRequestRepository.save(any(AiRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // Default: no prior history
        when(aiRequestRepository.findAll()).thenReturn(List.of());
        when(aiRequestRepository.countByUserIdAndCreatedAtAfter(any(), any())).thenReturn(0L);
        when(aiRequestRepository.countByUserIdAndRequestTypeAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);
        when(aiRequestRepository.findByUserIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
    }

    // ── Generate summary ─────────────────────────────────────────────────────

    @Test
    void generateSummaryReturnsPrimaryModelResponse() {
        String result = aiService.generateSummary(contentReq("FREE", "Java Developer"));
        assertNotNull(result);
        assertTrue(result.contains("Groq") || result.contains("Professional content"),
                "Expected Groq stub response, got: " + result);
    }

    @Test
    void generateSummaryThrowsWhenFreeQuotaExhausted() {
        // Simulate 5 prior calls this month
        when(aiRequestRepository.countByUserIdAndCreatedAtAfter(any(), any())).thenReturn(5L);

        assertThrows(ResponseStatusException.class,
                () -> aiService.generateSummary(contentReq("FREE", "Java Developer")));
    }

    // ── Generate bullets ─────────────────────────────────────────────────────

    @Test
    void generateBulletPointsReturnsList() {
        List<String> bullets = aiService.generateBulletPoints(contentReq("FREE", "Developer"));
        assertNotNull(bullets);
        // Stub response is a single line → list has at least one entry
        assertFalse(bullets.isEmpty());
    }

    // ── Cover letter (Premium only) ───────────────────────────────────────────

    @Test
    void generateCoverLetterRequiresPremium() {
        assertThrows(ResponseStatusException.class,
                () -> aiService.generateCoverLetter(contentReq("FREE", "Manager")));
    }

    @Test
    void generateCoverLetterSucceedsForPremium() {
        String result = aiService.generateCoverLetter(contentReq("PREMIUM", "Manager"));
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    // ── Improve section (Premium only) ────────────────────────────────────────

    @Test
    void improveSectionRequiresPremium() {
        assertThrows(ResponseStatusException.class,
                () -> aiService.improveSection(contentReq("FREE", "Engineer")));
    }

    @Test
    void improveTranslateAndTailorSucceedForPremium() {
        assertFalse(aiService.improveSection(contentReq("PREMIUM", "Engineer")).isBlank());
        assertFalse(aiService.translateResume(contentReq("PREMIUM", "Engineer")).isBlank());
        assertFalse(aiService.tailorResumeForJob(
                new TailorRequest(1L, 1L, "PREMIUM", "{}", "Senior Java Developer role")).isBlank());
    }

    // ── ATS compatibility check ────────────────────────────────────────────────

    @Test
    void checkAtsCompatibilityScoresCorrectly() {
        var response = aiService.checkAtsCompatibility(new AtsRequest(
                1L, 1L, "FREE",
                "java spring boot microservices docker kubernetes aws",
                "java spring boot microservices docker kubernetes aws sql python"));

        assertTrue(response.score() >= 0 && response.score() <= 100);
        assertNotNull(response.missingKeywords());
        assertNotNull(response.feedback());
    }

    @Test
    void checkAtsReturnsHighScoreForExactMatch() {
        var response = aiService.checkAtsCompatibility(new AtsRequest(
                1L, 1L, "FREE",
                "java spring docker kubernetes",
                "java spring docker kubernetes"));

        assertEquals(100, response.score());
        assertTrue(response.missingKeywords().isEmpty());
    }

    @Test
    void checkAtsReturnsLowAndZeroScoreBranches() {
        var low = aiService.checkAtsCompatibility(new AtsRequest(
                1L, 1L, "FREE", "java", "python kubernetes leadership"));
        assertTrue(low.score() < 50);
        assertFalse(low.missingKeywords().isEmpty());

        var empty = aiService.checkAtsCompatibility(new AtsRequest(
                1L, 1L, "PREMIUM", "java", ""));
        assertEquals(0, empty.score());
    }

    @Test
    void checkAtsThrowsWhenFreeAtsQuotaExhausted() {
        when(aiRequestRepository.countByUserIdAndRequestTypeAndCreatedAtAfter(any(), any(), any()))
                .thenReturn(0L, 0L, 1L, 1L, 2L, 2L, 3L);

        // Exhaust the 3-check limit
        aiService.checkAtsCompatibility(new AtsRequest(1L, 1L, "FREE", "java", "java python"));
        aiService.checkAtsCompatibility(new AtsRequest(1L, 1L, "FREE", "java", "java python"));
        aiService.checkAtsCompatibility(new AtsRequest(1L, 1L, "FREE", "java", "java python"));

        assertThrows(ResponseStatusException.class,
                () -> aiService.checkAtsCompatibility(
                        new AtsRequest(1L, 1L, "FREE", "java", "java python")));
    }

    // ── Skill suggestions ─────────────────────────────────────────────────────

    @Test
    void suggestSkillsReturnsNonEmptyList() {
        var response = aiService.suggestSkills(contentReq("FREE", "Data Scientist"));
        assertNotNull(response.skills());
        assertFalse(response.skills().isEmpty());
    }

    @Test
    void generateSummaryUsesConfiguredOpenAiProvider() throws Exception {
        HttpServer server = jsonServer(200,
                "{\"choices\":[{\"message\":{\"content\":\"OpenAI summary\"}}]}");
        try {
            setField(aiService, "primaryModel", "gpt-4o");
            setField(aiService, "openAiApiKey", "test-key");
            setField(aiService, "openAiBaseUrl", serverUrl(server));

            assertEquals("OpenAI summary", aiService.generateSummary(contentReq("FREE", "Engineer")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void improveSectionUsesConfiguredClaudeProvider() throws Exception {
        HttpServer server = jsonServer(200,
                "{\"content\":[{\"text\":\"Claude rewrite\"}]}");
        try {
            setField(aiService, "primaryModel", "claude-3-5-sonnet");
            setField(aiService, "claudeApiKey", "test-key");
            setField(aiService, "claudeBaseUrl", serverUrl(server));

            assertEquals("Claude rewrite", aiService.improveSection(contentReq("PREMIUM", "Engineer")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void providerHttpFailureFallsBackToMockWhenEnabled() throws Exception {
        HttpServer server = jsonServer(500, "{\"error\":{\"message\":\"provider down\"}}");
        try {
            setField(aiService, "groqApiKey", "test-key");
            setField(aiService, "openAiApiKey", "test-key");
            setField(aiService, "primaryModel", "llama-3.3-70b-versatile");
            setField(aiService, "secondaryModel", "gpt-4o");
            setField(aiService, "groqBaseUrl", serverUrl(server));
            setField(aiService, "openAiBaseUrl", serverUrl(server));

            String response = aiService.generateSummary(contentReq("FREE", "Engineer"));

            assertTrue(response.contains("Provider fallback"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void providerFailureThrowsBadGatewayWhenMockDisabled() throws Exception {
        setField(aiService, "mockEnabled", false);

        assertThrows(ResponseStatusException.class,
                () -> aiService.generateSummary(contentReq("FREE", "Engineer")));
    }

    @Test
    void checkAtsUsesParsableAiJsonWhenProviderReturnsIt() throws Exception {
        String atsJson = "{\"choices\":[{\"message\":{\"content\":\"```json\\n"
                + "{\\\"score\\\":88,\\\"missingKeywords\\\":[\\\"aws\\\"],\\\"feedback\\\":\\\"Strong match.\\\"}"
                + "\\n```\"}}]}";
        HttpServer server = jsonServer(200, atsJson);
        try {
            setField(aiService, "groqApiKey", "test-key");
            setField(aiService, "groqBaseUrl", serverUrl(server));

            var response = aiService.checkAtsCompatibility(new AtsRequest(
                    1L, 1L, "PREMIUM", "java spring", "java spring aws"));

            assertEquals(88, response.score());
            assertEquals(List.of("aws"), response.missingKeywords());
            assertEquals("Strong match.", response.feedback());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void openAiAndClaudeEmptyProviderResponsesReturnBlankContent() throws Exception {
        HttpServer openAiServer = jsonServer(200, "{\"choices\":[]}");
        HttpServer claudeServer = jsonServer(200, "{\"content\":[]}");
        try {
            setField(aiService, "primaryModel", "gpt-4o");
            setField(aiService, "openAiApiKey", "test-key");
            setField(aiService, "openAiBaseUrl", serverUrl(openAiServer));
            assertEquals("", aiService.generateSummary(contentReq("FREE", "Engineer")));

            setField(aiService, "primaryModel", "claude-3-5-sonnet");
            setField(aiService, "claudeApiKey", "test-key");
            setField(aiService, "claudeBaseUrl", serverUrl(claudeServer));
            assertEquals("", aiService.improveSection(contentReq("PREMIUM", "Engineer")));
        } finally {
            openAiServer.stop(0);
            claudeServer.stop(0);
        }
    }

    // ── Tailor (Premium only) ─────────────────────────────────────────────────

    @Test
    void tailorResumeRequiresPremium() {
        assertThrows(ResponseStatusException.class,
                () -> aiService.tailorResumeForJob(
                        new TailorRequest(1L, 1L, "FREE", "{}", "Senior Java Developer role")));
    }

    @Test
    void tailorResumeSucceedsForPremium() {
        String result = aiService.tailorResumeForJob(
                new TailorRequest(1L, 1L, "PREMIUM", "{}", "Senior Java Developer role"));
        assertNotNull(result);
    }

    // ── Translate (Premium only) ──────────────────────────────────────────────

    @Test
    void translateRequiresPremium() {
        assertThrows(ResponseStatusException.class,
                () -> aiService.translateResume(
                        new ContentRequest(1L, 1L, "FREE", null, null,
                                null, null, "English content", "French")));
    }

    @Test
    void notificationsAreSentForAiAtsAndQuotaWarnings() throws Exception {
        AiNotificationClient notificationClient = org.mockito.Mockito.mock(AiNotificationClient.class);
        aiService = new AiServiceImpl(aiRequestRepository, availableProvider(notificationClient), emptyProvider());
        setField(aiService, "primaryModel", "llama-3.3-70b-versatile");
        setField(aiService, "secondaryModel", "llama-3.1-8b-instant");
        setField(aiService, "mockEnabled", true);
        when(aiRequestRepository.save(any(AiRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aiRequestRepository.countByUserIdAndCreatedAtAfter(any(), any())).thenReturn(4L);
        when(aiRequestRepository.countByUserIdAndRequestTypeAndCreatedAtAfter(any(), any(), any())).thenReturn(2L);

        aiService.generateSummary(contentReq("FREE", "Developer"));
        aiService.checkAtsCompatibility(new AtsRequest(1L, 1L, "FREE", "java", "java python"));

        verify(notificationClient, atLeastOnce()).notifyUser(argThat(payload -> "AI_DONE".equals(payload.get("type"))));
        verify(notificationClient, atLeastOnce()).notifyUser(argThat(payload -> "ATS_SCORE_COMPLETE".equals(payload.get("type"))));
        verify(notificationClient, atLeastOnce()).notifyUser(argThat(payload -> "QUOTA_WARNING".equals(payload.get("type"))));
    }

    // ── History & Quota ────────────────────────────────────────────────────────

    @Test
    void getAiHistoryFiltersToUser() {
        AiRequest userReq   = aiReq(99L, java.time.YearMonth.now(java.time.ZoneOffset.UTC).toString());
        when(aiRequestRepository.findByUserIdOrderByCreatedAtDesc(99L)).thenReturn(List.of(userReq));

        List<AiRequest> history = aiService.getAiHistory(99L);
        assertEquals(1, history.size());
        assertEquals(99L, history.get(0).getUserId());
    }

    @Test
    void remainingQuotaIsCorrectForFreeUser() {
        // 2 calls used already
        when(aiRequestRepository.countByUserIdAndCreatedAtAfter(any(), any())).thenReturn(2L);
        when(aiRequestRepository.countByUserIdAndRequestTypeAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);

        var quota = aiService.getRemainingQuota(1L, "FREE");
        assertEquals(2L, quota.aiCallsUsed());
        assertEquals(3L, quota.remainingAiCalls()); // 5 - 2
        assertEquals("FREE", quota.subscriptionPlan());
    }

    @Test
    void premiumUserHasUnlimitedQuota() {
        var quota = aiService.getRemainingQuota(1L, "PREMIUM");
        assertEquals(Long.MAX_VALUE, quota.remainingAiCalls());
        assertEquals(Long.MAX_VALUE, quota.remainingAtsChecks());
    }

    @Test
    void logMonthlyQuotaResetDoesNotThrow() {
        assertDoesNotThrow(() -> aiService.logMonthlyQuotaReset());
    }

    @Test
    void providerConfigurationAndFallbackHelpersCoverEdgeBranches() throws Exception {
        setField(aiService, "groqApiKey", "groq-key");
        setField(aiService, "openAiApiKey", "");
        setField(aiService, "claudeApiKey", null);

        assertDoesNotThrow(() -> aiService.logProviderConfiguration());
        assertEquals("{}", invokeString(aiService, "ensureJsonObjectResponse", "not-json", "{}"));
        assertEquals("{\"sections\":[],\"tailoringStatus\":\"AI response was not valid JSON\"}",
                invokeString(aiService, "ensureJsonObjectResponse", "not-json", "also-not-json"));
        assertEquals("", invokeString(aiService, "sanitize", (Object) null));
    }

    @Test
    void redisQuotaCacheIsReadWrittenAndFailureTolerant() throws Exception {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ai:quota:1:" + java.time.YearMonth.now(java.time.ZoneOffset.UTC) + ":AI_TOTAL"))
                .thenReturn("2");

        aiService = new AiServiceImpl(aiRequestRepository, availableProvider((AiNotificationClient) null),
                availableProvider(redisTemplate));
        setField(aiService, "primaryModel", "llama-3.3-70b-versatile");
        setField(aiService, "secondaryModel", "llama-3.1-8b-instant");
        setField(aiService, "mockEnabled", true);
        when(aiRequestRepository.save(any(AiRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aiRequestRepository.countByUserIdAndRequestTypeAndCreatedAtAfter(any(), any(), any())).thenReturn(1L);

        var quota = aiService.getRemainingQuota(1L, "FREE");

        assertEquals(2L, quota.aiCallsUsed());
        assertEquals(1L, quota.atsChecksUsed());

        doThrow(new RuntimeException("redis down")).when(valueOperations).increment(any());
        assertDoesNotThrow(() -> aiService.generateSummary(contentReq("FREE", "Developer")));
    }

    @Test
    void invalidCachedQuotaFallsBackToDatabaseAndCacheWriteFailuresAreIgnored() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn("not-a-number");
        doThrow(new RuntimeException("redis write down"))
                .when(valueOperations).set(any(), any(), any(Long.class), any());

        aiService = new AiServiceImpl(aiRequestRepository, availableProvider((AiNotificationClient) null),
                availableProvider(redisTemplate));
        when(aiRequestRepository.countByUserIdAndCreatedAtAfter(any(), any())).thenReturn(3L);
        when(aiRequestRepository.countByUserIdAndRequestTypeAndCreatedAtAfter(any(), any(), any())).thenReturn(2L);

        var quota = aiService.getRemainingQuota(7L, "FREE");

        assertEquals(3L, quota.aiCallsUsed());
        assertEquals(2L, quota.atsChecksUsed());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ContentRequest contentReq(String plan, String jobTitle) {
        return new ContentRequest(1L, 1L, plan, "generic prompt", jobTitle,
                3, List.of("Java", "Spring"), "Some content", "English");
    }

    private AiRequest aiReq(Long userId, String month) {
        AiRequest r = new AiRequest();
        r.setUserId(userId);
        r.setCreatedAt(Instant.parse(month + "-01T00:00:00Z"));
        return r;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static String invokeString(Object target, String methodName, Object... args) throws Exception {
        Class<?>[] parameterTypes = java.util.Arrays.stream(args)
                .map(arg -> String.class)
                .toArray(Class<?>[]::new);
        var method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (String) method.invoke(target, args);
    }

    private static HttpServer jsonServer(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String serverUrl(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static <T> ObjectProvider<T> availableProvider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }

    private static ObjectProvider<StringRedisTemplate> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public StringRedisTemplate getObject(Object... args) {
                return null;
            }

            @Override
            public StringRedisTemplate getIfAvailable() {
                return null;
            }

            @Override
            public StringRedisTemplate getIfUnique() {
                return null;
            }

            @Override
            public StringRedisTemplate getObject() {
                return null;
            }
        };
    }
}
