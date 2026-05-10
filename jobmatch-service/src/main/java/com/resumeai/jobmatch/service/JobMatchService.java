package com.resumeai.jobmatch.service;

import com.resumeai.jobmatch.dto.JobMatchDtos.AnalyzeJobFitRequest;
import com.resumeai.jobmatch.dto.JobMatchDtos.JobSearchRequest;
import com.resumeai.jobmatch.model.JobMatch;
import java.util.List;

public interface JobMatchService {
    JobMatch analyzeJobFit(AnalyzeJobFitRequest request);
    List<JobMatch> getMatchesByResume(Long resumeId);
    List<JobMatch> getMatchesByUser(Long userId);
    JobMatch getMatchById(String matchId);
    JobMatch bookmarkMatch(String matchId, boolean bookmarked);
    List<JobMatch> fetchJobsFromLinkedIn(JobSearchRequest request);
    List<JobMatch> fetchJobsFromNaukri(JobSearchRequest request);
    String getTailoringRecommendations(String matchId);
    List<JobMatch> getTopMatches(Long userId);
    void deleteMatch(String matchId);
    long cleanupOldUnbookmarkedMatches(int olderThanDays);
}
