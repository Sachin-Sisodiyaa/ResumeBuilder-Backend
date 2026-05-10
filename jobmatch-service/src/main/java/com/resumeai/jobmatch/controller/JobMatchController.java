package com.resumeai.jobmatch.controller;

import com.resumeai.jobmatch.dto.JobMatchDtos.AnalyzeJobFitRequest;
import com.resumeai.jobmatch.dto.JobMatchDtos.JobSearchRequest;
import com.resumeai.jobmatch.model.JobMatch;
import com.resumeai.jobmatch.service.JobMatchService;
import com.resumeai.jobmatch.service.ResumeTextExtractor;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/job-matches")
@RequiredArgsConstructor
public class JobMatchController {
    private final JobMatchService jobMatchService;
    private final ResumeTextExtractor resumeTextExtractor;

    @PostMapping("/analyze")
    public JobMatch analyze(@RequestBody AnalyzeJobFitRequest request) {
        return jobMatchService.analyzeJobFit(request);
    }

    @GetMapping
    public List<JobMatch> list(@RequestParam(value = "resumeId", required = false) Long resumeId,
                               @RequestParam(value = "userId", required = false) Long userId) {
        if (resumeId != null) {
            return jobMatchService.getMatchesByResume(resumeId);
        }
        if (userId != null) {
            return jobMatchService.getMatchesByUser(userId);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resumeId or userId is required");
    }

    @GetMapping("/{matchId}")
    public JobMatch getById(@PathVariable("matchId") String matchId) {
        return jobMatchService.getMatchById(matchId);
    }

    @PutMapping("/{matchId}/bookmark")
    public JobMatch bookmark(@PathVariable("matchId") String matchId,
                             @RequestParam(value = "bookmarked", defaultValue = "true") boolean bookmarked) {
        return jobMatchService.bookmarkMatch(matchId, bookmarked);
    }

    @PostMapping("/fetch-linkedin")
    public List<JobMatch> fetchLinkedIn(@RequestBody JobSearchRequest request) {
        return jobMatchService.fetchJobsFromLinkedIn(request);
    }

    @PostMapping("/fetch-naukri")
    public List<JobMatch> fetchNaukri(@RequestBody JobSearchRequest request) {
        return jobMatchService.fetchJobsFromNaukri(request);
    }

    @PostMapping(value = "/fetch-linkedin/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<JobMatch> fetchLinkedInWithResumeUpload(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "resumeId", required = false) Long resumeId,
            @RequestParam("jobTitle") String jobTitle,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "recipientEmail", required = false) String recipientEmail,
            @RequestParam("file") MultipartFile file) {
        return jobMatchService.fetchJobsFromLinkedIn(new JobSearchRequest(
                userId, resumeId, jobTitle, location, recipientEmail, resumeTextExtractor.extract(file)));
    }

    @PostMapping(value = "/fetch-naukri/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<JobMatch> fetchNaukriWithResumeUpload(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "resumeId", required = false) Long resumeId,
            @RequestParam("jobTitle") String jobTitle,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "recipientEmail", required = false) String recipientEmail,
            @RequestParam("file") MultipartFile file) {
        return jobMatchService.fetchJobsFromNaukri(new JobSearchRequest(
                userId, resumeId, jobTitle, location, recipientEmail, resumeTextExtractor.extract(file)));
    }

    @PostMapping(value = "/extract-resume-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> extractResumeText(@RequestParam("file") MultipartFile file) {
        return Map.of("text", resumeTextExtractor.extract(file));
    }

    @GetMapping("/{matchId}/recommendations")
    public Map<String, String> recommendations(@PathVariable("matchId") String matchId) {
        return Map.of("recommendations", jobMatchService.getTailoringRecommendations(matchId));
    }

    @GetMapping("/top/{userId}")
    public List<JobMatch> top(@PathVariable("userId") Long userId) {
        return jobMatchService.getTopMatches(userId);
    }

    @DeleteMapping("/cleanup")
    public Map<String, Object> cleanup(@RequestParam(value = "olderThanDays", defaultValue = "5") int olderThanDays) {
        long deleted = jobMatchService.cleanupOldUnbookmarkedMatches(olderThanDays);
        return Map.of(
                "deleted", deleted,
                "olderThanDays", olderThanDays,
                "bookmarkedPreserved", true);
    }

    @DeleteMapping("/{matchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("matchId") String matchId) {
        jobMatchService.deleteMatch(matchId);
    }
}
