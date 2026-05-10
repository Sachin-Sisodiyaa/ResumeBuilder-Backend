package com.resumeai.export.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.export.dto.ExportDtos.ExportQueueMessage;
import com.resumeai.export.dto.ExportDtos.ExportRequest;
import com.resumeai.export.dto.ExportDtos.ExportStats;
import com.resumeai.export.model.ExportJob;
import com.resumeai.export.repository.ExportRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.ObjectProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Production export service.
 *
 * <p>Generates real PDF (Apache PDFBox) and DOCX (Apache POI) files and stores
 * the downloadable bytes in MySQL via
 * {@link ExportRepository}. Daily rate limits are enforced using a JPA
 * count query instead of in-memory counters.
 *
 * <p>Business rules:
 * <ul>
 *   <li>Free tier: max 10 PDF exports per calendar day</li>
 *   <li>DOCX and JSON export: Premium subscribers only</li>
 *   <li>Files expire after {@code app.export.expiry-days} days (default 7)</li>
 *   <li>Nightly cleanup at 02:00 UTC removes expired files and DB records</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String DEFAULT_RESUME_TITLE = "Resume";
    private static final String FIELD_TITLE = "title";

    private final ExportRepository    exportRepository;
    private final ResumePdfGenerator  pdfGenerator;
    private final ResumeDocxGenerator docxGenerator;
    private final FileStorageService  fileStorageService;
    private final ObjectProvider<ExportNotificationClient> notificationClientProvider;
    private final ObjectProvider<ExportQueuePublisher> exportQueuePublisherProvider;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.export.expiry-days:7}")
    private int expiryDays = 7;

    @Value("${app.export.free-pdf-daily-limit:10}")
    private int freePdfDailyLimit = 10;

    @Value("${app.export.async-enabled:true}")
    private boolean asyncEnabled = true;

    @Value("${app.export.rabbitmq-enabled:false}")
    private boolean rabbitMqEnabled;

    // ── PDF export ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ExportJob exportToPdf(ExportRequest request) {
        if ("FREE".equalsIgnoreCase(request.subscriptionPlan())) {
            enforceDailyPdfLimit(request.userId());
        }
        return submitExport(request, "PDF");
    }

    // ── DOCX export ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ExportJob exportToDocx(ExportRequest request) {
        requirePremium(request.subscriptionPlan(), "DOCX export");
        return submitExport(request, "DOCX");
    }

    // ── JSON export ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ExportJob exportToJson(ExportRequest request) {
        requirePremium(request.subscriptionPlan(), "JSON export");
        return submitExport(request, "JSON");
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    @Override
    public ExportJob getJobStatus(String jobId) {
        return exportRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Export job not found: " + jobId));
    }

    @Override
    public List<ExportJob> getExportsByUser(Long userId) {
        return exportRepository.findByUserIdOrderByRequestedAtDesc(userId);
    }

    @Override
    public String downloadFile(String jobId) {
        ExportJob job = getJobStatus(jobId);
        if (!STATUS_COMPLETED.equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Export job is not yet complete.");
        }
        if (job.getExpiresAt() != null && Instant.now().isAfter(job.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Download link has expired. Please re-export.");
        }
        return job.getFileUrl();
    }

    @Override
    public void deleteExport(String jobId) {
        ExportJob job = getJobStatus(jobId);
        fileStorageService.deleteJobFiles(jobId);
        exportRepository.delete(job);
        log.info("Export job {} and its files deleted", jobId);
    }

    // ── Scheduled cleanup ─────────────────────────────────────────────────────

    @Override
    @Scheduled(cron = "${app.export.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public int cleanupExpiredExports() {
        Instant cutoff = Instant.now().minus(expiryDays, ChronoUnit.DAYS);
        List<ExportJob> expired = exportRepository.findByRequestedAtBefore(cutoff);
        expired.forEach(job -> fileStorageService.deleteJobFiles(job.getJobId()));
        exportRepository.deleteAll(expired);
        log.info("Export cleanup: removed {} jobs older than {} days", expired.size(), expiryDays);
        return expired.size();
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Override
    public ExportStats getExportStats() {
        // Use JPA count queries instead of loading all rows
        long total     = exportRepository.count();
        long queued    = exportRepository.countByStatus("QUEUED");
        long completed = exportRepository.countByStatus(STATUS_COMPLETED);
        long failed    = exportRepository.countByStatus("FAILED");
        Map<String, Long> byFormat = Map.of(
                "PDF", exportRepository.countByFormat("PDF"),
                "DOCX", exportRepository.countByFormat("DOCX"),
                "JSON", exportRepository.countByFormat("JSON"));
        return new ExportStats(total, queued, completed, failed, byFormat);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ExportJob initJob(ExportRequest request, String format) {
        ExportJob job = new ExportJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setResumeId(request.resumeId());
        job.setUserId(request.userId());
        job.setTemplateId(request.templateId());
        job.setCustomizations(request.customizations());
        job.setFormat(format);
        job.setStatus("QUEUED");
        job.setRequestedAt(Instant.now());
        job.setExpiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS));
        return job;
    }

    private ExportJob submitExport(ExportRequest request, String format) {
        ExportJob job = exportRepository.save(initJob(request, format));
        if (asyncEnabled) {
            ExportQueueMessage message = new ExportQueueMessage(job.getJobId(), format, request);
            ExportQueuePublisher publisher = exportQueuePublisherProvider.getIfAvailable();
            if (rabbitMqEnabled && publisher != null && publisher.publish(message)) {
                return job;
            }
            runAfterCommit(() -> CompletableFuture.runAsync(() -> processQueuedExportSafely(message)));
            return job;
        }
        return processExport(job, request, format);
    }

    public ExportJob processQueuedExport(ExportQueueMessage message) {
        return processExport(getJobStatus(message.jobId()), message.request(), message.format());
    }

    private void processQueuedExportSafely(ExportQueueMessage message) {
        try {
            processQueuedExport(message);
        } catch (RuntimeException ex) {
            log.error("{} async export worker failed for job {}: {}",
                    message.format(), message.jobId(), ex.getMessage(), ex);
            exportRepository.findById(message.jobId()).ifPresent(job -> {
                job.setStatus("FAILED");
                job.setCompletedAt(Instant.now());
                exportRepository.save(job);
            });
        }
    }

    private void runAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private ExportJob processExport(ExportJob job, ExportRequest request, String format) {
        try {
            switch (format) {
                case "PDF" -> {
                    ResumeDocumentData data = buildDocumentData(request);
                    Path file = pdfGenerator.generate(job, data);
                    markCompleted(job, file, "pdf");
                }
                case "DOCX" -> {
                    ResumeDocumentData data = buildDocumentData(request);
                    Path file = docxGenerator.generate(job, data);
                    markCompleted(job, file, "docx");
                }
                case "JSON" -> {
                    String json = request.customizations() != null ? request.customizations() : "{}";
                    Path file = fileStorageService.write(job.getJobId(), "resume.json",
                            json.getBytes(StandardCharsets.UTF_8));
                    markCompleted(job, file, "json");
                }
                default -> throw new IllegalArgumentException("Unsupported export format: " + format);
            }
            sendExportReadyNotification(job);
        } catch (IOException | RuntimeException e) {
            log.error("{} generation failed for job {}: {}", format, job.getJobId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now());
            exportRepository.save(job);
        }
        return exportRepository.save(job);
    }

    private void markCompleted(ExportJob job, Path file, String extension) {
        job.setFileUrl(fileStorageService.buildDownloadUrl(job.getJobId()));
        job.setFileSizeKb(fileStorageService.fileSizeKb(file));
        job.setFileName("resume." + extension);
        job.setContentType(contentTypeFor(extension));
        try {
            byte[] bytes = Files.exists(file)
                    ? Files.readAllBytes(file)
                    : ("ResumeAI " + job.getFormat() + " export " + job.getJobId()).getBytes(StandardCharsets.UTF_8);
            job.setFileContent(bytes);
            fileStorageService.deleteJobFiles(job.getJobId());
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist export file in SQL storage", e);
        }
        job.setStatus(STATUS_COMPLETED);
        job.setCompletedAt(Instant.now());
    }

    private String contentTypeFor(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "json" -> "application/json";
            default -> "application/octet-stream";
        };
    }

    /**
     * Builds document content from the export request.
     *
     * <p>The {@code customizations} JSON field carries resume content
     * serialised by the web layer. It should contain fields like:
     * fullName, email, phone, location, targetJobTitle, and a sections array.
     * Falls back to a minimal document when JSON is absent or malformed.
     */
    private ResumeDocumentData buildDocumentData(ExportRequest request) {
        String json = request.customizations();
        if (json == null || json.isBlank()) {
            return ResumeDocumentData.minimal(DEFAULT_RESUME_TITLE, "Resume content not available.");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String fullName = textOrDefault(root, "fullName", DEFAULT_RESUME_TITLE);
            String resumeTitle = textOrDefault(root, FIELD_TITLE, fullName);
            String templateName = textOrNull(root, "templateName");
            String templateCategory = textOrNull(root, "templateCategory");
            String email    = textOrNull(root, "email");
            String phone    = textOrNull(root, "phone");
            String location = textOrNull(root, "location");
            String jobTitle = textOrNull(root, "targetJobTitle");
            String customFont = textOrNull(root, "customFont");
            String customColor = textOrNull(root, "customColor");

            List<ResumeDocumentData.Section> sections = new ArrayList<>();
            JsonNode sectionsNode = root.get("sections");
            if (sectionsNode != null && sectionsNode.isArray()) {
                for (JsonNode sn : sectionsNode) {
                    String title   = textOrDefault(sn, FIELD_TITLE, "Section");
                    String content = textOrDefault(sn, "content", "");
                    sections.add(new ResumeDocumentData.Section(title, content));
                }
            }
            // If no sections were parsed, create one from the raw JSON
            if (sections.isEmpty()) {
                sections.add(new ResumeDocumentData.Section("RESUME", json));
            }
            return ResumeDocumentData.fromRequest(
                    fullName, email, phone, location, jobTitle,
                    resumeTitle, templateName, templateCategory,
                    customFont, customColor, sections);
        } catch (Exception e) {
            log.warn("Could not parse customizations JSON, using minimal fallback: {}", e.getMessage());
            return ResumeDocumentData.minimal(DEFAULT_RESUME_TITLE, json);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        String val = textOrNull(node, field);
        return val != null ? val : defaultValue;
    }

    private void enforceDailyPdfLimit(Long userId) {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        long todayCount = exportRepository.countByUserIdAndFormatAndRequestedAtAfter(
                userId, "PDF", startOfDay);
        if (todayCount >= freePdfDailyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Free tier: max " + freePdfDailyLimit + " PDF exports per day.");
        }
    }

    private void requirePremium(String plan, String feature) {
        if (!"PREMIUM".equalsIgnoreCase(plan)) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    feature + " is available for Premium subscribers only.");
        }
    }

    private void sendExportReadyNotification(ExportJob job) {
        if (notificationClientProvider == null) {
            return;
        }
        ExportNotificationClient notificationClient = notificationClientProvider.getIfAvailable();
        if (notificationClient == null) {
            return;
        }

        String email = "";
        try {
            if (job.getCustomizations() != null && !job.getCustomizations().isBlank()) {
                JsonNode root = objectMapper.readTree(job.getCustomizations());
                if (root.has("email")) {
                    email = root.get("email").asText();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract email from export job customizations: {}", e.getMessage());
        }

        notificationClient.notifyUser(Map.of(
                "recipientId", job.getUserId(),
                "recipientEmail", email != null ? email : "",
                "type", "EXPORT_READY",
                "title", "Export file ready",
                "message", "Your " + job.getFormat() + " resume export is ready to download.",
                "channel", "ALL",
                "relatedId", job.getJobId(),
                "relatedType", "exportJobId",
                "actionUrl", "/api/v1/exports/" + job.getJobId() + "/download"
        ));
    }
}
