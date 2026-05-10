package com.resumeai.export.dto;

import java.util.Map;

public final class ExportDtos {
    private ExportDtos() {
    }

    public record ExportRequest(Long resumeId, Long userId, Long templateId, String subscriptionPlan, String customizations) {
    }

    public record ExportQueueMessage(String jobId, String format, ExportRequest request) {
    }

    public record ExportStats(long totalExports, long queued, long completed, long failed,
                              Map<String, Long> byFormat) {
        public ExportStats(long totalExports, long queued, long completed, long failed) {
            this(totalExports, queued, completed, failed, Map.of());
        }
    }
}
