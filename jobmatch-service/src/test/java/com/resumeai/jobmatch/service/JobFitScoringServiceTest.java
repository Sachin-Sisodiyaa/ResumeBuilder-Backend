package com.resumeai.jobmatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.resumeai.jobmatch.dto.JobMatchDtos.AnalyzeJobFitRequest;
import com.resumeai.jobmatch.model.JobMatch;
import com.resumeai.jobmatch.repository.JobMatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link JobFitScoringService} — the extracted ATS scoring logic.
 */
@ExtendWith(MockitoExtension.class)
class JobFitScoringServiceTest {

    @Mock
    private JobMatchRepository repository;

    private JobFitScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new JobFitScoringService(repository);
        when(repository.save(any(JobMatch.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Score calculation ─────────────────────────────────────────────────────

    @Test
    void perfectMatchScores100() {
        JobMatch result = scoringService.scoreAndSave(new AnalyzeJobFitRequest(
                1L, 1L, "java spring docker kubernetes",
                "Developer", "java spring docker kubernetes", "MANUAL"
        ));
        assertEquals(100, result.getMatchScore());
        assertTrue(result.getMissingSkills().isBlank());
    }

    @Test
    void noOverlapScoresZero() {
        JobMatch result = scoringService.scoreAndSave(new AnalyzeJobFitRequest(
                1L, 1L, "cooking baking recipes",
                "Chef", "java spring aws docker", "MANUAL"
        ));
        assertEquals(0, result.getMatchScore());
    }

    @Test
    void partialMatchScoresBetween0And100() {
        JobMatch result = scoringService.scoreAndSave(new AnalyzeJobFitRequest(
                1L, 1L, "java sql",
                "Developer", "java sql docker aws kubernetes", "MANUAL"
        ));
        assertTrue(result.getMatchScore() > 0);
        assertTrue(result.getMatchScore() < 100);
        assertFalse(result.getMissingSkills().isBlank());
    }

    @Test
    void emptyJobDescriptionScoresZero() {
        JobMatch result = scoringService.scoreAndSave(new AnalyzeJobFitRequest(
                1L, 1L, "java spring", "Developer", "", "MANUAL"
        ));
        assertEquals(0, result.getMatchScore());
    }

    // ── Record persistence ────────────────────────────────────────────────────

    @Test
    void savedMatchHasRequiredFields() {
        JobMatch result = scoringService.scoreAndSave(new AnalyzeJobFitRequest(
                5L, 10L, "java spring boot", "Backend Developer",
                "java spring boot sql docker", "LINKEDIN"
        ));

        assertNotNull(result.getMatchId());
        assertEquals(5L, result.getResumeId());
        assertEquals(10L, result.getUserId());
        assertEquals("Backend Developer", result.getJobTitle());
        assertEquals("LINKEDIN", result.getSource());
        assertNotNull(result.getMatchedAt());
        assertFalse(result.isBookmarked());
        assertNotNull(result.getRecommendations());
    }

    @Test
    void missingSkillsLimitedToEight() {
        // Job has 10+ keywords that resume doesn't have
        String resume  = "java";
        String job     = "python scala golang rust erlang haskell clojure kotlin javascript typescript java";
        JobMatch result = scoringService.scoreAndSave(new AnalyzeJobFitRequest(
                1L, 1L, resume, "Developer", job, "MANUAL"
        ));

        long missingCount = result.getMissingSkills().isBlank() ? 0
                : result.getMissingSkills().split(",").length;
        assertTrue(missingCount <= 8, "Missing skills should be capped at 8, got: " + missingCount);
    }
}
