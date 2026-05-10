package com.resumeai.section.controller;

import com.resumeai.section.dto.SectionDtos.BulkSectionUpdateRequest;
import com.resumeai.section.dto.SectionDtos.ReorderRequest;
import com.resumeai.section.dto.SectionDtos.SectionRequest;
import com.resumeai.section.dto.SectionDtos.VisibilityRequest;
import com.resumeai.section.model.ResumeSection;
import com.resumeai.section.service.SectionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sections")
@RequiredArgsConstructor
public class SectionController {
    private final SectionService sectionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeSection create(@RequestBody SectionRequest request) {
        return sectionService.addSection(request);
    }

    @GetMapping
    public List<ResumeSection> list(@RequestParam("resumeId") Long resumeId,
                                    @RequestParam(value = "sectionType", required = false) String sectionType) {
        return sectionService.getSectionsByResume(resumeId, sectionType);
    }

    @GetMapping("/{sectionId}")
    public ResumeSection getById(@PathVariable("sectionId") Long sectionId) {
        return sectionService.getSectionById(sectionId);
    }

    @PutMapping("/{sectionId}")
    public ResumeSection update(@PathVariable("sectionId") Long sectionId, @RequestBody SectionRequest request) {
        return sectionService.updateSection(sectionId, request);
    }

    @PutMapping("/reorder/{resumeId}")
    public List<ResumeSection> reorder(@PathVariable("resumeId") Long resumeId, @RequestBody ReorderRequest request) {
        return sectionService.reorderSections(resumeId, request.orderedSectionIds());
    }

    @PutMapping("/{sectionId}/visibility")
    public ResumeSection visibility(@PathVariable("sectionId") Long sectionId, @RequestBody VisibilityRequest request) {
        return sectionService.toggleVisibility(sectionId, Boolean.TRUE.equals(request.visible()));
    }

    @PutMapping("/bulk/{resumeId}")
    public List<ResumeSection> bulk(@PathVariable("resumeId") Long resumeId, @RequestBody BulkSectionUpdateRequest request) {
        return sectionService.bulkUpdateSections(resumeId, request.sections());
    }

    @DeleteMapping("/{sectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("sectionId") Long sectionId) {
        sectionService.deleteSection(sectionId);
    }

    @DeleteMapping("/resume/{resumeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll(@PathVariable("resumeId") Long resumeId) {
        sectionService.deleteAllSections(resumeId);
    }
}
