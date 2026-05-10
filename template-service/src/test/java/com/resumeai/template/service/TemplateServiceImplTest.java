package com.resumeai.template.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resumeai.template.dto.TemplateDtos.TemplateRequest;
import com.resumeai.template.model.ResumeTemplate;
import com.resumeai.template.repository.TemplateRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TemplateServiceImplTest {
    @Mock
    private TemplateRepository templateRepository;

    private TemplateServiceImpl templateService;

    @BeforeEach
    void setUp() {
        when(templateRepository.findAll()).thenReturn(List.of());
        when(templateRepository.save(any(ResumeTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        templateService = new TemplateServiceImpl(templateRepository);
    }

    @Test
    void createTemplatePersistsTemplate() {
        ResumeTemplate created = templateService.createTemplate(new TemplateRequest(
            "Modern", "Desc", "/img", "<html>", "css", "MODERN", true, true
        ));

        assertEquals("Modern", created.getName());
        assertEquals("MODERN", created.getCategory());
        verify(templateRepository).save(any(ResumeTemplate.class));
    }

    @Test
    void deactivateTemplateMarksTemplateInactive() {
        ResumeTemplate template = new ResumeTemplate();
        template.setTemplateId(1L);
        template.setActive(true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        ResumeTemplate updated = templateService.deactivateTemplate(1L);

        assertFalse(updated.isActive());
    }

    @Test
    void popularTemplatesSortByUsage() {
        ResumeTemplate low = new ResumeTemplate();
        low.setUsageCount(1);
        low.setActive(true);
        ResumeTemplate high = new ResumeTemplate();
        high.setUsageCount(10);
        high.setActive(true);
        when(templateRepository.findByActiveTrueOrderByUsageCountDesc()).thenReturn(List.of(high, low));

        List<ResumeTemplate> popular = templateService.getPopularTemplates();

        assertEquals(10, popular.get(0).getUsageCount());
    }

    @Test
    void renderPreviewProcessesThymeleafTemplateWithCss() {
        ResumeTemplate template = new ResumeTemplate();
        template.setTemplateId(7L);
        template.setName("Template A");
        template.setHtmlLayout("<!DOCTYPE html><html><head><title>Preview</title></head><body><h1 th:text=\"${fullName}\">Name</h1></body></html>");
        template.setCssStyles("h1{color:#0b6b5c}");
        when(templateRepository.findById(7L)).thenReturn(Optional.of(template));

        String preview = templateService.renderPreview(7L);

        assertFalse(preview.contains("th:text"));
        assertEquals(true, preview.contains("Aarav Sharma"));
        assertEquals(true, preview.contains("h1{color:#0b6b5c}"));
    }
}
