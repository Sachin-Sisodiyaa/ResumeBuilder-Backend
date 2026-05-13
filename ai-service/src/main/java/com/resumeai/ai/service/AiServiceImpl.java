package com.resumeai.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.ai.dto.AiDtos.AtsRequest;
import com.resumeai.ai.dto.AiDtos.AtsResponse;
import com.resumeai.ai.dto.AiDtos.ContentRequest;
import com.resumeai.ai.dto.AiDtos.QuotaResponse;
import com.resumeai.ai.dto.AiDtos.SkillSuggestionResponse;
import com.resumeai.ai.dto.AiDtos.TailorRequest;
import com.resumeai.ai.model.AiRequest;
import com.resumeai.ai.repository.AiRequestRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * AI content generation service.
 *
 * <p>Routes all prompts to the configured primary model (GPT-4o) with failover
 * to the secondary (Claude) if the primary is flagged unavailable.
 * Free-tier users are limited to 5 AI calls + 3 ATS checks per calendar month.
 * Premium users have unlimited calls; token usage is tracked for cost monitoring.
 *
 * <p>Runtime calls use the configured Groq/Claude API keys. A deterministic
 * mock mode exists only for local tests and must be enabled explicitly.
 */
@Service
@Slf4j
public class AiServiceImpl implements AiService {
    private static final String STATUS_CONFIGURED = "configured";
    private static final String STATUS_MISSING = "missing";
    private static final String REQUEST_SUMMARY = "SUMMARY";
    private static final String REQUEST_BULLETS = "BULLETS";
    private static final String REQUEST_ATS_CHECK = "ATS_CHECK";
    private static final String PLAN_PREMIUM = "PREMIUM";
    private static final String AI_TOTAL = "AI_TOTAL";
    private static final String PROVIDER_FALLBACK = "Provider fallback";
    private static final String DEFAULT_GROQ_MODEL = "llama-3.3-70b-versatile";
    private static final String DEFAULT_OPENAI_MODEL = "gpt-4o";
    private static final String DEFAULT_CLAUDE_MODEL = "claude-3-5-sonnet-20241022";
    private static final String LANGUAGE_SUFFIX = ". Language: ";
    private static final String JSON_MODEL = "model";
    private static final String JSON_MESSAGES = "messages";
    private static final String JSON_ROLE = "role";
    private static final String JSON_SYSTEM = "system";
    private static final String JSON_USER = "user";
    private static final String JSON_CONTENT = "content";
    private static final String JSON_MAX_TOKENS = "max_tokens";
    private static final String HTTP_CONTENT_TYPE = "Content-Type";
    private static final String SYSTEM_PROMPT =
            "You are a professional resume writing assistant. Respond concisely and professionally.";
    private static final String NOTIFICATION_RECIPIENT_ID = "recipientId";
    private static final String NOTIFICATION_RECIPIENT_EMAIL = "recipientEmail";
    private static final String NOTIFICATION_TYPE = "type";
    private static final String NOTIFICATION_TITLE = "title";
    private static final String NOTIFICATION_MESSAGE = "message";
    private static final String NOTIFICATION_CHANNEL = "channel";
    private static final String NOTIFICATION_RELATED_ID = "relatedId";
    private static final String NOTIFICATION_RELATED_TYPE = "relatedType";
    private static final String NOTIFICATION_ACTION_URL = "actionUrl";
    private static final String CHANNEL_ALL = "ALL";
    private static final String RELATED_RESUME_ID = "resumeId";

