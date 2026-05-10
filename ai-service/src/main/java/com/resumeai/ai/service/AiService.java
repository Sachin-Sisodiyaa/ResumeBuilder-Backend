package com.resumeai.ai.service;

import com.resumeai.ai.dto.AiDtos.AtsRequest;
import com.resumeai.ai.dto.AiDtos.AtsResponse;
import com.resumeai.ai.dto.AiDtos.ContentRequest;
import com.resumeai.ai.dto.AiDtos.QuotaResponse;
import com.resumeai.ai.dto.AiDtos.SkillSuggestionResponse;
import com.resumeai.ai.dto.AiDtos.TailorRequest;
import com.resumeai.ai.model.AiRequest;
import java.util.List;

public interface AiService {
    String generateSummary(ContentRequest request);
    
    List<String> generateBulletPoints(ContentRequest request);
    
    String generateCoverLetter(ContentRequest request);
    
    String improveSection(ContentRequest request);
    
    AtsResponse checkAtsCompatibility(AtsRequest request);
    
    SkillSuggestionResponse suggestSkills(ContentRequest request);
    
    String tailorResumeForJob(TailorRequest request);
    
    String translateResume(ContentRequest request);
    
    List<AiRequest> getAiHistory(Long userId);
    
    QuotaResponse getRemainingQuota(Long userId, String subscriptionPlan);
}
