package com.resumeai.export.controller;

import com.resumeai.export.dto.ExportDtos.ExportRequest;
import com.resumeai.export.dto.ExportDtos.ExportStats;
import com.resumeai.export.model.ExportJob;
import com.resumeai.export.service.ExportService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exports")
@RequiredArgsConstructor
public class ExportController {
    private final ExportService exportService;

    @PostMapping("/pdf")
    public ExportJob pdf(@RequestBody ExportRequest request) {
        return exportService.exportToPdf(request);
    }

    @PostMapping("/docx")
    public ExportJob docx(@RequestBody ExportRequest request) {
        return exportService.exportToDocx(request);
    }

    @PostMapping("/json")
    public ExportJob json(@RequestBody ExportRequest request) {
        return exportService.exportToJson(request);
    }

    @PostMapping("/download/pdf")
    public ResponseEntity<byte[]> downloadPdf(@RequestBody ExportRequest request) {
        return file(exportService.exportToPdf(request).getJobId());
    }

    @PostMapping("/download/docx")
    public ResponseEntity<byte[]> downloadDocx(@RequestBody ExportRequest request) {
        return file(exportService.exportToDocx(request).getJobId());
    }

    @PostMapping("/download/json")
    public ResponseEntity<byte[]> downloadJson(@RequestBody ExportRequest request) {
        return file(exportService.exportToJson(request).getJobId());
    }

    @GetMapping("/{jobId}")
    public ExportJob status(@PathVariable("jobId") String jobId) {
        return exportService.getJobStatus(jobId);
    }

    @GetMapping
    public List<ExportJob> byUser(@RequestParam("userId") Long userId) {
        return exportService.getExportsByUser(userId);
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<byte[]> download(@PathVariable("jobId") String jobId) {
        exportService.downloadFile(jobId);
        return file(jobId);
    }

    @GetMapping("/{jobId}/file")
    public ResponseEntity<byte[]> file(@PathVariable("jobId") String jobId) {
        ExportJob job = exportService.getJobStatus(jobId);
        if (!"COMPLETED".equals(job.getStatus())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Export job is " + job.getStatus() + ". Try again when it is COMPLETED.").getBytes());
        }
        if (job.getFileContent() == null || job.getFileContent().length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Export file is not available. Please create the export again.".getBytes());
        }
        return ResponseEntity.ok()
                .contentType(resolveContentType(job.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + job.getFileName() + "\"")
                .body(job.getFileContent());
    }

    @DeleteMapping("/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("jobId") String jobId) {
        exportService.deleteExport(jobId);
    }

    @PostMapping("/cleanup")
    public Map<String, Integer> cleanup() {
        return Map.of("deleted", exportService.cleanupExpiredExports());
    }

    @GetMapping("/stats/summary")
    public ExportStats stats() {
        return exportService.getExportStats();
    }

    private MediaType resolveContentType(String contentType) {
        return contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
    }
}