    private final AiRequestRepository aiRequestRepository;
    private final AiNotificationClient notificationClient;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public AiServiceImpl(AiRequestRepository aiRequestRepository,
                         AiNotificationClient notificationClient,
                         ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.aiRequestRepository = aiRequestRepository;
        this.notificationClient = notificationClient;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public AiServiceImpl(AiRequestRepository aiRequestRepository) {
        this(aiRequestRepository, (AiNotificationClient) null, emptyRedisProvider());
    }

    public AiServiceImpl(AiRequestRepository aiRequestRepository,
                         ObjectProvider<AiNotificationClient> notificationClientProvider,
                         ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this(aiRequestRepository,
                notificationClientProvider == null ? null : notificationClientProvider.getIfAvailable(),
                redisTemplateProvider == null ? emptyRedisProvider() : redisTemplateProvider);
    }

    @Value("${app.ai.primary-model:gpt-4o}")
    private String primaryModel;

    @Value("${app.ai.secondary-model:claude-3-5-sonnet-20241022}")
    private String secondaryModel;

    @Value("${app.ai.mock-enabled:false}")
    private boolean mockEnabled;

    @Value("${app.ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${app.ai.groq.base-url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;

    @Value("${app.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${app.ai.openai.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;

    @Value("${app.ai.claude.api-key:}")
    private String claudeApiKey;

    @Value("${app.ai.claude.base-url:https://api.anthropic.com/v1}")
    private String claudeBaseUrl;

    /** Tracks models that have failed in the current period (in-memory flag). */
    private final Set<String> unavailableModels = ConcurrentHashMap.newKeySet();

    // ATS quota is now tracked via JPA (AiRequest rows with requestType=ATS_CHECK)
    // No in-memory counter needed Ã¢â‚¬â€ survives restarts.

    @PostConstruct
    void logProviderConfiguration() {
        log.info("AI provider configuration loaded. Groq key: {}, OpenAI key: {}, Claude key: {}, mock mode: {}, primary: {}, secondary: {}",
                providerStatus(groqApiKey),
                providerStatus(openAiApiKey),
                providerStatus(claudeApiKey),
                mockEnabled,
                primaryModel,
                secondaryModel);
    }

    private static final int FREE_AI_CALLS_LIMIT  = 5;
    private static final int FREE_ATS_CHECKS_LIMIT = 3;

    @Override
    public String generateSummary(ContentRequest req) {
        enforceQuota(req.userId(), req.subscriptionPlan());
        String prompt = buildSummaryPrompt(req);
        String model = resolveModel();
        String response = persist(req.userId(), req.resumeId(), REQUEST_SUMMARY, prompt,
                callAiApi(prompt, model),
                model).getAiResponse();
        notifyAiCompletion(req.userId(), req.resumeId(), REQUEST_SUMMARY, req.recipientEmail());
        maybeNotifyQuota(req.userId(), req.subscriptionPlan(), req.recipientEmail());
        return response;
    }

    @Override
    public List<String> generateBulletPoints(ContentRequest req) {
        enforceQuota(req.userId(), req.subscriptionPlan());
        String prompt = "Generate 4-6 strong resume bullet points for a "
                + req.jobTitle() + " with " + req.yearsOfExperience()
                + " years experience. Skills: " + joinSkills(req.skills())
                + ". Context: " + sanitize(req.sectionContent())
                + LANGUAGE_SUFFIX + defaultLang(req.language());
        String model = resolveModel();
        String raw = callAiApi(prompt, model);
        persist(req.userId(), req.resumeId(), REQUEST_BULLETS, prompt, raw, model);
        notifyAiCompletion(req.userId(), req.resumeId(), REQUEST_BULLETS, req.recipientEmail());
        maybeNotifyQuota(req.userId(), req.subscriptionPlan(), req.recipientEmail());
        return Arrays.stream(raw.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    @Override
    public String generateCoverLetter(ContentRequest req) {
        requirePremium(req.subscriptionPlan(), "Cover letter generation");
        String prompt = "Write a personalised, professional cover letter for a "
                + req.jobTitle() + " role. Candidate skills: " + joinSkills(req.skills())
                + ". Years of experience: " + req.yearsOfExperience()
                + ". Job context: " + sanitize(req.sectionContent())
                + LANGUAGE_SUFFIX + defaultLang(req.language());
        String model = resolveModel();
        String response = persist(req.userId(), req.resumeId(), "COVER_LETTER", prompt,
                callAiApi(prompt, model),
                model).getAiResponse();
        notifyAiCompletion(req.userId(), req.resumeId(), "COVER_LETTER", req.recipientEmail());
        maybeNotifyQuota(req.userId(), req.subscriptionPlan(), req.recipientEmail());
        return response;
    }

    @Override
    public String improveSection(ContentRequest req) {
        requirePremium(req.subscriptionPlan(), "Section improvement");
        String prompt = "Rewrite the following resume section for maximum impact and ATS optimisation.\n"
                + "Section content:\n" + sanitize(req.sectionContent())
                + "\nJob title target: " + req.jobTitle()
                + "\nLanguage: " + defaultLang(req.language());
        String model = resolveModel();
        String response = persist(req.userId(), req.resumeId(), "IMPROVE_SECTION", prompt,
                callAiApi(prompt, model),
                model).getAiResponse();
        notifyAiCompletion(req.userId(), req.resumeId(), "IMPROVE_SECTION", req.recipientEmail());
        maybeNotifyQuota(req.userId(), req.subscriptionPlan(), req.recipientEmail());
        return response;
    }

    @Override
    public AtsResponse checkAtsCompatibility(AtsRequest req) {
        enforceAtsQuota(req.userId(), req.subscriptionPlan());
        String resumeText = sanitize(req.resumeText());
        String jobDesc    = sanitize(req.jobDescription());

        // Strip JSON structural noise so the AI (and fallback) sees real content
        String cleanResume = stripJsonNoise(resumeText);
        String cleanJobDesc = stripJsonNoise(jobDesc);

        // Try AI-powered ATS analysis first
        AtsResponse aiResult = tryAiAtsAnalysis(cleanResume, cleanJobDesc);
        if (aiResult != null) {
            String prompt = "AI ATS check: resume vs job description";
            persist(req.userId(), req.resumeId(), REQUEST_ATS_CHECK, prompt,
                    "Score: " + aiResult.score(), resolveModel());
            notifyAtsCompletion(req.userId(), req.resumeId(), aiResult.score(), req.recipientEmail());
            maybeNotifyQuota(req.userId(), req.subscriptionPlan(), req.recipientEmail());
            return aiResult;
        }

        // Fallback: improved keyword matching on cleaned text
        Set<String> jdKeywords = extractMeaningfulKeywords(cleanJobDesc);
        Set<String> resumeKeywords = extractMeaningfulKeywords(cleanResume);

        List<String> missing = jdKeywords.stream()
                .filter(kw -> !resumeKeywords.contains(kw))
                .limit(15)
                .sorted()
                .toList();

        int matchCount = (int) jdKeywords.stream().filter(resumeKeywords::contains).count();
        int score = jdKeywords.isEmpty() ? 0
                : (int) Math.round(100.0 * matchCount / jdKeywords.size());

        String feedback = atsFeedback(score);

        String prompt = "ATS check (keyword fallback): resume vs job description. Score=" + score;
        persist(req.userId(), req.resumeId(), REQUEST_ATS_CHECK, prompt,
                "Score: " + score, resolveModel());
        notifyAtsCompletion(req.userId(), req.resumeId(), score, req.recipientEmail());
        maybeNotifyQuota(req.userId(), req.subscriptionPlan(), req.recipientEmail());

        return new AtsResponse(score, missing, feedback);
    }

    /**
     * Attempts AI-powered ATS analysis. Returns null if the AI provider is
     * unavailable or the response cannot be parsed.
     */
    private AtsResponse tryAiAtsAnalysis(String resumeText, String jobDescription) {
        String prompt = "You are an expert ATS (Applicant Tracking System) analyzer.\n"
                + "Compare the following resume content against the job description.\n"
                + "Evaluate keyword match, skills alignment, experience relevance, and formatting best practices.\n"
                + "Be strict and realistic Ã¢â‚¬â€ most resumes score between 30-75 unless they are very well targeted.\n\n"
                + "RESUME CONTENT:\n" + truncate(resumeText, 3000) + "\n\n"
                + "JOB DESCRIPTION:\n" + truncate(jobDescription, 2000) + "\n\n"
                + "Return ONLY a valid JSON object (no markdown, no code fences) with these exact fields:\n"
                + "{\n"
                + "  \"score\": <integer 0-100>,\n"
                + "  \"missingKeywords\": [\"keyword1\", \"keyword2\", ...],\n"
                + "  \"feedback\": \"<2-3 sentence analysis>\"\n"
                + "}";
        try {
            String raw = callAiApi(prompt, resolveModel());
            return parseAtsJson(raw);
        } catch (Exception ex) {
            log.warn("AI ATS analysis unavailable, falling back to keyword matching: {}", ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private AtsResponse parseAtsJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            // Strip markdown code fences if present
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "").trim();
            }
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            int score = node.has("score") ? node.get("score").asInt() : -1;
            if (score < 0 || score > 100) return null;

            List<String> missing = new java.util.ArrayList<>();
            if (node.has("missingKeywords") && node.get("missingKeywords").isArray()) {
                for (var el : node.get("missingKeywords")) {
                    missing.add(el.asText());
                }
            }
            String feedback = node.has("feedback") ? node.get("feedback").asText() : "";
            return new AtsResponse(score, missing, feedback);
        } catch (Exception ex) {
            log.debug("Failed to parse AI ATS response: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Strips JSON structural characters, URLs, and common field names so keyword
     * extraction works on actual resume/JD content, not JSON syntax or URL noise.
     */
    private String stripJsonNoise(String text) {
        if (text == null) return "";
        return text
                // Remove full URLs (http/https)
                .replaceAll("https?://[^\\s]+", " ")
                // Remove domain-like patterns (e.g. careers360.com)
                .replaceAll("\\b[a-zA-Z0-9.-]+\\.(com|org|net|io|co|in|edu|gov)\\b", " ")
                // Remove JSON structural chars
                .replaceAll("[{}\\[\\]\",:]", " ")
                // Remove common JSON/data field names
                .replaceAll("\\b(sectionId|resumeId|sectionType|displayOrder|visible|aiGenerated|content|title)\\b", " ")
                .replaceAll("\\b(true|false|null)\\b", " ")
                // Remove standalone numbers
                .replaceAll("\\b\\d+\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Extracts meaningful keywords (length > 3, excludes common stop words).
     */
    private Set<String> extractMeaningfulKeywords(String text) {
        Set<String> stopWords = Set.of(
                "that", "this", "with", "from", "have", "been", "will", "your",
                "they", "them", "their", "what", "when", "where", "which", "about",
                "into", "also", "each", "more", "some", "such", "than", "then",
                "very", "just", "should", "could", "would", "make", "like", "over",
                "after", "before", "between", "through", "under", "above", "below"
        );
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9+#.]+"))
                .filter(w -> w.length() > 3)
                .filter(w -> !stopWords.contains(w))
                .collect(Collectors.toSet());
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    @Override
    public SkillSuggestionResponse suggestSkills(ContentRequest req) {
        enforceQuota(req.userId(), req.subscriptionPlan());
        String prompt = "List 10-15 key technical and soft skills for a " + req.jobTitle()
                + " role in 2025. Return one skill per line.";
        String model = resolveModel();
        String raw = callAiApi(prompt, model);
        persist(req.userId(), req.resumeId(), "SKILL_SUGGEST", prompt, raw, model);
        notifyAiCompletion(req.userId(), req.resumeId(), "SKILL_SUGGEST", req.recipientEmail());
        maybeNotifyQuota(req.userId(), req.subscriptionPlan(), req.recipientEmail());
        List<String> skills = Arrays.stream(raw.split("\n"))
                .map(s -> s.replaceAll("^[\\d.\\-*Ã¢â‚¬Â¢]+\\s*", "").trim())
                .filter(s -> !s.isBlank())
                .toList();
        return new SkillSuggestionResponse(skills);
    }

    @Override
    public String tailorResumeForJob(TailorRequest req) {
        requirePremium(req.subscriptionPlan(), "Resume tailoring");
        String prompt = "Tailor the following resume JSON for this job description.\n"
                + "Resume JSON:\n" + sanitize(req.resumeJson())
                + "\nJob Description:\n" + sanitize(req.jobDescription())
                + "\nReturn a revised resume JSON with adjusted content, keywords, and focus.";
        String model = resolveModel();
        String response = persist(req.userId(), req.resumeId(), "TAILOR", prompt,
                callAiApi(prompt, model),
                model).getAiResponse();
        notifyAiCompletion(req.userId(), req.resumeId(), "TAILOR", req.recipientEmail());
        maybeNotifyQuota(req.userId(), req.subscriptionPlan(), req.recipientEmail());
        return ensureJsonObjectResponse(response, req.resumeJson());
    }

    @Override
    public String translateResume(ContentRequest req) {
        requirePremium(req.subscriptionPlan(), "Resume translation");
        String prompt = "Translate the following resume content into " + defaultLang(req.language())
                + " while preserving professional tone and all section structure.\n\n"
                + sanitize(req.sectionContent());
        String model = resolveModel();
        String response = persist(req.userId(), req.resumeId(), "TRANSLATE", prompt,
                callAiApi(prompt, model),
                model).getAiResponse();
        notifyAiCompletion(req.userId(), req.resumeId(), "TRANSLATE", req.recipientEmail());
        maybeNotifyQuota(req.userId(), req.subscriptionPlan(), req.recipientEmail());
        return response;
    }

    // History & Quota
    @Override
    public List<AiRequest> getAiHistory(Long userId) {
        // Use indexed JPA query instead of findAll() + stream filtering
        return aiRequestRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public QuotaResponse getRemainingQuota(Long userId, String subscriptionPlan) {
        String month = currentMonth();
        long aiUsed  = countAiCalls(userId, month);
        // ATS count is persisted in DB Ã¢â‚¬â€ use JPA instead of in-memory counter
        long atsUsed = countAtsCalls(userId, month);
        if (PLAN_PREMIUM.equalsIgnoreCase(subscriptionPlan)) {
            return new QuotaResponse(aiUsed, atsUsed, Long.MAX_VALUE, Long.MAX_VALUE, PLAN_PREMIUM, month);
        }
        return new QuotaResponse(
                aiUsed, atsUsed,
                Math.max(0, FREE_AI_CALLS_LIMIT  - aiUsed),
                Math.max(0, FREE_ATS_CHECKS_LIMIT - atsUsed),
                "FREE", month);
    }

    // Scheduled tasks

    /**
     * Monthly quota resets automatically because countAiCalls and countAtsCalls
     * query by YearMonth window from the DB. No in-memory cleanup needed.
     * This scheduled job is kept as a no-op logging heartbeat.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void logMonthlyQuotaReset() {
        log.info("New quota month started: {}", currentMonth());
    }

    // Private helpers

    private void enforceQuota(Long userId, String plan) {
        if (PLAN_PREMIUM.equalsIgnoreCase(plan)) return;
        long used = countAiCalls(userId, currentMonth());
        if (used >= FREE_AI_CALLS_LIMIT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Free tier AI quota exhausted (" + FREE_AI_CALLS_LIMIT + "/month). Upgrade to Premium.");
        }
    }

    private void enforceAtsQuota(Long userId, String plan) {
        if (PLAN_PREMIUM.equalsIgnoreCase(plan)) return;
        // Count is read from DB Ã¢â‚¬â€ persists across restarts
        long used = countAtsCalls(userId, currentMonth());
        if (used >= FREE_ATS_CHECKS_LIMIT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Free tier ATS quota exhausted (" + FREE_ATS_CHECKS_LIMIT + "/month). Upgrade to Premium.");
        }
        // ATS check will be persisted in DB by persist() call in checkAtsCompatibility()
    }

    private void requirePremium(String plan, String feature) {
        if (!PLAN_PREMIUM.equalsIgnoreCase(plan)) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    feature + " is available for Premium subscribers only.");
        }
    }

    private long countAiCalls(Long userId, String month) {
        return countQuota(userId, month, AI_TOTAL, () -> {
            Instant startOfMonth = YearMonth.parse(month).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            return aiRequestRepository.countByUserIdAndCreatedAtAfter(userId, startOfMonth);
        });
    }

    private long countAtsCalls(Long userId, String month) {
        return countQuota(userId, month, REQUEST_ATS_CHECK, () -> {
            Instant startOfMonth = YearMonth.parse(month).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            return aiRequestRepository.countByUserIdAndRequestTypeAndCreatedAtAfter(userId, REQUEST_ATS_CHECK, startOfMonth);
        });
    }

    private String resolveModel() {
        for (String model : configuredModels()) {
            if (!unavailableModels.contains(model)) {
                if (!model.equalsIgnoreCase(primaryModel)) {
                    log.warn("Primary model {} unavailable or unconfigured, using {}", primaryModel, model);
                }
                return model;
            }
        }
        return primaryModel;
    }

    /**
     * Routes an AI prompt to the configured model.
     * - llama, mixtral, and gemma models -> Groq Chat Completions API
     * - gpt and o1 models -> legacy OpenAI Chat Completions API
     * - claude-* -> Anthropic Messages API
     * Falls back to the stub only after all configured real providers fail.
     */
    private String callAiApi(String prompt, String model) {
        try {
            return callModel(prompt, model);
        } catch (RestClientResponseException ex) {
            log.warn("AI API call failed for model={} with HTTP {}: {}. Trying configured failover models.",
                    model, ex.getStatusCode().value(), providerError(ex));
            unavailableModels.add(model);
            String failoverResponse = tryFailover(prompt, model);
            if (failoverResponse != null) {
                return failoverResponse;
            }
            if (mockEnabled) {
                return mockedAiResponse(prompt, PROVIDER_FALLBACK);
            }
            throw aiProviderException(ex);
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("AI API call failed for model={}: {}. Trying configured failover models.",
                    model, ex.getMessage());
            unavailableModels.add(model);
            String failoverResponse = tryFailover(prompt, model);
            if (failoverResponse != null) {
                return failoverResponse;
            }
            if (mockEnabled) {
                return mockedAiResponse(prompt, PROVIDER_FALLBACK);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI provider is unavailable or not configured. Check OPENAI_API_KEY, CLAUDE_API_KEY, or GROQ_API_KEY and restart ai-service.");
        }
    }

    private String tryFailover(String prompt, String failedModel) {
        for (String failoverModel : configuredModels()) {
            if (failoverModel.equalsIgnoreCase(failedModel) || unavailableModels.contains(failoverModel)) {
                continue;
            }
            try {
                log.info("Trying AI failover model={}", failoverModel);
                return callModel(prompt, failoverModel);
            } catch (RestClientResponseException ex) {
                log.warn("AI failover failed for model={} with HTTP {}: {}",
                        failoverModel, ex.getStatusCode().value(), providerError(ex));
                unavailableModels.add(failoverModel);
            } catch (RestClientException | IllegalStateException ex) {
                log.warn("AI failover failed for model={}: {}", failoverModel, ex.getMessage());
                unavailableModels.add(failoverModel);
            }
        }
        return null;
    }

    private List<String> configuredModels() {
        Set<String> models = new LinkedHashSet<>();
        addIfConfigured(models, primaryModel);
        addIfConfigured(models, secondaryModel);
        if (isConfigured(groqApiKey)) {
            addIfConfigured(models, DEFAULT_GROQ_MODEL);
        }
        if (isConfigured(openAiApiKey)) {
            addIfConfigured(models, DEFAULT_OPENAI_MODEL);
        }
        if (isConfigured(claudeApiKey)) {
            addIfConfigured(models, DEFAULT_CLAUDE_MODEL);
        }
        return new ArrayList<>(models);
    }

    private void addIfConfigured(Set<String> models, String model) {
        if (model == null || model.isBlank()) {
            return;
        }
        if (isModelProviderConfigured(model)) {
            models.add(model);
        } else {
            log.debug("Skipping AI model={} because its API key is not configured", model);
        }
    }

    private boolean isModelProviderConfigured(String model) {
        String normalized = model == null ? "" : model.toLowerCase();
        if (normalized.startsWith("claude")) {
            return isConfigured(claudeApiKey);
        }
        if (normalized.startsWith("gpt") || normalized.startsWith("o1")) {
            return isConfigured(openAiApiKey);
        }
        return isConfigured(groqApiKey);
    }

    private ResponseStatusException aiProviderException(RestClientResponseException ex) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "AI provider returned HTTP " + ex.getStatusCode().value() + ": " + providerError(ex), ex);
    }

    private String providerError(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return ex.getStatusText();
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() > 240 ? compact.substring(0, 240) + "..." : compact;
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }

    private String providerStatus(String apiKey) {
        return isConfigured(apiKey) ? STATUS_CONFIGURED : STATUS_MISSING;
    }

    private String atsFeedback(int score) {
        if (score >= 80) {
            return "Excellent ATS match. Your resume is well-aligned with the job description.";
        }
        if (score >= 50) {
            return "Good match. Consider adding the missing keywords to improve your score.";
        }
        return "Low match. Significant keywords from the job description are absent from your resume.";
    }

    private String callModel(String prompt, String model) {
        String normalized = model == null ? "" : model.toLowerCase();
        if (normalized.startsWith("claude")) {
            return callClaude(prompt, model);
        }
        if (normalized.startsWith("gpt") || normalized.startsWith("o1")) {
            return callOpenAi(prompt, model);
        }
        return callGroq(prompt, model);
    }


    /**
     * Calls Groq's OpenAI-compatible Chat Completions endpoint.
     */
    private String callGroq(String prompt, String model) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY is not configured");
        }

        var requestBody = Map.of(
            JSON_MODEL, model,
            JSON_MESSAGES, List.of(
                Map.of(JSON_ROLE, JSON_SYSTEM, JSON_CONTENT, SYSTEM_PROMPT),
                Map.of(JSON_ROLE, JSON_USER, JSON_CONTENT, prompt)
            ),
            JSON_MAX_TOKENS, 1024,
            "temperature", 0.7
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = RestClient.builder()
            .baseUrl(groqBaseUrl)
            .defaultHeader("Authorization", "Bearer " + groqApiKey)
            .defaultHeader(HTTP_CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
            .post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(Map.class);

        return extractOpenAiContent(response);
    }


    /**
     * Calls the OpenAI Chat Completions endpoint.
     * Docs: https://platform.openai.com/docs/api-reference/chat
     */
    private String callOpenAi(String prompt, String model) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured");
        }

        // Build request body
        var requestBody = Map.of(
            JSON_MODEL, model,
            JSON_MESSAGES, List.of(
                Map.of(JSON_ROLE, JSON_SYSTEM, JSON_CONTENT, SYSTEM_PROMPT),
                Map.of(JSON_ROLE, JSON_USER, JSON_CONTENT, prompt)
            ),
            JSON_MAX_TOKENS, 1024,
            "temperature", 0.7
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = RestClient.builder()
            .baseUrl(openAiBaseUrl)
            .defaultHeader("Authorization", "Bearer " + openAiApiKey)
            .defaultHeader(HTTP_CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
            .post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(Map.class);

        return extractOpenAiContent(response);
    }

    @SuppressWarnings("unchecked")
    private String extractOpenAiContent(Map<String, Object> response) {
        if (response == null) return "";
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return "";
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) return "";
        return String.valueOf(message.getOrDefault(JSON_CONTENT, "")).trim();
    }


    /**
     * Calls the Anthropic Messages endpoint.
     * Docs: https://docs.anthropic.com/en/api/messages
     */
    private String callClaude(String prompt, String model) {
        if (claudeApiKey == null || claudeApiKey.isBlank()) {
            throw new IllegalStateException("CLAUDE_API_KEY is not configured");
        }

        var requestBody = Map.of(
            JSON_MODEL, model,
            JSON_MAX_TOKENS, 1024,
            JSON_SYSTEM, SYSTEM_PROMPT,
            JSON_MESSAGES, List.of(
                Map.of(JSON_ROLE, JSON_USER, JSON_CONTENT, prompt)
            )
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = RestClient.builder()
            .baseUrl(claudeBaseUrl)
            .defaultHeader("x-api-key", claudeApiKey)
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader(HTTP_CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
            .post()
            .uri("/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(Map.class);

        return extractClaudeContent(response);
    }

    @SuppressWarnings("unchecked")
    private String extractClaudeContent(Map<String, Object> response) {
        if (response == null) return "";
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get(JSON_CONTENT);
        if (content == null || content.isEmpty()) return "";
        return String.valueOf(content.get(0).getOrDefault("text", "")).trim();
    }

    private String mockedAiResponse(String prompt, String modelLabel) {
        String shortPrompt = prompt.substring(0, Math.min(prompt.length(), 80));
        return "[" + modelLabel + " mock] Professional content generated for: \"" + shortPrompt + "\"";
    }

    private AiRequest persist(Long userId, Long resumeId,
                              String type, String prompt, String response, String model) {
        AiRequest req = new AiRequest();
        req.setRequestId(UUID.randomUUID().toString());
        req.setUserId(userId);
        req.setResumeId(resumeId);
        req.setRequestType(type);
        req.setInputPrompt(prompt);
        req.setAiResponse(response);
        req.setModel(model);
        req.setTokensUsed(estimateTokens(prompt + response));
        req.setStatus("COMPLETED");
        req.setCreatedAt(Instant.now());
        req.setCompletedAt(Instant.now());
        AiRequest saved = aiRequestRepository.save(req);
        incrementQuotaCache(userId, type, saved.getCreatedAt());
        return saved;
    }

    private String buildSummaryPrompt(ContentRequest req) {
        return "Write a concise 3-4 sentence professional summary for a " + req.jobTitle()
                + " with " + req.yearsOfExperience() + " years of experience."
                + " Key skills: " + joinSkills(req.skills())
                + ". Additional context: " + sanitize(req.sectionContent())
                + LANGUAGE_SUFFIX + defaultLang(req.language());
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .replaceAll("(?i)<\\s*script[^>]*>.*?<\\s*/\\s*script\\s*>", "")
                .replaceAll("(?i)\\b(ignore|bypass|override)\\s+(all\\s+)?(previous|prior|system|developer)\\s+(instructions|prompts)\\b", "[removed]")
                .trim();
    }

    private String ensureJsonObjectResponse(String response, String fallbackResumeJson) {
        String candidate = extractJsonObject(response);
        if (candidate != null) {
            return candidate;
        }
        String fallback = extractJsonObject(sanitize(fallbackResumeJson));
        if (fallback != null) {
            return fallback;
        }
        return "{\"sections\":[],\"tailoringStatus\":\"AI response was not valid JSON\"}";
    }

    private String extractJsonObject(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        String json = trimmed.substring(start, end + 1);
        try {
            objectMapper.readTree(json);
            return json;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String joinSkills(List<String> skills) {
        if (skills == null || skills.isEmpty()) return "general";
        return String.join(", ", skills);
    }

    private String defaultLang(String lang) {
        return (lang == null || lang.isBlank()) ? "English" : lang;
    }

    private int estimateTokens(String text) {
        return text.length() / 4; // rough approximation: 1 token Ã¢â€°Ë† 4 chars
    }

    private String currentMonth() {
        return YearMonth.now(ZoneOffset.UTC).toString(); // e.g. "2026-04"
    }

    private String yearMonthOf(Instant instant) {
        return YearMonth.from(instant.atOffset(ZoneOffset.UTC)).toString();
    }

    private long countQuota(Long userId, String month, String type, LongSupplier dbCounter) {
        StringRedisTemplate redisTemplate = redis();
        String key = quotaKey(userId, month, type);
        if (redisTemplate != null) {
            try {
                String cached = redisTemplate.opsForValue().get(key);
                if (cached != null && !cached.isBlank()) {
                    return Long.parseLong(cached);
                }
            } catch (RuntimeException ex) {
                log.debug("Redis quota lookup skipped for {}: {}", key, ex.getMessage());
            }
        }
        long count = dbCounter.getAsLong();
        cacheQuotaCount(redisTemplate, key, count);
        return count;
    }

    private void incrementQuotaCache(Long userId, String type, Instant createdAt) {
        StringRedisTemplate redisTemplate = redis();
        if (redisTemplate == null || userId == null || createdAt == null) {
            return;
        }
        String month = yearMonthOf(createdAt);
        incrementQuotaKey(redisTemplate, quotaKey(userId, month, AI_TOTAL));
        if (REQUEST_ATS_CHECK.equalsIgnoreCase(type)) {
            incrementQuotaKey(redisTemplate, quotaKey(userId, month, REQUEST_ATS_CHECK));
        }
    }

    private void incrementQuotaKey(StringRedisTemplate redisTemplate, String key) {
        try {
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, 45, TimeUnit.DAYS);
        } catch (RuntimeException ex) {
            log.debug("Redis quota increment skipped for {}: {}", key, ex.getMessage());
        }
    }

    private void cacheQuotaCount(StringRedisTemplate redisTemplate, String key, long count) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(count), 45, TimeUnit.DAYS);
        } catch (RuntimeException ex) {
            log.debug("Redis quota cache write skipped for {}: {}", key, ex.getMessage());
        }
    }

    private StringRedisTemplate redis() {
        return redisTemplateProvider.getIfAvailable();
    }

    private String quotaKey(Long userId, String month, String type) {
        return "ai:quota:" + userId + ":" + month + ":" + type;
    }

    private static ObjectProvider<AiNotificationClient> emptyNotificationProvider() {
        return new ObjectProvider<>() {
            @Override
            public AiNotificationClient getObject(Object... args) {
                return null;
            }

            @Override
            public AiNotificationClient getIfAvailable() {
                return null;
            }

            @Override
            public AiNotificationClient getIfUnique() {
                return null;
            }

            @Override
            public AiNotificationClient getObject() {
                return null;
            }
        };
    }

    private static ObjectProvider<StringRedisTemplate> emptyRedisProvider() {
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

    private void notifyAiCompletion(Long userId, Long resumeId, String operation, String email) {
        if (notificationClient == null) {
            return;
        }
        notificationClient.notifyUser(Map.of(
                NOTIFICATION_RECIPIENT_ID, userId,
                NOTIFICATION_RECIPIENT_EMAIL, email != null ? email : "",
                NOTIFICATION_TYPE, "AI_DONE",
                NOTIFICATION_TITLE, "AI content generated",
                NOTIFICATION_MESSAGE, operation + " completed successfully. Your content is ready.",
                NOTIFICATION_CHANNEL, CHANNEL_ALL,
                NOTIFICATION_RELATED_ID, String.valueOf(resumeId),
                NOTIFICATION_RELATED_TYPE, RELATED_RESUME_ID,
                NOTIFICATION_ACTION_URL, "/api/v1/web/builder/open/" + resumeId
        ));
    }

    private void notifyAtsCompletion(Long userId, Long resumeId, int score, String email) {
        if (notificationClient == null) {
            return;
        }
        notificationClient.notifyUser(Map.of(
                NOTIFICATION_RECIPIENT_ID, userId,
                NOTIFICATION_RECIPIENT_EMAIL, email != null ? email : "",
                NOTIFICATION_TYPE, "ATS_SCORE_COMPLETE",
                NOTIFICATION_TITLE, "ATS score computation complete",
                NOTIFICATION_MESSAGE, "Your ATS compatibility score is " + score + "%. Check for keyword gaps now.",
                NOTIFICATION_CHANNEL, CHANNEL_ALL,
                NOTIFICATION_RELATED_ID, String.valueOf(resumeId),
                NOTIFICATION_RELATED_TYPE, RELATED_RESUME_ID,
                NOTIFICATION_ACTION_URL, "/api/v1/web/builder/preview/" + resumeId
        ));
    }

    private void maybeNotifyQuota(Long userId, String subscriptionPlan, String email) {
        if (PLAN_PREMIUM.equalsIgnoreCase(subscriptionPlan)) {
            return;
        }
        QuotaResponse quota = getRemainingQuota(userId, subscriptionPlan);
        
        // Quota nearing limit (80% used). Free tier limits: AI=5, ATS=3.
        // AI: Used >= 4 (80% of 5)
        // ATS: Used >= 3 because 2/3 is below the 80% threshold.
        long aiUsed = 5 - quota.remainingAiCalls();
        long atsUsed = 3 - quota.remainingAtsChecks();
        
        if (aiUsed >= 4 || atsUsed >= 3) {
            if (notificationClient != null) {
                notificationClient.notifyUser(Map.of(
                        NOTIFICATION_RECIPIENT_ID, userId,
                        NOTIFICATION_RECIPIENT_EMAIL, email != null ? email : "",
                        NOTIFICATION_TYPE, "QUOTA_WARNING",
                        NOTIFICATION_TITLE, "AI quota nearing limit",
                        NOTIFICATION_MESSAGE, "You have used 80% or more of your monthly free-tier AI quota. Upgrade to Premium for unlimited calls.",
                        NOTIFICATION_CHANNEL, CHANNEL_ALL,
                        NOTIFICATION_RELATED_ID, String.valueOf(userId),
                        NOTIFICATION_RELATED_TYPE, "userId",
                        NOTIFICATION_ACTION_URL, "/billing"
                ));
            }
        }
    }
}
