package com.resumeai.jobmatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.resumeai.jobmatch.dto.JobMatchDtos.JobSearchRequest;
import com.resumeai.jobmatch.model.JobMatch;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JobScraperServiceImplTest {

    private JobFitScoringService scoringService;
    private JobScraperServiceImpl scraperService;

    @BeforeEach
    void setUp() {
        scoringService = org.mockito.Mockito.mock(JobFitScoringService.class);
        scraperService = new JobScraperServiceImpl(scoringService);
        ReflectionTestUtils.setField(scraperService, "rapidApiKey", "");
        ReflectionTestUtils.setField(scraperService, "rapidApiHost", "jsearch.p.rapidapi.com");
        when(scoringService.scoreAndSave(any())).thenAnswer(invocation -> {
            JobMatch match = new JobMatch();
            match.setJobTitle(invocation.getArgument(0, com.resumeai.jobmatch.dto.JobMatchDtos.AnalyzeJobFitRequest.class).jobTitle());
            match.setJobDescription(invocation.getArgument(0, com.resumeai.jobmatch.dto.JobMatchDtos.AnalyzeJobFitRequest.class).jobDescription());
            match.setSource(invocation.getArgument(0, com.resumeai.jobmatch.dto.JobMatchDtos.AnalyzeJobFitRequest.class).source());
            return match;
        });
    }

    @Test
    void fallbackLinkedInJobsAreReturnedWhenApiKeyMissing() {
        List<JobMatch> jobs = scraperService.fetchLinkedInJobs(
                new JobSearchRequest(1L, 2L, "Backend Engineer", "Bengaluru"));

        assertEquals(3, jobs.size());
        assertTrue(jobs.stream().allMatch(job -> "LINKEDIN".equals(job.getSource())));
        assertTrue(jobs.stream().allMatch(job -> job.getApplyUrl().contains("linkedin.com/jobs/search")));
    }

    @Test
    void fallbackNaukriJobsUseRemoteWhenLocationBlank() {
        List<JobMatch> jobs = scraperService.fetchNaukriJobs(
                new JobSearchRequest(1L, 2L, "Java Developer", " "));

        assertEquals(3, jobs.size());
        assertTrue(jobs.stream().allMatch(job -> "NAUKRI".equals(job.getSource())));
        assertTrue(jobs.stream().allMatch(job -> job.getApplyUrl().contains("naukri.com")));
    }

    @Test
    void fetchLinkedInJobsUsesJSearchDataAndFiltersBySite() throws Exception {
        HttpServer server = jsonServer("""
                {"data":[
                  {
                    "job_apply_link":"https://example.com/apply",
                    "job_publisher":"Other Board",
                    "job_title":"Ignored",
                    "job_description":"Ignored"
                  },
                  {
                    "job_apply_link":"https://www.linkedin.com/jobs/view/123",
                    "job_publisher":"LinkedIn",
                    "job_title":"Platform Engineer",
                    "job_description":"Java Spring Docker",
                    "job_highlights":{
                      "Qualifications":["5+ years building APIs","Strong SQL skills"],
                      "Responsibilities":["Own services in production"]
                    },
                    "employer_name":"Acme",
                    "job_city":"Pune",
                    "job_country":"India"
                  }
                ]}
                """);
        try {
            ReflectionTestUtils.setField(scraperService, "rapidApiKey", "key");
            ReflectionTestUtils.setField(scraperService, "rapidApiBaseUrl", serverUrl(server));
            ReflectionTestUtils.setField(scraperService, "resultsPerPage", 10);

            List<JobMatch> jobs = scraperService.fetchLinkedInJobs(
                    new JobSearchRequest(11L, 22L, "Platform Engineer", "Pune"));

            assertEquals(1, jobs.size());
            assertEquals("LINKEDIN", jobs.get(0).getSource());
            assertEquals("Platform Engineer @ Acme (Pune, India)", jobs.get(0).getJobTitle());
            assertEquals("https://www.linkedin.com/jobs/view/123", jobs.get(0).getApplyUrl());
            assertTrue(jobs.get(0).getJobDescription().contains("Qualifications"));
            assertTrue(jobs.get(0).getJobDescription().contains("5+ years building APIs"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchNaukriJobsFallsBackWhenJSearchReturnsNoData() throws Exception {
        HttpServer server = jsonServer("{}");
        try {
            ReflectionTestUtils.setField(scraperService, "rapidApiKey", "key");
            ReflectionTestUtils.setField(scraperService, "rapidApiBaseUrl", serverUrl(server));

            List<JobMatch> jobs = scraperService.fetchNaukriJobs(
                    new JobSearchRequest(1L, 2L, "Java Developer", "Mumbai"));

            assertEquals(3, jobs.size());
            assertTrue(jobs.stream().allMatch(job -> "NAUKRI".equals(job.getSource())));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchLinkedInJobsFallsBackWhenJSearchCallFails() throws Exception {
        HttpServer server = jsonServer("{}");
        String stoppedServerUrl = serverUrl(server);
        server.stop(0);
        ReflectionTestUtils.setField(scraperService, "rapidApiKey", "key");
        ReflectionTestUtils.setField(scraperService, "rapidApiBaseUrl", stoppedServerUrl);

        List<JobMatch> jobs = scraperService.fetchLinkedInJobs(
                new JobSearchRequest(1L, 2L, "Backend Engineer", "Remote"));

        assertEquals(3, jobs.size());
        assertTrue(jobs.stream().allMatch(job -> "LINKEDIN".equals(job.getSource())));
    }

    private static HttpServer jsonServer(String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String serverUrl(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }
}
