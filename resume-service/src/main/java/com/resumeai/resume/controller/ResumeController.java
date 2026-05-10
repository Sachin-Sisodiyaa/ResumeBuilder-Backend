package com.resumeai.resume.controller;

import com.resumeai.resume.dto.ResumeDtos.AtsScoreUpdateRequest;
import com.resumeai.resume.dto.ResumeDtos.ResumeRequest;
import com.resumeai.resume.model.Resume;
import com.resumeai.resume.service.ResumeTextExtractor;
import com.resumeai.resume.service.ResumeService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
public class ResumeController {
    private final ResumeService resumeService;
    private final ResumeTextExtractor resumeTextExtractor;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Resume create(@RequestBody ResumeRequest request,
                         @RequestHeader(value = "X-User-Id", required = false) Long authUserId,
                         @RequestHeader(value = "X-User-Plan", defaultValue = "FREE") String authPlan) {
        requireUser(authUserId);
        return resumeService.createResume(new ResumeRequest(
                authUserId,
                request.title(),
                request.targetJobTitle(),
                request.templateId(),
                request.language(),
                authPlan));
    }

    @PostMapping("/{resumeId}/duplicate")
    public Resume duplicate(@PathVariable("resumeId") Long resumeId,
                            @RequestParam(value = "subscriptionPlan", defaultValue = "FREE") String subscriptionPlan,
                            @RequestHeader(value = "X-User-Id", required = false) Long authUserId,
                            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String authRole,
                            @RequestHeader(value = "X-User-Plan", defaultValue = "FREE") String authPlan) {
        requireOwnerOrAdmin(resumeService.getResumeById(resumeId), authUserId, authRole);
        String plan = isAdmin(authRole) ? subscriptionPlan : authPlan;
        return resumeService.duplicateResume(resumeId, plan);
    }

    @GetMapping("/{resumeId}")
    public Resume getById(@PathVariable("resumeId") Long resumeId,
                          @RequestHeader(value = "X-User-Id", required = false) Long authUserId,
                          @RequestHeader(value = "X-User-Role", defaultValue = "USER") String authRole) {
        Resume resume = resumeService.getResumeById(resumeId);
        if (!resume.isPublic()) {
            requireOwnerOrAdmin(resume, authUserId, authRole);
        }
        return resume;
    }

    @GetMapping
    public List<Resume> list(@RequestParam(value = "userId", required = false) Long userId,
                             @RequestParam(value = "templateId", required = false) Long templateId,
                             @RequestParam(value = "publicOnly", defaultValue = "false") boolean publicOnly,
                             @RequestHeader(value = "X-User-Id", required = false) Long authUserId,
                             @RequestHeader(value = "X-User-Role", defaultValue = "USER") String authRole) {
        if (publicOnly) {
            return resumeService.getResumes(userId, templateId, true);
        }
        requireUser(authUserId);
        Long effectiveUserId = isAdmin(authRole) ? userId : authUserId;
        return resumeService.getResumes(effectiveUserId, templateId, false);
    }

    @PutMapping("/{resumeId}")
    public Resume update(@PathVariable("resumeId") Long resumeId,
                         @RequestBody ResumeRequest request,
                         @RequestHeader(value = "X-User-Id", required = false) Long authUserId,
                         @RequestHeader(value = "X-User-Role", defaultValue = "USER") String authRole) {
        requireOwnerOrAdmin(resumeService.getResumeById(resumeId), authUserId, authRole);
        return resumeService.updateResume(resumeId, request);
    }

    @PutMapping("/{resumeId}/publish")
    public Resume publish(@PathVariable("resumeId") Long resumeId,
                          @RequestHeader(value = "X-User-Id", required = false) Long authUserId,
                          @RequestHeader(value = "X-User-Role", defaultValue = "USER") String authRole) {
        requireOwnerOrAdmin(resumeService.getResumeById(resumeId), authUserId, authRole);
        return resumeService.publishResume(resumeId);
    }

    @PutMapping("/{resumeId}/unpublish")
    public Resume unpublish(@PathVariable("resumeId") Long resumeId,
                            @RequestHeader(value = "X-User-Id", required = false) Long authUserId,
                            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String authRole) {
        requireOwnerOrAdmin(resumeService.getResumeById(resumeId), authUserId, authRole);
        return resumeService.unpublishResume(resumeId);
    }

    @PutMapping("/{resumeId}/ats-score")
    public Resume updateAtsScore(@PathVariable("resumeId") Long resumeId,
                                 @RequestBody AtsScoreUpdateRequest request,
                                 @RequestHeader(value = "X-User-Id", required = false) Long authUserId,
                                 @RequestHeader(value = "X-User-Role", defaultValue = "USER") String authRole) {
        requireOwnerOrAdmin(resumeService.getResumeById(resumeId), authUserId, authRole);
        return resumeService.updateAtsScore(resumeId, request.atsScore());
    }

    @PutMapping("/{resumeId}/views/increment")
    public Resume incrementViewCount(@PathVariable("resumeId") Long resumeId,
                                     @RequestHeader(value = "X-User-Id", required = false) Long authUserId,
                                     @RequestHeader(value = "X-User-Role", defaultValue = "USER") String authRole) {
        Resume resume = resumeService.getResumeById(resumeId);
        if (!resume.isPublic()) {
            requireOwnerOrAdmin(resume, authUserId, authRole);
        }
        return resumeService.incrementViewCount(resumeId);
    }

    @GetMapping("/{resumeId}/share-link")
    public java.util.Map<String, String> getShareLink(
            @PathVariable("resumeId") Long resumeId,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        Resume resume = resumeService.getResumeById(resumeId);
        if (!resume.isPublic()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Resume must be published before sharing. Use the publish endpoint first.");
        }
        String baseUrl = httpRequest.getScheme() + "://" + httpRequest.getServerName()
                + (httpRequest.getServerPort() != 80 && httpRequest.getServerPort() != 443
                        ? ":" + httpRequest.getServerPort() : "");
        String shareUrl = baseUrl + "/api/v1/resumes/" + resumeId + "?public=true";
        return java.util.Map.of(
                "shareUrl", shareUrl,
                "resumeId", String.valueOf(resumeId),
                "title", resume.getTitle()
        );
    }

    @DeleteMapping("/{resumeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("resumeId") Long resumeId,
                       @RequestHeader(value = "X-User-Id", required = false) Long authUserId,
                       @RequestHeader(value = "X-User-Role", defaultValue = "USER") String authRole) {
        requireOwnerOrAdmin(resumeService.getResumeById(resumeId), authUserId, authRole);
        resumeService.deleteResume(resumeId);
    }

    @PostMapping(value = "/extract-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> extractResumeText(@RequestParam("file") MultipartFile file,
                                                  @RequestHeader(value = "X-User-Id", required = false) Long authUserId) {
        requireUser(authUserId);
        return Map.of("text", resumeTextExtractor.extract(file));
    }

    private void requireUser(Long authUserId) {
        if (authUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
    }

    private void requireOwnerOrAdmin(Resume resume, Long authUserId, String authRole) {
        requireUser(authUserId);
        if (!isAdmin(authRole) && !authUserId.equals(resume.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own resumes");
        }
    }

    private boolean isAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
