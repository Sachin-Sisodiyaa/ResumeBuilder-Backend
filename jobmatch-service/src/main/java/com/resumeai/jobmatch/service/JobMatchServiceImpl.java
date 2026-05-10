package com.resumeai.jobmatch.service;

import com.resumeai.jobmatch.dto.JobMatchDtos.AnalyzeJobFitRequest;
import com.resumeai.jobmatch.dto.JobMatchDtos.JobSearchRequest;
import com.resumeai.jobmatch.model.JobMatch;
import com.resumeai.jobmatch.repository.JobMatchRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class JobMatchServiceImpl implements JobMatchService {

    private final JobMatchRepository repository;
    private final JobFitScoringService scoringService;
    private final JobScraperService jobScraperService;
    private final JobMatchNotificationClient notificationClient;
    private final ObjectProvider<JobMatchService> selfProvider;

    @Value("${app.jobsearch.retention-days:5}")
    private int retentionDays;

    @Override
    public JobMatch analyzeJobFit(AnalyzeJobFitRequest request) {
        validateAnalyzeRequest(request);
        JobMatch match = scoringService.scoreAndSave(request);
        notifyJobMatch(match, "Job fit analyzed", match.getMatchScore() + "% match for " + match.getJobTitle(), request.recipientEmail());
        return match;
    }

    @Override
    public List<JobMatch> getMatchesByResume(Long resumeId) {
        return repository.findByResumeIdOrderByMatchScoreDesc(resumeId);
    }

    @Override
    public List<JobMatch> getMatchesByUser(Long userId) {
        return repository.findByUserIdOrderByMatchedAtDesc(userId);
    }

    @Override
    public JobMatch getMatchById(String matchId) {
        return repository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job match not found"));
    }

    @Override
    public JobMatch bookmarkMatch(String matchId, boolean bookmarked) {
        JobMatch match = getMatchById(matchId);
        match.setBookmarked(bookmarked);
        return repository.save(match);
    }

    @Override
    public List<JobMatch> fetchJobsFromLinkedIn(JobSearchRequest request) {
        validateSearchRequest(request);
        List<JobMatch> matches = jobScraperService.fetchLinkedInJobs(request);
        notifySearchResults(request, matches, "LinkedIn");
        return matches;
    }

    @Override
    public List<JobMatch> fetchJobsFromNaukri(JobSearchRequest request) {
        validateSearchRequest(request);
        List<JobMatch> matches = jobScraperService.fetchNaukriJobs(request);
        notifySearchResults(request, matches, "Naukri");
        return matches;
    }

    @Override
    public String getTailoringRecommendations(String matchId) {
        return getMatchById(matchId).getRecommendations();
    }

    @Override
    public List<JobMatch> getTopMatches(Long userId) {
        return repository.findTop5ByUserIdOrderByMatchScoreDesc(userId);
    }

    @Override
    public void deleteMatch(String matchId) {
        getMatchById(matchId);
        repository.deleteById(matchId);
    }

    @Override
    @Transactional
    public long cleanupOldUnbookmarkedMatches(int olderThanDays) {
        if (olderThanDays < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "olderThanDays must be at least 1");
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(olderThanDays));
        return repository.deleteByBookmarkedFalseAndMatchedAtBefore(cutoff);
    }

    @Scheduled(cron = "${app.jobsearch.cleanup-cron:0 0 3 * * *}")
    public void cleanupExpiredSearchResults() {
        self().cleanupOldUnbookmarkedMatches(retentionDays);
    }

    private void validateAnalyzeRequest(AnalyzeJobFitRequest request) {
        if (request == null || request.userId() == null || isBlank(request.resumeText())
                || isBlank(request.jobTitle()) || isBlank(request.jobDescription())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "userId, resumeText, jobTitle, and jobDescription are required");
        }
    }

    private void validateSearchRequest(JobSearchRequest request) {
        if (request == null || request.userId() == null || isBlank(request.jobTitle())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "userId and jobTitle are required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void notifySearchResults(JobSearchRequest request, List<JobMatch> matches, String source) {
        if (matches.isEmpty()) {
            return;
        }
        JobMatch best = matches.stream()
                .max(Comparator.comparingInt(JobMatch::getMatchScore))
                .orElse(matches.get(0));
        notifyJobMatch(best, "New job matches found",
                matches.size() + " " + source + " matches found for " + request.jobTitle()
                        + ". Best score: " + best.getMatchScore() + "%.",
                request.recipientEmail());
    }

    private void notifyJobMatch(JobMatch match, String title, String message, String email) {
        notificationClient.notifyUser(Map.of(
                "recipientId", match.getUserId(),
                "recipientEmail", email != null ? email : "",
                "type", "JOB_MATCH_FOUND",
                "title", title,
                "message", message,
                "channel", "ALL",
                "relatedId", match.getMatchId(),
                "relatedType", "job-match",
                "actionUrl", "/jobs/" + match.getMatchId()
        ));
    }

    private JobMatchService self() {
        JobMatchService service = selfProvider == null ? null : selfProvider.getIfAvailable();
        return service == null ? this : service;
    }
}
