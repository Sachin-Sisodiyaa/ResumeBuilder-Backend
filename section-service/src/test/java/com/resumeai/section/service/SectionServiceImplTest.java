package com.resumeai.section.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resumeai.section.dto.SectionDtos.SectionRequest;
import com.resumeai.section.model.ResumeSection;
import com.resumeai.section.repository.SectionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SectionServiceImplTest {
    @Mock
    private SectionRepository sectionRepository;

    @InjectMocks
    private SectionServiceImpl sectionService;

    @Test
    void addSectionAssignsVisibleDefaults() {
        when(sectionRepository.countByResumeId(1L)).thenReturn(0L);
        when(sectionRepository.save(any(ResumeSection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResumeSection section = sectionService.addSection(new SectionRequest(1L, "SUMMARY", "Summary", "Text", null, null, true));

        assertTrue(section.isVisible());
        assertEquals(1, section.getDisplayOrder());
    }

    @Test
    void updateSectionAppliesIncomingFields() {
        ResumeSection section = new ResumeSection();
        section.setSectionId(5L);
        section.setTitle("Old");
        when(sectionRepository.findById(5L)).thenReturn(Optional.of(section));
        when(sectionRepository.save(any(ResumeSection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResumeSection updated = sectionService.updateSection(5L, new SectionRequest(1L, "SUMMARY", "New", "Body", 2, false, false));

        assertEquals("New", updated.getTitle());
        assertEquals(2, updated.getDisplayOrder());
    }

    @Test
    void deleteAllSectionsRemovesSectionsForResume() {
        sectionService.deleteAllSections(9L);

        verify(sectionRepository).deleteByResumeId(9L);
    }

    @Test
    void getSectionsUsesSpecificQueries() {
        ResumeSection summary = section(1L, 1L, 2);
        when(sectionRepository.findByResumeIdAndSectionType(1L, "SUMMARY")).thenReturn(List.of(summary));
        when(sectionRepository.findByResumeIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(summary));

        assertEquals(1, sectionService.getSectionsByResume(1L, "SUMMARY").size());
        assertEquals(1, sectionService.getSectionsByResume(1L, null).size());
    }

    @Test
    void reorderToggleBulkAndDeletePaths() {
        ResumeSection first = section(10L, 1L, 1);
        ResumeSection second = section(11L, 1L, 2);
        when(sectionRepository.findByResumeIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(first, second), List.of(second, first));
        when(sectionRepository.findById(10L)).thenReturn(Optional.of(first));
        when(sectionRepository.save(any(ResumeSection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ResumeSection> reordered = sectionService.reorderSections(1L, List.of(11L, 10L));
        assertEquals(2, reordered.size());

        ResumeSection hidden = sectionService.toggleVisibility(10L, false);
        assertFalse(hidden.isVisible());

        List<ResumeSection> bulk = sectionService.bulkUpdateSections(1L, List.of(
                new SectionRequest(1L, "SKILLS", "Skills", "Java", 1, true, false)));
        assertEquals(1, bulk.size());

        sectionService.deleteSection(10L);
        verify(sectionRepository).deleteById(10L);
    }

    @Test
    void missingSectionThrows() {
        when(sectionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> sectionService.getSectionById(404L));
    }

    private ResumeSection section(Long id, Long resumeId, int order) {
        ResumeSection section = new ResumeSection();
        section.setSectionId(id);
        section.setResumeId(resumeId);
        section.setTitle("Title");
        section.setSectionType("SUMMARY");
        section.setDisplayOrder(order);
        section.setVisible(true);
        return section;
    }
}
