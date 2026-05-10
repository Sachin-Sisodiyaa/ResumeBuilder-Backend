package com.resumeai.section.dto;

import java.util.List;

public final class SectionDtos {
    private SectionDtos() {
    }

    public record SectionRequest(Long resumeId, String sectionType, String title, String content, Integer displayOrder,
                                 Boolean visible, Boolean aiGenerated) {
    }

    public record ReorderRequest(List<Long> orderedSectionIds) {
    }

    public record VisibilityRequest(Boolean visible) {
    }

    public record BulkSectionUpdateRequest(List<SectionRequest> sections) {
    }
}
