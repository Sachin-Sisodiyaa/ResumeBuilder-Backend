package com.resumeai.jobmatch.service;

import com.resumeai.jobmatch.dto.JobMatchDtos.AnalyzeJobFitRequest;
import com.resumeai.jobmatch.dto.JobMatchDtos.JobSearchRequest;
import com.resumeai.jobmatch.model.JobMatch;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobScraperServiceImpl implements JobScraperService {

    private final JobFitScoringService scoringService;

    @Value("${app.jobsearch.rapidapi.key:}")
    private String rapidApiKey;

    @Value("${app.jobsearch.rapidapi.host:jsearch.p.rapidapi.com}")
    private String rapidApiHost;

    @Value("${app.jobsearch.rapidapi.base-url:https://jsearch.p.rapidapi.com}")
    private String rapidApiBaseUrl;

    @Value("${app.jobsearch.results-per-page:10}")
    private int resultsPerPage;

    @Override
    public List<JobMatch> fetchLinkedInJobs(JobSearchRequest request) {
        return fetchFromJSearch(request, "LINKEDIN", "linkedin.com");
    }

    @Override
    public List<JobMatch> fetchNaukriJobs(JobSearchRequest request) {
        return fetchFromJSearch(request, "NAUKRI", "naukri.com");
    }

    @SuppressWarnings("unchecked")
    private List<JobMatch> fetchFromJSearch(JobSearchRequest request,
                                            String source, String siteHint) {
        if (rapidApiKey == null || rapidApiKey.isBlank()) {
            log.warn("RAPIDAPI_KEY not set in env — returning synthetic {} jobs for '{}' in '{}'",
                    source, request.jobTitle(), request.location());
            return createFallbackMatches(request, source, siteHint);
        }

        try {
            String query = request.jobTitle()
                    + (request.location() != null && !request.location().isBlank()
                       ? " in " + request.location() : "");

            Map<String, Object> response = RestClient.builder()
                    .baseUrl(rapidApiBaseUrl)
                    .defaultHeader("X-RapidAPI-Key", rapidApiKey)
                    .defaultHeader("X-RapidAPI-Host", rapidApiHost)
                    .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("query", query)
                            .queryParam("num_pages", "1")
                            .queryParam("page", "1")
                            .queryParam("date_posted", "week")
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("data")) {
                log.warn("JSearch returned empty response for source={}", source);
                return createFallbackMatches(request, source, siteHint);
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) {
                return createFallbackMatches(request, source, siteHint);
            }

            List<JobMatch> results = new ArrayList<>();
            for (Map<String, Object> job : data) {
                String applyLink = String.valueOf(job.getOrDefault("job_apply_link", ""));
                String publisher = String.valueOf(job.getOrDefault("job_publisher", ""));
                String siteKey   = siteHint.replace(".com", "");
                if (!applyLink.contains(siteHint) && !publisher.toLowerCase().contains(siteKey)) {
                    continue;
                }

                String title       = valueOrDefault(job.get("job_title"), request.jobTitle());
                String description = buildFullDescription(job);
                String employer    = valueOrDefault(job.get("employer_name"), "");
                String city        = valueOrDefault(job.get("job_city"), "");
                String country     = valueOrDefault(job.get("job_country"), "");
                String location    = buildLocation(city, country);

                JobMatch match = scoringService.scoreAndSave(new AnalyzeJobFitRequest(
                        request.resumeId(),
                        request.userId(),
                        safeResumeText(request),
                        title,
                        description,
                        source,
                        request.recipientEmail()
                ));

                match.setJobTitle(buildDisplayTitle(title, employer, location));
                match.setSource(source);
                match.setApplyUrl(applyLink);
                results.add(match);

                if (results.size() >= resultsPerPage) break;
            }

            if (results.isEmpty()) {
                log.info("No {} listings matched site filter '{}'; using unfiltered JSearch results as fallback", source, siteHint);
                for (Map<String, Object> job : data) {
                    String title       = valueOrDefault(job.get("job_title"), request.jobTitle());
                    String description = buildFullDescription(job);
                    String employer    = valueOrDefault(job.get("employer_name"), "");
                    String city        = valueOrDefault(job.get("job_city"), "");
                    String country     = valueOrDefault(job.get("job_country"), "");
                    String location    = buildLocation(city, country);
                    String applyLink   = String.valueOf(job.getOrDefault("job_apply_link", ""));

                    JobMatch match = scoringService.scoreAndSave(new AnalyzeJobFitRequest(
                            request.resumeId(),
                            request.userId(),
                            safeResumeText(request),
                            title,
                            description,
                            source,
                            request.recipientEmail()
                    ));

                    match.setJobTitle(buildDisplayTitle(title, employer, location));
                    match.setSource(source);
                    match.setApplyUrl(applyLink);
                    results.add(match);

                    if (results.size() >= resultsPerPage) break;
                }
            }

            if (results.isEmpty()) {
                log.info("No JSearch listings found; using synthetic fallback", source, siteHint);
                return createFallbackMatches(request, source, siteHint);
            }

            log.info("Fetched {} {} jobs for query '{}'", results.size(), source, request.jobTitle());
            return results;

        } catch (RestClientException ex) {
            log.error("JSearch API call failed completely for source={}: {}. The request probably timed out or was rejected.",
                    source, ex.getMessage(), ex);
            return createFallbackMatches(request, source, siteHint);
        }
    }

    private List<JobMatch> createFallbackMatches(JobSearchRequest request,
                                                  String source, String provider) {
        List<String> descriptions = List.of(
                request.jobTitle() + " role focused on building reliable web features, APIs, and team collaboration in "
                        + safeLocation(request.location()) + ". Review the original platform listing for company-specific requirements.",
                request.jobTitle() + " role requiring practical backend/frontend delivery, SQL, debugging, and deployment readiness.",
                "Senior " + request.jobTitle()
                        + " role centered on scalable services, clean implementation, testing, and production ownership."
        );

        String fallbackApplyUrl = providerSearchUrl(provider, request.jobTitle(), request.location());
        return descriptions.stream().map(desc -> {
            JobMatch match = scoringService.scoreAndSave(new AnalyzeJobFitRequest(
                        request.resumeId(),
                        request.userId(),
                        safeResumeText(request),
                        request.jobTitle(),
                        desc,
                        source,
                        request.recipientEmail()
                ));
            match.setApplyUrl(fallbackApplyUrl);
            return match;
        }).toList();
    }

    private String safeLocation(String location) {
        return location == null || location.isBlank() ? "remote teams" : location;
    }

    private String valueOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? fallback : text;
    }

    private String buildLocation(String city, String country) {
        if (city.isBlank()) {
            return country;
        }
        if (country.isBlank()) {
            return city;
        }
        return city + ", " + country;
    }

    private String buildDisplayTitle(String title, String employer, String location) {
        StringBuilder displayTitle = new StringBuilder(title);
        if (!employer.isBlank()) {
            displayTitle.append(" @ ").append(employer);
        }
        if (!location.isBlank()) {
            displayTitle.append(" (").append(location).append(")");
        }
        return displayTitle.toString();
    }

    private String safeResumeText(JobSearchRequest request) {
        if (request.resumeText() != null && !request.resumeText().isBlank()) {
            return request.resumeText();
        }
        return request.jobTitle() + " spring boot sql docker apis collaboration";
    }

    @SuppressWarnings("unchecked")
    private String buildFullDescription(Map<String, Object> job) {
        StringBuilder description = new StringBuilder(valueOrDefault(job.get("job_description"), ""));
        Object highlightsValue = job.get("job_highlights");
        if (highlightsValue instanceof Map<?, ?> highlights) {
            for (Map.Entry<?, ?> entry : highlights.entrySet()) {
                if (!(entry.getValue() instanceof List<?> values) || values.isEmpty()) {
                    continue;
                }
                String heading = valueOrDefault(entry.getKey(), "");
                if (!heading.isBlank()) {
                    appendSection(description, heading);
                }
                values.stream()
                        .map(value -> valueOrDefault(value, ""))
                        .filter(value -> !value.isBlank())
                        .forEach(value -> description.append("- ").append(value).append("\n"));
            }
        } else if (highlightsValue instanceof List<?> highlights) {
            appendSection(description, "Highlights");
            highlights.stream()
                    .map(value -> valueOrDefault(value, ""))
                    .filter(value -> !value.isBlank())
                    .forEach(value -> description.append("- ").append(value).append("\n"));
        }
        return description.toString().trim();
    }

    private void appendSection(StringBuilder description, String heading) {
        if (!description.isEmpty()) {
            description.append("\n\n");
        }
        description.append(heading).append("\n");
    }

    private String providerSearchUrl(String provider, String jobTitle, String location) {
        String query = encode((jobTitle == null ? "" : jobTitle)
                + (location == null || location.isBlank() ? "" : " " + location));
        if (provider.contains("linkedin")) {
            return "https://www.linkedin.com/jobs/search/?keywords=" + query;
        }
        if (provider.contains("naukri")) {
            return "https://www.naukri.com/" + query.replace("+", "-") + "-jobs";
        }
        return "";
    }

    private String encode(String value) {
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8);
    }
}
