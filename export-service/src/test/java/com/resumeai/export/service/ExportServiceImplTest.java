package com.resumeai.export.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

import com.resumeai.export.dto.ExportDtos.ExportQueueMessage;
import com.resumeai.export.dto.ExportDtos.ExportRequest;
import com.resumeai.export.model.ExportJob;
import com.resumeai.export.repository.ExportRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ExportServiceImplTest {

    @Mock
    private ExportRepository exportRepository;

    @Mock
    private ResumePdfGenerator pdfGenerator;

    @Mock
    private ResumeDocxGenerator docxGenerator;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ExportServiceImpl exportService;

    @BeforeEach
    void useSynchronousModeUnlessTestEnablesAsync() {
        ReflectionTestUtils.setField(exportService, "asyncEnabled", false);
    }

    // ── PDF export (Free) ─────────────────────────────────────────────────────

    @Test
    void exportToPdfCreatesCompletedJob() throws Exception {
        when(exportRepository.countByUserIdAndFormatAndRequestedAtAfter(any(), any(), any())).thenReturn(0L);
        when(exportRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pdfGenerator.generate(any(ExportJob.class), any(ResumeDocumentData.class)))
                .thenReturn(Path.of("exports", "job-1", "resume.pdf"));
        when(fileStorageService.buildDownloadUrl(any())).thenReturn("http://localhost:8086/api/v1/exports/job-1/file");
        when(fileStorageService.fileSizeKb(any())).thenReturn(42);

        ExportJob job = exportService.exportToPdf(
                new ExportRequest(1L, 2L, 3L, "FREE", "{}"));

        assertEquals("PDF", job.getFormat());
        assertEquals("COMPLETED", job.getStatus());
        assertNotNull(job.getFileUrl());
        assertTrue(job.getFileUrl().endsWith("/file"));
        verify(exportRepository, org.mockito.Mockito.times(2)).save(any(ExportJob.class));
    }

    @Test
    void freePdfLimitBlocksAfterTenExports() {
        when(exportRepository.countByUserIdAndFormatAndRequestedAtAfter(any(), any(), any())).thenReturn(10L);

        assertThrows(ResponseStatusException.class,
                () -> exportService.exportToPdf(
                        new ExportRequest(1L, 5L, 3L, "FREE", "{}")));
    }

    // ── DOCX export (Premium only) ─────────────────────────────────────────────

    @Test
    void docxExportRequiresPremium() {
        assertThrows(ResponseStatusException.class,
                () -> exportService.exportToDocx(
                        new ExportRequest(1L, 2L, 3L, "FREE", "{}")));
    }

    @Test
    void docxExportSucceedsForPremium() throws Exception {
        when(exportRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(docxGenerator.generate(any(ExportJob.class), any(ResumeDocumentData.class)))
                .thenReturn(Path.of("exports", "job-2", "resume.docx"));
        when(fileStorageService.buildDownloadUrl(any())).thenReturn("http://localhost:8086/api/v1/exports/job-2/file");
        when(fileStorageService.fileSizeKb(any())).thenReturn(54);

        ExportJob job = exportService.exportToDocx(
                new ExportRequest(1L, 2L, 3L, "PREMIUM", "{}"));

        assertEquals("DOCX", job.getFormat());
        assertEquals("COMPLETED", job.getStatus());
        assertTrue(job.getFileUrl().endsWith("/file"));
    }

    // ── JSON export (Premium only) ─────────────────────────────────────────────

    @Test
    void jsonExportRequiresPremium() {
        assertThrows(ResponseStatusException.class,
                () -> exportService.exportToJson(
                        new ExportRequest(1L, 2L, 3L, "FREE", "{}")));
    }

    @Test
    void jsonExportSucceedsForPremium() throws Exception {
        when(exportRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fileStorageService.write(any(), any(), any())).thenReturn(Path.of("exports", "job-3", "resume.json"));
        when(fileStorageService.buildDownloadUrl(any())).thenReturn("http://localhost:8086/api/v1/exports/job-3/file");
        when(fileStorageService.fileSizeKb(any())).thenReturn(3);

        ExportJob job = exportService.exportToJson(
                new ExportRequest(1L, 2L, 3L, "PREMIUM", "{\"fullName\":\"Alice\",\"sections\":[{\"title\":\"Summary\",\"content\":\"Java\"}]}"));

        assertEquals("JSON", job.getFormat());
        assertEquals("COMPLETED", job.getStatus());
        assertEquals("application/json", job.getContentType());
    }

    @Test
    void jsonExportUsesEmptyObjectWhenCustomizationsMissing() throws Exception {
        when(exportRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fileStorageService.write(any(), any(), any())).thenReturn(Path.of("exports", "job-4", "resume.json"));
        when(fileStorageService.buildDownloadUrl(any())).thenReturn("http://localhost:8086/api/v1/exports/job-4/file");
        when(fileStorageService.fileSizeKb(any())).thenReturn(1);

        ExportJob job = exportService.exportToJson(
                new ExportRequest(1L, 2L, 3L, "PREMIUM", null));

        assertEquals("COMPLETED", job.getStatus());
        assertEquals("application/json", job.getContentType());
    }

    @Test
    void completedExportNotifiesUserWhenClientIsAvailable() throws Exception {
        ExportNotificationClient notificationClient = org.mockito.Mockito.mock(ExportNotificationClient.class);
        ExportServiceImpl notifyingService = new ExportServiceImpl(
                exportRepository, pdfGenerator, docxGenerator, fileStorageService,
                availableProvider(notificationClient), availableProvider(null));
        ReflectionTestUtils.setField(notifyingService, "asyncEnabled", false);
        when(exportRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fileStorageService.write(any(), any(), any())).thenReturn(Path.of("exports", "job-5", "resume.json"));
        when(fileStorageService.buildDownloadUrl(any())).thenReturn("http://localhost:8086/api/v1/exports/job-5/file");
        when(fileStorageService.fileSizeKb(any())).thenReturn(1);

        ExportJob job = notifyingService.exportToJson(
                new ExportRequest(1L, 2L, 3L, "PREMIUM", "{\"email\":\"user@example.com\"}"));

        assertEquals("COMPLETED", job.getStatus());
        verify(notificationClient).notifyUser(argThat(payload ->
                "user@example.com".equals(payload.get("recipientEmail"))
                        && "EXPORT_READY".equals(payload.get("type"))));
    }

    @Test
    void generatorFailureMarksJobFailed() throws Exception {
        when(exportRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pdfGenerator.generate(any(ExportJob.class), any(ResumeDocumentData.class)))
                .thenThrow(new IOException("disk full"));

        ExportJob job = exportService.exportToPdf(
                new ExportRequest(1L, 2L, 3L, "PREMIUM", "{bad-json"));

        assertEquals("FAILED", job.getStatus());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    void asyncRabbitPublishLeavesJobQueued() throws Exception {
        ExportQueuePublisher publisher = org.mockito.Mockito.mock(ExportQueuePublisher.class);
        ExportServiceImpl asyncService = new ExportServiceImpl(
                exportRepository, pdfGenerator, docxGenerator, fileStorageService,
                emptyProvider(), availableProvider(publisher));
        ReflectionTestUtils.setField(asyncService, "asyncEnabled", true);
        ReflectionTestUtils.setField(asyncService, "rabbitMqEnabled", true);
        when(exportRepository.countByUserIdAndFormatAndRequestedAtAfter(any(), any(), any())).thenReturn(0L);
        when(exportRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(publisher.publish(any(ExportQueueMessage.class))).thenReturn(true);

        ExportJob job = asyncService.exportToPdf(
                new ExportRequest(1L, 2L, 3L, "FREE", "{}"));

        assertEquals("QUEUED", job.getStatus());
        verify(publisher).publish(any(ExportQueueMessage.class));
        verify(pdfGenerator, never()).generate(any(), any());
    }

    @Test
    void asyncLocalWorkerCompletesQueuedJobWithoutRabbitMq() throws Exception {
        ExportServiceImpl asyncService = new ExportServiceImpl(
                exportRepository, pdfGenerator, docxGenerator, fileStorageService,
                emptyProvider(), availableProvider(null));
        ReflectionTestUtils.setField(asyncService, "asyncEnabled", true);
        ReflectionTestUtils.setField(asyncService, "rabbitMqEnabled", false);

        Map<String, ExportJob> jobs = new ConcurrentHashMap<>();
        when(exportRepository.countByUserIdAndFormatAndRequestedAtAfter(any(), any(), any())).thenReturn(0L);
        when(exportRepository.save(any(ExportJob.class))).thenAnswer(inv -> {
            ExportJob saved = inv.getArgument(0);
            jobs.put(saved.getJobId(), saved);
            return saved;
        });
        when(exportRepository.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(jobs.get(inv.getArgument(0))));
        when(pdfGenerator.generate(any(ExportJob.class), any(ResumeDocumentData.class)))
                .thenReturn(Path.of("exports", "async-job", "resume.pdf"));
        when(fileStorageService.buildDownloadUrl(any()))
                .thenReturn("http://localhost:8086/api/v1/exports/async-job/file");
        when(fileStorageService.fileSizeKb(any())).thenReturn(42);

        ExportJob job = asyncService.exportToPdf(
                new ExportRequest(1L, 2L, 3L, "FREE", "{}"));

        waitForTerminalStatus(job);
        assertEquals("COMPLETED", job.getStatus());
        assertNotNull(job.getFileUrl());
        verify(pdfGenerator).generate(any(ExportJob.class), any(ResumeDocumentData.class));
    }

    // ── Download ───────────────────────────────────────────────────────────────

    @Test
    void downloadFileReturnsStoredUrl() {
        ExportJob job = new ExportJob();
        job.setJobId("abc");
        job.setStatus("COMPLETED");
        job.setFileUrl("https://download.example.com/file.pdf");
        job.setExpiresAt(Instant.now().plusSeconds(3600));
        when(exportRepository.findById("abc")).thenReturn(Optional.of(job));

        String url = exportService.downloadFile("abc");
        assertEquals("https://download.example.com/file.pdf", url);
    }

    @Test
    void downloadThrowsForExpiredLink() {
        ExportJob job = new ExportJob();
        job.setJobId("expired");
        job.setStatus("COMPLETED");
        job.setFileUrl("https://s3.example.com/old.pdf");
        job.setExpiresAt(Instant.now().minusSeconds(60)); // already expired
        when(exportRepository.findById("expired")).thenReturn(Optional.of(job));

        assertThrows(ResponseStatusException.class,
                () -> exportService.downloadFile("expired"));
    }

    @Test
    void statusDownloadAndDeleteErrorPaths() {
        ExportJob queued = new ExportJob();
        queued.setJobId("queued");
        queued.setStatus("QUEUED");
        when(exportRepository.findById("queued")).thenReturn(Optional.of(queued));
        when(exportRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> exportService.getJobStatus("missing"));
        assertThrows(ResponseStatusException.class, () -> exportService.downloadFile("queued"));

        exportService.deleteExport("queued");
        verify(fileStorageService).deleteJobFiles("queued");
        verify(exportRepository).delete(queued);
    }

    // ── Stats ──────────────────────────────────────────────────────────────────

    @Test
    void exportStatsCountsCompletedJobs() {
        when(exportRepository.count()).thenReturn(1L);
        when(exportRepository.countByStatus("QUEUED")).thenReturn(0L);
        when(exportRepository.countByStatus("COMPLETED")).thenReturn(1L);
        when(exportRepository.countByStatus("FAILED")).thenReturn(0L);

        var stats = exportService.getExportStats();
        assertEquals(1, stats.completed());
        assertTrue(stats.totalExports() > 0);
    }

    // ── Cleanup ────────────────────────────────────────────────────────────────

    @Test
    void cleanupRemovesExpiredJobs() {
        ExportJob expired = new ExportJob();
        expired.setJobId("old-job");
        expired.setRequestedAt(Instant.now().minusSeconds(8 * 24 * 60 * 60));
        expired.setStatus("COMPLETED");

        ExportJob valid = new ExportJob();
        valid.setJobId("new-job");
        valid.setRequestedAt(Instant.now());
        valid.setStatus("COMPLETED");

        when(exportRepository.findByRequestedAtBefore(any())).thenReturn(List.of(expired));

        int deleted = exportService.cleanupExpiredExports();
        assertEquals(1, deleted);
        verify(fileStorageService).deleteJobFiles("old-job");
        verify(exportRepository).deleteAll(List.of(expired));
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

    private static void waitForTerminalStatus(ExportJob job) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while ("QUEUED".equals(job.getStatus()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
    }

    private static ObjectProvider<ExportNotificationClient> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public ExportNotificationClient getObject(Object... args) {
                return null;
            }

            @Override
            public ExportNotificationClient getIfAvailable() {
                return null;
            }

            @Override
            public ExportNotificationClient getIfUnique() {
                return null;
            }

            @Override
            public ExportNotificationClient getObject() {
                return null;
            }
        };
    }
}

