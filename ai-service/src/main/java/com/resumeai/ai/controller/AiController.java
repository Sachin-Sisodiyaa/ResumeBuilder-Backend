package com.resumeai.ai.controller;

import com.resumeai.ai.dto.AiDtos.AtsRequest;
import com.resumeai.ai.dto.AiDtos.AtsResponse;
import com.resumeai.ai.dto.AiDtos.ContentRequest;
import com.resumeai.ai.dto.AiDtos.QuotaResponse;
import com.resumeai.ai.dto.AiDtos.SkillSuggestionResponse;
import com.resumeai.ai.dto.AiDtos.TailorRequest;
import com.resumeai.ai.model.AiRequest;
import com.resumeai.ai.service.AiService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiService aiService;

    @PostMapping("/generate-summary")
    public String generateSummary(@RequestBody ContentRequest request) {
        return aiService.generateSummary(request);
    }

    @PostMapping("/generate-bullets")
    public List<String> generateBullets(@RequestBody ContentRequest request) {
        return aiService.generateBulletPoints(request);
    }

    @PostMapping("/generate-cover-letter")
    public String generateCoverLetter(@RequestBody ContentRequest request) {
        return aiService.generateCoverLetter(request);
    }

    @PostMapping("/improve-section")
    public String improveSection(@RequestBody ContentRequest request) {
        return aiService.improveSection(request);
    }

    @PostMapping("/check-ats")
    public AtsResponse checkAts(@RequestBody AtsRequest request) {
        return aiService.checkAtsCompatibility(request);
    }

    @PostMapping("/suggest-skills")
    public SkillSuggestionResponse suggestSkills(@RequestBody ContentRequest request) {
        return aiService.suggestSkills(request);
    }

    @PostMapping("/tailor-for-job")
    public String tailor(@RequestBody TailorRequest request) {
        return aiService.tailorResumeForJob(request);
    }

    @PostMapping("/translate")
    public String translate(@RequestBody ContentRequest request) {
        return aiService.translateResume(request);
    }

    @GetMapping("/history/{userId}")
    public List<AiRequest> history(@PathVariable("userId") Long userId) {
        return aiService.getAiHistory(userId);
    }

    @GetMapping("/quota/{userId}/{subscriptionPlan}")
    public QuotaResponse quota(@PathVariable("userId") Long userId,
                               @PathVariable("subscriptionPlan") String subscriptionPlan) {
        return aiService.getRemainingQuota(userId, subscriptionPlan);
    }
}
