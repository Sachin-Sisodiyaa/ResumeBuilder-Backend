package com.resumeai.web.controller;

import com.resumeai.web.model.WebOverview;
import com.resumeai.web.service.WebPortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Web portal endpoints used by the frontend dashboard and navigation views. */
@RestController
@RequestMapping("/api/v1/web")
@RequiredArgsConstructor
public class ResumeController {

    private final WebPortalService webPortalService;

    @GetMapping("/home")
    public WebOverview home() {
        return webPortalService.home();
    }

    @GetMapping("/dashboard/{userId}")
    public WebOverview dashboard(@PathVariable("userId") Long userId) {
        return webPortalService.dashboard(userId);
    }

    @GetMapping("/templates")
    public WebOverview templates(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "plan", required = false) String plan) {
        return webPortalService.templates(category, plan);
    }

    @GetMapping("/gallery")
    public WebOverview gallery(
            @RequestParam(value = "jobTitle", required = false) String jobTitle,
            @RequestParam(value = "templateCategory", required = false) String templateCategory) {
        return webPortalService.gallery(jobTitle, templateCategory);
    }

    @GetMapping("/notifications/{userId}")
    public WebOverview notifications(@PathVariable("userId") Long userId) {
        return webPortalService.notifications(userId);
    }

    @GetMapping("/notifications/{userId}/unread-count")
    public WebOverview unreadCount(@PathVariable("userId") Long userId) {
        return webPortalService.unreadNotificationCount(userId);
    }
}
