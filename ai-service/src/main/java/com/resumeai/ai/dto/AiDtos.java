package com.resumeai.ai.dto;

import com.resumeai.ai.model.AiRequest;
import java.util.List;

public final class AiDtos {
    private AiDtos() {
    }

    public record ContentRequest(Long userId, Long resumeId, String subscriptionPlan, String prompt, String jobTitle,
                                 Integer yearsOfExperience, List<String> skills, String sectionContent, String language,
                                 String recipientEmail) {
        public ContentRequest(Long userId, Long resumeId, String subscriptionPlan, String prompt, String jobTitle,
                              Integer yearsOfExperience, List<String> skills, String sectionContent, String language) {
            this(userId, resumeId, subscriptionPlan, prompt, jobTitle, yearsOfExperience, skills, sectionContent,
                    language, null);
        }
    }

    public record AtsRequest(Long userId, Long resumeId, String subscriptionPlan, String resumeText, String jobDescription,
                             String recipientEmail) {
        public AtsRequest(Long userId, Long resumeId, String subscriptionPlan, String resumeText, String jobDescription) {
            this(userId, resumeId, subscriptionPlan, resumeText, jobDescription, null);
        }
    }

    public record TailorRequest(Long userId, Long resumeId, String subscriptionPlan, String resumeJson, String jobDescription,
                                String recipientEmail) {
        public TailorRequest(Long userId, Long resumeId, String subscriptionPlan, String resumeJson, String jobDescription) {
            this(userId, resumeId, subscriptionPlan, resumeJson, jobDescription, null);
        }
    }

    public record QuotaResponse(long aiCallsUsed, long atsChecksUsed, long remainingAiCalls, long remainingAtsChecks,
                                String subscriptionPlan, String month) {
    }

    public record AtsResponse(int score, List<String> missingKeywords, String feedback) {
    }

    public record SkillSuggestionResponse(List<String> skills) {
    }

    public record AiHistoryResponse(List<AiRequest> requests) {
    }
}
