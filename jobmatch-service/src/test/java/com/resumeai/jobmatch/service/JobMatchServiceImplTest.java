package com.resumeai.jobmatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resumeai.jobmatch.dto.JobMatchDtos.AnalyzeJobFitRequest;
import com.resumeai.jobmatch.dto.JobMatchDtos.JobSearchRequest;
import com.resumeai.jobmatch.model.JobMatch;
import com.resumeai.jobmatch.repository.JobMatchRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for {@link JobMatchServiceImpl}.
 *
 * <p>{@link JobFitScoringService} and {@link JobScraperService} are mocked so
 * the test focuses on the orchestration logic in JobMatchServiceImpl, not the
 * scoring algorithm (covered by {@link JobFitScoringServiceTest}) or the HTTP
 * client (covered by integration tests).
 */
@ExtendWith(MockitoExtension.class)
class JobMatchServiceImplTest {

    @Mock
    private JobMatchRepository jobMatchRepository;

    @Mock
    private JobFitScoringService scoringService;

    @Mock
    private JobScraperService jobScraperService;

    @Mock
    private JobMatchNotificationClient notificationClient;

    @InjectMocks
    private JobMatchServiceImpl jobMatchService;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private JobMatch sampleMatch(String id, Long userId) {
        JobMatch m = new JobMatch();
        m.setMatchId(id != null ? id : UUID.randomUUID().toString());
        m.setUserId(userId);
        m.setResumeId(1L);
        m.setJobTitle("Backend Developer");
        m.setMatchScore(75);
        m.setMissingSkills("docker, aws");
        m.setRecommendations("Add docker and aws");
        m.setSource("MANUAL");
        m.setMatchedAt(Instant.now());
        m.setBookmarked(false);
        return m;
    }

    // ── analyzeJobFit ─────────────────────────────────────────────────────────

    @Test
    void analyzeJobFitDelegatesToScoringService() {
        AnalyzeJobFitRequest req = new AnalyzeJobFitRequest(
                1L, 2L, "java sql", "Backend Developer", "java sql docker aws", "MANUAL");
        JobMatch expected = sampleMatch("m-1", 2L);
        when(scoringService.scoreAndSave(req)).thenReturn(expected);

        JobMatch result = jobMatchService.analyzeJobFit(req);

        assertNotNull(result);
        assertEquals("m-1", result.getMatchId());
        verify(scoringService).scoreAndSave(req);
    }

    // ── bookmarkMatch ─────────────────────────────────────────────────────────

    @Test
    void bookmarkMatchUpdatesFlag() {
        JobMatch match = sampleMatch("m1", 2L);
        when(jobMatchRepository.findById("m1")).thenReturn(Optional.of(match));
        when(jobMatchRepository.save(any(JobMatch.class))).thenAnswer(inv -> inv.getArgument(0));

        JobMatch updated = jobMatchService.bookmarkMatch("m1", true);

        assertTrue(updated.isBookmarked());
        verify(jobMatchRepository).save(match);
    }

    @Test
    void bookmarkMatchCanUnbookmark() {
        JobMatch match = sampleMatch("m2", 2L);
        match.setBookmarked(true);
        when(jobMatchRepository.findById("m2")).thenReturn(Optional.of(match));
        when(jobMatchRepository.save(any(JobMatch.class))).thenAnswer(inv -> inv.getArgument(0));

        JobMatch updated = jobMatchService.bookmarkMatch("m2", false);

        assertFalse(updated.isBookmarked());
    }

    // ── getTopMatches ─────────────────────────────────────────────────────────

    @Test
    void getTopMatchesLimitsToFive() {
        List<JobMatch> all = List.of(
                sampleMatch(null, 2L), sampleMatch(null, 2L), sampleMatch(null, 2L),
                sampleMatch(null, 2L), sampleMatch(null, 2L), sampleMatch(null, 2L) // 6 total
        );
        when(jobMatchRepository.findTop5ByUserIdOrderByMatchScoreDesc(2L)).thenReturn(all.subList(0, 5));

        List<JobMatch> top = jobMatchService.getTopMatches(2L);

        assertEquals(5, top.size());
    }

    @Test
    void getTopMatchesFiltersToCorrectUser() {
        JobMatch userMatch  = sampleMatch("u1", 2L);
        JobMatch otherMatch = sampleMatch("o1", 99L);
        when(jobMatchRepository.findTop5ByUserIdOrderByMatchScoreDesc(2L)).thenReturn(List.of(userMatch));

        List<JobMatch> top = jobMatchService.getTopMatches(2L);

        assertEquals(1, top.size());
        assertEquals("u1", top.get(0).getMatchId());
    }

    // ── fetchJobsFromLinkedIn / Naukri ────────────────────────────────────────

