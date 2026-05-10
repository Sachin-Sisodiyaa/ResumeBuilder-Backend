package com.resumeai.jobmatch.service;

import com.resumeai.jobmatch.dto.JobMatchDtos.JobSearchRequest;
import com.resumeai.jobmatch.model.JobMatch;
import java.util.List;

/**
 * Abstracts job-board scraping so implementations can be swapped
 * (production REST calls vs stub fallback) without changing the service layer.
 */
public interface JobScraperService {

    /**
     * Fetches LinkedIn job listings matching the search criteria.
     * Production: calls RapidAPI JSearch with employment_type=FULLTIME and
     *             filters results whose source contains "linkedin".
     */
    List<JobMatch> fetchLinkedInJobs(JobSearchRequest request);

    /**
     * Fetches Naukri job listings matching the search criteria.
     * Production: calls RapidAPI JSearch with employer filter for naukri.com.
     */
    List<JobMatch> fetchNaukriJobs(JobSearchRequest request);
}
