package com.resumeai.jobmatch.dto;

public final class JobMatchDtos {
    private JobMatchDtos() {
    }

    public record AnalyzeJobFitRequest(Long resumeId, Long userId, String resumeText, String jobTitle,
                                       String jobDescription, String source, String recipientEmail) {
        public AnalyzeJobFitRequest(Long resumeId, Long userId, String resumeText, String jobTitle,
                                    String jobDescription, String source) {
            this(resumeId, userId, resumeText, jobTitle, jobDescription, source, null);
        }
    }

    public record JobSearchRequest(Long userId, Long resumeId, String jobTitle, String location, String recipientEmail,
                                   String resumeText) {
        public JobSearchRequest(Long userId, Long resumeId, String jobTitle, String location, String recipientEmail) {
            this(userId, resumeId, jobTitle, location, recipientEmail, null);
        }

        public JobSearchRequest(Long userId, Long resumeId, String jobTitle, String location) {
            this(userId, resumeId, jobTitle, location, null, null);
        }
    }
}
