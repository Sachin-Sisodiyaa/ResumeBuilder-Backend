package com.resumeai.template.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TemplateServiceImplCoverageTest {

    @Mock
    private TemplateRepository templateRepository;

    private TemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        when(templateRepository.save(any(ResumeTemplate.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new TemplateServiceImpl(templateRepository);
    }

    @Test
    void initSeedsAllDefaultTemplatesWhenRepositoryIsEmpty() {
        when(templateRepository.findAll()).thenReturn(List.of());

        service.init();

        ArgumentCaptor<ResumeTemplate> captor = ArgumentCaptor.forClass(ResumeTemplate.class);
        verify(templateRepository, org.mockito.Mockito.atLeast(19)).save(captor.capture());
        List<String> names = captor.getAllValues().stream().map(ResumeTemplate::getName).toList();
        assertTrue(names.contains("Modern ATS"));
        assertTrue(names.contains("Infographic Pro"));
    }

    @Test
    void initRefreshesExistingSeededTemplate() {
        ResumeTemplate existing = template("Classic", "OLD", true, 1);
        when(templateRepository.findAll()).thenReturn(List.of(existing));

        service.init();

        assertEquals("CLASSIC", existing.getCategory());
        assertFalse(existing.getHtmlLayout().isBlank());
        verify(templateRepository, org.mockito.Mockito.atLeastOnce()).save(existing);
    }

    @Test
    void getTemplatesUsesFastPathsAndFallbackFilters() {
        ResumeTemplate activeFree = template("A", "TECH", false, 3);
        ResumeTemplate inactivePremium = template("B", "TECH", true, 10);
        inactivePremium.setActive(false);

        when(templateRepository.findByActiveTrueOrderByUsageCountDesc()).thenReturn(List.of(activeFree));
        when(templateRepository.findByCategoryAndActiveTrue("TECH")).thenReturn(List.of(activeFree));
        when(templateRepository.findByPremiumAndActiveTrue(false)).thenReturn(List.of(activeFree));
        when(templateRepository.findAll()).thenReturn(List.of(activeFree, inactivePremium));

        assertEquals(List.of(activeFree), service.getTemplates(null, null, true));
        assertEquals(List.of(activeFree), service.getTemplates(null, "TECH", true));
        assertEquals(List.of(activeFree), service.getTemplates(false, null, true));
        assertEquals(List.of(inactivePremium), service.getTemplates(true, "TECH", false));
    }

    @Test
    void updateDeleteIncrementStatsAndPreviewCoverBranches() {
        ResumeTemplate existing = template("Preview", "OLD", false, 2);
        existing.setTemplateId(5L);
        existing.setHtmlLayout("<section>{{fullName}}</section>");
        existing.setCssStyles(null);
        when(templateRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(templateRepository.findAll()).thenReturn(List.of(existing));

        ResumeTemplate updated = service.updateTemplate(5L, new TemplateRequest(
                "Updated", "Desc", "/new", "", "body{}", "NEW", true, false));
        assertEquals("Updated", updated.getName());
        assertTrue(updated.isPremium());
        assertFalse(updated.isActive());

        assertEquals(3, service.incrementUsage(5L).getUsageCount());
        assertTrue(service.renderPreview(5L).contains("Aarav Sharma"));
        assertEquals(3L, service.getUsageStats().get("Updated"));

        service.deleteTemplate(5L);
        verify(templateRepository).deleteById(5L);
    }

    @Test
    void getTemplateByIdRejectsMissingTemplate() {
        when(templateRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.getTemplateById(404L));
    }

    private ResumeTemplate template(String name, String category, boolean premium, int usage) {
        ResumeTemplate template = new ResumeTemplate();
        template.setName(name);
        template.setDescription("Description");
        template.setThumbnailUrl("/thumb.png");
        template.setCategory(category);
        template.setPremium(premium);
        template.setActive(true);
        template.setUsageCount(usage);
        template.setHtmlLayout("<html><head></head><body>{{fullName}}</body></html>");
        template.setCssStyles("body{}");
        return template;
    }
}
