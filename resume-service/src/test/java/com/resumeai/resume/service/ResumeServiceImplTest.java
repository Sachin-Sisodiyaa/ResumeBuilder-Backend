package com.resumeai.resume.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resumeai.resume.dto.ResumeDtos.ResumeRequest;
import com.resumeai.resume.model.Resume;
import com.resumeai.resume.repository.ResumeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ResumeServiceImplTest {
    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private TemplateCatalogClient templateCatalogClient;

    @InjectMocks
    private ResumeServiceImpl resumeService;

    @Test
    void createResumeInitializesDraftState() {
        when(resumeRepository.countByUserId(1L)).thenReturn(0L);
        when(resumeRepository.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resume created = resumeService.createResume(new ResumeRequest(1L, "My Resume", "Backend Developer", 2L, null, "FREE"));

        assertEquals("DRAFT", created.getStatus());
        assertEquals(0, created.getAtsScore());
        assertFalse(created.isPublic());
        verify(templateCatalogClient).validateUsableTemplate(2L, "FREE");
        verify(templateCatalogClient).incrementUsage(2L);
    }

    @Test
    void createResumeAllowsNoTemplate() {
        when(resumeRepository.countByUserId(1L)).thenReturn(0L);
        when(resumeRepository.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resume created = resumeService.createResume(new ResumeRequest(1L, "My Resume", "Backend Developer", null, null, "FREE"));

        assertEquals("DRAFT", created.getStatus());
        verify(templateCatalogClient).validateUsableTemplate(null, "FREE");
        verify(templateCatalogClient).incrementUsage(null);
    }

    @Test
    void duplicateResumeCreatesCopy() {
        Resume source = new Resume();
        source.setResumeId(1L);
        source.setUserId(2L);
        source.setTitle("Original");
        source.setTemplateId(3L);
        source.setLanguage("en");
        source.setAtsScore(80);
        when(resumeRepository.findById(1L)).thenReturn(Optional.of(source));
        when(resumeRepository.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resume copy = resumeService.duplicateResume(1L, "PREMIUM");

        assertEquals("Original Copy", copy.getTitle());
        assertEquals(2L, copy.getUserId());
    }

    @Test
    void getResumesFiltersPublicOnly() {
        Resume first = new Resume();
        first.setUserId(1L);
        first.setTemplateId(1L);
        first.setPublic(true);
        Resume second = new Resume();
        second.setUserId(1L);
        second.setTemplateId(1L);
        second.setPublic(false);
        when(resumeRepository.findByIsPublicTrueOrderByViewCountDesc()).thenReturn(List.of(first));

        List<Resume> results = resumeService.getResumes(1L, 1L, true);

        assertEquals(1, results.size());
        verify(resumeRepository).findByIsPublicTrueOrderByViewCountDesc();
    }

    @Test
    void getResumesSupportsUserTemplateAndAdminPaths() {
        Resume first = resume(1L, 7L);
        Resume second = resume(1L, 8L);
        when(resumeRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(first, second));
        when(resumeRepository.findAll()).thenReturn(List.of(first, second));

        assertEquals(1, resumeService.getResumes(1L, 7L, false).size());
        assertEquals(2, resumeService.getResumes(1L, null, false).size());
        assertEquals(2, resumeService.getResumes(null, null, false).size());
    }

    @Test
    void updateResumeAppliesOnlyProvidedFields() {
        Resume resume = resume(1L, 2L);
        resume.setResumeId(10L);
        resume.setTitle("Old");
        when(resumeRepository.findById(10L)).thenReturn(Optional.of(resume));
        when(resumeRepository.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resume updated = resumeService.updateResume(
                10L,
                new ResumeRequest(1L, "New", "Engineer", 5L, "hi", "PREMIUM"));

        assertEquals("New", updated.getTitle());
        assertEquals("Engineer", updated.getTargetJobTitle());
        assertEquals(5L, updated.getTemplateId());
        assertEquals("hi", updated.getLanguage());
        verify(templateCatalogClient).validateUsableTemplate(5L, "PREMIUM");
    }

    @Test
    void statusAndVisibilityOperationsUpdateResume() {
        Resume resume = resume(1L, 2L);
        resume.setResumeId(10L);
        resume.setViewCount(4);
        when(resumeRepository.findById(10L)).thenReturn(Optional.of(resume));
        when(resumeRepository.save(any(Resume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("COMPLETE", resumeService.updateAtsScore(10L, 80).getStatus());
        assertEquals(true, resumeService.publishResume(10L).isPublic());
        assertEquals(false, resumeService.unpublishResume(10L).isPublic());
        assertEquals(5, resumeService.incrementViewCount(10L).getViewCount());

        resumeService.deleteResume(10L);
        verify(resumeRepository).deleteById(10L);
    }

    @Test
    void missingOrFreeLimitThrows() {
        when(resumeRepository.findById(404L)).thenReturn(Optional.empty());
        when(resumeRepository.countByUserId(1L)).thenReturn(3L);

        assertThrows(ResponseStatusException.class, () -> resumeService.getResumeById(404L));
        assertThrows(ResponseStatusException.class, () -> resumeService.createResume(
                new ResumeRequest(1L, "Title", "Role", 1L, "en", "FREE")));
    }

    private Resume resume(Long userId, Long templateId) {
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setTemplateId(templateId);
        resume.setTitle("Title");
        resume.setTargetJobTitle("Role");
        resume.setLanguage("en");
        resume.setAtsScore(10);
        resume.setViewCount(0);
        return resume;
    }
}
