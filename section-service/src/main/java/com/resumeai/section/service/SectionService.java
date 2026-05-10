package com.resumeai.section.service;

import com.resumeai.section.dto.SectionDtos.SectionRequest;
import com.resumeai.section.model.ResumeSection;
import java.util.List;

public interface SectionService {
    ResumeSection addSection(SectionRequest request);
    List<ResumeSection> getSectionsByResume(Long resumeId, String sectionType);
    ResumeSection getSectionById(Long sectionId);
    ResumeSection updateSection(Long sectionId, SectionRequest request);
    List<ResumeSection> reorderSections(Long resumeId, List<Long> orderedSectionIds);
    ResumeSection toggleVisibility(Long sectionId, boolean visible);
    List<ResumeSection> bulkUpdateSections(Long resumeId, List<SectionRequest> sections);
    void deleteSection(Long sectionId);
    void deleteAllSections(Long resumeId);
}
