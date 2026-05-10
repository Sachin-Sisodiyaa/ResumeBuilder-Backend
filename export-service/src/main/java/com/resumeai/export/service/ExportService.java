package com.resumeai.export.service;

import com.resumeai.export.dto.ExportDtos.ExportRequest;
import com.resumeai.export.dto.ExportDtos.ExportStats;
import com.resumeai.export.model.ExportJob;
import java.util.List;

public interface ExportService {
    ExportJob exportToPdf(ExportRequest request);
    ExportJob exportToDocx(ExportRequest request);
    ExportJob exportToJson(ExportRequest request);
    ExportJob getJobStatus(String jobId);
    List<ExportJob> getExportsByUser(Long userId);
    String downloadFile(String jobId);
    void deleteExport(String jobId);
    int cleanupExpiredExports();
    ExportStats getExportStats();
}
