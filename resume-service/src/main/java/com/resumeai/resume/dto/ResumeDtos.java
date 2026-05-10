package com.resumeai.resume.dto;

public final class ResumeDtos {
    private ResumeDtos() {
    }

    public record ResumeRequest(Long userId, String title, String targetJobTitle, Long templateId, String language,
                                String subscriptionPlan) {
    }

    public record AtsScoreUpdateRequest(Integer atsScore) {
    }
}
