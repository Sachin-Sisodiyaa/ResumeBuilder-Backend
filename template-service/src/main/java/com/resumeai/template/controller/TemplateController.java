package com.resumeai.template.controller;

import com.resumeai.template.dto.TemplateDtos.TemplateRequest;
import com.resumeai.template.model.ResumeTemplate;
import com.resumeai.template.service.TemplateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {
    private final TemplateService templateService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeTemplate create(@RequestBody TemplateRequest request) {
        return templateService.createTemplate(request);
    }

    @GetMapping("/{templateId}")
    public ResumeTemplate getById(@PathVariable("templateId") Long templateId) {
        return templateService.getTemplateById(templateId);
    }

    @GetMapping(value = "/{templateId}/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String preview(@PathVariable("templateId") Long templateId) {
        return templateService.renderPreview(templateId);
    }

    @GetMapping
    public List<ResumeTemplate> list(@RequestParam(value = "premium", required = false) Boolean premium,
                                     @RequestParam(value = "category", required = false) String category,
                                     @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly) {
        return templateService.getTemplates(premium, category, activeOnly);
    }

    @GetMapping("/popular")
    public List<ResumeTemplate> popular() {
        return templateService.getPopularTemplates();
    }

    @PutMapping("/{templateId}")
    public ResumeTemplate update(@PathVariable("templateId") Long templateId, @RequestBody TemplateRequest request) {
        return templateService.updateTemplate(templateId, request);
    }

    @PutMapping("/{templateId}/deactivate")
    public ResumeTemplate deactivate(@PathVariable("templateId") Long templateId) {
        return templateService.deactivateTemplate(templateId);
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("templateId") Long templateId) {
        templateService.deleteTemplate(templateId);
    }

    @PutMapping("/{templateId}/usage")
    public ResumeTemplate usage(@PathVariable("templateId") Long templateId) {
        return templateService.incrementUsage(templateId);
    }
}
