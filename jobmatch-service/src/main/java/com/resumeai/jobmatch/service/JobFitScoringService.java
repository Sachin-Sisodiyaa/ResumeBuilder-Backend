package com.resumeai.jobmatch.service;

import com.resumeai.jobmatch.dto.JobMatchDtos.AnalyzeJobFitRequest;
import com.resumeai.jobmatch.model.JobMatch;
import com.resumeai.jobmatch.repository.JobMatchRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Shared ATS scoring + persistence logic extracted to break the circular
 * dependency between {@link JobMatchServiceImpl} and {@link JobScraperServiceImpl}.
 *
 * <p>Both services depend on this bean; neither depends on the other.
 */
@Service
@RequiredArgsConstructor
public class JobFitScoringService {

    private final JobMatchRepository repository;

    /**
     * Scores a candidate resume against a job description, persists the
     * {@link JobMatch} record, and returns it.
     */
    public JobMatch scoreAndSave(AnalyzeJobFitRequest request) {
        Set<String> resumeWords = words(request.resumeText());
        Set<String> jobWords    = words(request.jobDescription());
        List<String> missing = jobWords.stream()
                .filter(word -> !resumeWords.contains(word)).limit(8).toList();
        int score = jobWords.isEmpty() ? 0
                : Math.min(100, (int) (((jobWords.size() - missing.size()) * 100.0) / jobWords.size()));

        JobMatch match = new JobMatch();
        match.setMatchId(UUID.randomUUID().toString());
        match.setResumeId(request.resumeId() == null ? 0L : request.resumeId());
        match.setUserId(request.userId());
        match.setJobTitle(request.jobTitle());
        match.setJobDescription(request.jobDescription());
        match.setMatchScore(score);
        match.setMissingSkills(String.join(", ", missing));
        match.setRecommendations("Add quantified impact and these keywords: " + match.getMissingSkills());
        match.setSource(request.source() == null ? "MANUAL" : request.source());
        match.setMatchedAt(Instant.now());
        match.setBookmarked(false);
        return repository.save(match);
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private Set<String> words(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return List.of(text.toLowerCase().replaceAll("[^a-z0-9 ]", " ").split("\\s+"))
                .stream()
                .filter(w -> w.length() > 2)
                .collect(Collectors.toSet());
    }
}
