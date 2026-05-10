package com.resumeai.web.controller;

import com.resumeai.web.model.WebOverview;
import com.resumeai.web.service.WebPortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/web/builder")
@RequiredArgsConstructor
public class BuilderController {
    private final WebPortalService webPortalService;

    @GetMapping("/open/{resumeId}")
    public WebOverview open(@PathVariable("resumeId") Long resumeId) {
        return webPortalService.builder(resumeId);
    }

    @GetMapping("/preview/{resumeId}")
    public WebOverview preview(@PathVariable("resumeId") Long resumeId) {
        return webPortalService.preview(resumeId);
    }

    @GetMapping("/quota/{userId}")
    public WebOverview quota(@PathVariable("userId") Long userId) {
        return webPortalService.aiQuota(userId);
    }

    @GetMapping("/history/{userId}")
    public WebOverview history(@PathVariable("userId") Long userId) {
        return webPortalService.aiHistory(userId);
    }
}