    @Test
    void fetchJobsFromLinkedInDelegatesToScraper() {
        JobSearchRequest req = new JobSearchRequest(1L, 1L, "Java Developer", "Remote");
        List<JobMatch> scraped = List.of(sampleMatch("s1", 1L));
        when(jobScraperService.fetchLinkedInJobs(req)).thenReturn(scraped);

        List<JobMatch> result = jobMatchService.fetchJobsFromLinkedIn(req);

        assertEquals(1, result.size());
        assertEquals("s1", result.get(0).getMatchId());
        verify(jobScraperService).fetchLinkedInJobs(req);
    }

    @Test
    void fetchJobsFromNaukriDelegatesToScraper() {
        JobSearchRequest req = new JobSearchRequest(1L, 1L, "Java Developer", "Bangalore");
        List<JobMatch> scraped = List.of(sampleMatch("n1", 1L));
        when(jobScraperService.fetchNaukriJobs(req)).thenReturn(scraped);

        List<JobMatch> result = jobMatchService.fetchJobsFromNaukri(req);

        assertEquals(1, result.size());
        verify(jobScraperService).fetchNaukriJobs(req);
    }

    // ── deleteMatch ───────────────────────────────────────────────────────────

    @Test
    void deleteMatchRemovesFromRepository() {
        JobMatch match = sampleMatch("del1", 1L);
        when(jobMatchRepository.findById("del1")).thenReturn(Optional.of(match));

        jobMatchService.deleteMatch("del1");

        verify(jobMatchRepository).deleteById("del1");
    }

    @Test
    void cleanupOldUnbookmarkedMatchesDeletesOnlyBeforeCutoff() {
        when(jobMatchRepository.deleteByBookmarkedFalseAndMatchedAtBefore(any(Instant.class))).thenReturn(3L);

        long deleted = jobMatchService.cleanupOldUnbookmarkedMatches(30);

        assertEquals(3L, deleted);
        verify(jobMatchRepository).deleteByBookmarkedFalseAndMatchedAtBefore(argThat(cutoff ->
                cutoff.isBefore(Instant.now().minusSeconds(29L * 24 * 60 * 60))
                        && cutoff.isAfter(Instant.now().minusSeconds(31L * 24 * 60 * 60))));
    }

    @Test
    void cleanupOldUnbookmarkedMatchesRejectsInvalidRetention() {
        try {
            jobMatchService.cleanupOldUnbookmarkedMatches(0);
        } catch (ResponseStatusException ex) {
            assertEquals(400, ex.getStatusCode().value());
            return;
        }
        throw new AssertionError("Expected ResponseStatusException");
    }

    @Test
    void getMatchesByResumeReturnsList() {
        when(jobMatchRepository.findByResumeIdOrderByMatchScoreDesc(1L)).thenReturn(List.of(sampleMatch("r1", 2L)));
        assertEquals(1, jobMatchService.getMatchesByResume(1L).size());
    }

    @Test
    void getMatchesByUserReturnsList() {
        when(jobMatchRepository.findByUserIdOrderByMatchedAtDesc(2L)).thenReturn(List.of(sampleMatch("u1", 2L)));
        assertEquals(1, jobMatchService.getMatchesByUser(2L).size());
    }

    @Test
    void getMatchByIdThrowsNotFound() {
        when(jobMatchRepository.findById("invalid")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> jobMatchService.getMatchById("invalid"));
    }

    @Test
    void getTailoringRecommendationsReturnsString() {
        JobMatch match = sampleMatch("rec1", 2L);
        match.setRecommendations("Add more details");
        when(jobMatchRepository.findById("rec1")).thenReturn(Optional.of(match));
        assertEquals("Add more details", jobMatchService.getTailoringRecommendations("rec1"));
    }

    @Test
    void analyzeJobFitRejectsInvalidRequests() {
        assertThrows(ResponseStatusException.class, () -> jobMatchService.analyzeJobFit(null));
        AnalyzeJobFitRequest req1 = new AnalyzeJobFitRequest(1L, null, "text", "title", "desc", "src");
        assertThrows(ResponseStatusException.class, () -> jobMatchService.analyzeJobFit(req1));
        AnalyzeJobFitRequest req2 = new AnalyzeJobFitRequest(1L, 1L, "", "title", "desc", "src");
        assertThrows(ResponseStatusException.class, () -> jobMatchService.analyzeJobFit(req2));
    }

    @Test
    void fetchJobsFromLinkedInRejectsInvalidRequests() {
        assertThrows(ResponseStatusException.class, () -> jobMatchService.fetchJobsFromLinkedIn(null));
        JobSearchRequest req1 = new JobSearchRequest(null, 1L, "title", "loc");
        assertThrows(ResponseStatusException.class, () -> jobMatchService.fetchJobsFromLinkedIn(req1));
        JobSearchRequest req2 = new JobSearchRequest(1L, 1L, "", "loc");
        assertThrows(ResponseStatusException.class, () -> jobMatchService.fetchJobsFromLinkedIn(req2));
    }

    @Test
    void fetchJobsFromNaukriRejectsInvalidRequests() {
        assertThrows(ResponseStatusException.class, () -> jobMatchService.fetchJobsFromNaukri(null));
    }
}
