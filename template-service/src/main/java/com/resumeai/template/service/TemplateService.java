package com.resumeai.template.service;

import com.resumeai.template.dto.TemplateDtos.TemplateRequest;
import com.resumeai.template.model.ResumeTemplate;
import java.util.List;

public interface TemplateService {
    ResumeTemplate createTemplate(TemplateRequest request);
    ResumeTemplate getTemplateById(Long templateId);
    List<ResumeTemplate> getTemplates(Boolean premium, String category, boolean activeOnly);
    ResumeTemplate updateTemplate(Long templateId, TemplateRequest request);
    ResumeTemplate deactivateTemplate(Long templateId);
    void deleteTemplate(Long templateId);
    ResumeTemplate incrementUsage(Long templateId);
    List<ResumeTemplate> getPopularTemplates();
    String renderPreview(Long templateId);
}
