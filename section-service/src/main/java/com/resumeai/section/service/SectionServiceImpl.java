package com.resumeai.section.service;

import com.resumeai.section.dto.SectionDtos.SectionRequest;
import com.resumeai.section.model.ResumeSection;
import com.resumeai.section.repository.SectionRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SectionServiceImpl implements SectionService {
    private final SectionRepository sectionRepository;
    private final ObjectProvider<SectionService> selfProvider;

    @Override
    public ResumeSection addSection(SectionRequest request) {
        ResumeSection section = new ResumeSection();
        section.setResumeId(request.resumeId());
        section.setSectionType(request.sectionType());
        section.setTitle(request.title());
        section.setContent(request.content());
        section.setDisplayOrder(request.displayOrder() == null ? nextDisplayOrder(request.resumeId()) : request.displayOrder());
        section.setVisible(request.visible() == null || request.visible());
        section.setAiGenerated(Boolean.TRUE.equals(request.aiGenerated()));
        return sectionRepository.save(section);
    }

    @Override
    public List<ResumeSection> getSectionsByResume(Long resumeId, String sectionType) {
        // Use indexed JPA queries instead of findAll() + stream filtering
        if (sectionType != null && !sectionType.isBlank()) {
            return sectionRepository.findByResumeIdAndSectionType(resumeId, sectionType);
        }
        return sectionRepository.findByResumeIdOrderByDisplayOrderAsc(resumeId);
    }

    @Override
    public ResumeSection getSectionById(Long sectionId) {
        return sectionRepository.findById(sectionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found"));
    }

    @Override
    public ResumeSection updateSection(Long sectionId, SectionRequest request) {
        ResumeSection section = getSectionById(sectionId);
        if (request.sectionType() != null) {
            section.setSectionType(request.sectionType());
        }
        if (request.title() != null) {
            section.setTitle(request.title());
        }
        if (request.content() != null) {
            section.setContent(request.content());
        }
        if (request.displayOrder() != null) {
            section.setDisplayOrder(request.displayOrder());
        }
        if (request.visible() != null) {
            section.setVisible(request.visible());
        }
        if (request.aiGenerated() != null) {
            section.setAiGenerated(request.aiGenerated());
        }
        return sectionRepository.save(section);
    }

    @Override
    public List<ResumeSection> reorderSections(Long resumeId, List<Long> orderedSectionIds) {
        List<ResumeSection> sections = getSectionsByResume(resumeId, null);
        for (int index = 0; index < orderedSectionIds.size(); index++) {
            Long id = orderedSectionIds.get(index);
            int displayOrder = index + 1;
            sections.stream()
                .filter(section -> section.getSectionId().equals(id))
                .findFirst()
                .ifPresent(section -> {
                    section.setDisplayOrder(displayOrder);
                    sectionRepository.save(section);
                });
        }
        return getSectionsByResume(resumeId, null);
    }

    @Override
    public ResumeSection toggleVisibility(Long sectionId, boolean visible) {
        ResumeSection section = getSectionById(sectionId);
        section.setVisible(visible);
        return sectionRepository.save(section);
    }

    @Override
    public List<ResumeSection> bulkUpdateSections(Long resumeId, List<SectionRequest> sections) {
        SectionService service = self();
        List<ResumeSection> saved = new ArrayList<>();
        service.deleteAllSections(resumeId);
        for (SectionRequest request : sections) {
            saved.add(service.addSection(new SectionRequest(
                resumeId,
                request.sectionType(),
                request.title(),
                request.content(),
                request.displayOrder(),
                request.visible(),
                request.aiGenerated()
            )));
        }
        return saved;
    }

    @Override
    public void deleteSection(Long sectionId) {
        getSectionById(sectionId);
        sectionRepository.deleteById(sectionId);
    }

    @Override
    @Transactional
    public void deleteAllSections(Long resumeId) {
        // Use JPA batch delete instead of per-row deletes
        sectionRepository.deleteByResumeId(resumeId);
    }

    private int nextDisplayOrder(Long resumeId) {
        // Use JPA count query instead of loading all sections
        return (int) sectionRepository.countByResumeId(resumeId) + 1;
    }

    private SectionService self() {
        SectionService service = selfProvider == null ? null : selfProvider.getIfAvailable();
        return service == null ? this : service;
    }
}
