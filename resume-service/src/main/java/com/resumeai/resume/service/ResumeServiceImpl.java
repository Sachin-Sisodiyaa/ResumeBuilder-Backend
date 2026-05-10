package com.resumeai.resume.service;

import com.resumeai.resume.dto.ResumeDtos.ResumeRequest;
import com.resumeai.resume.model.Resume;
import com.resumeai.resume.repository.ResumeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {
    private static final int FREE_RESUME_LIMIT = 3;
    private final ResumeRepository resumeRepository;
    private final TemplateCatalogClient templateCatalogClient;

    @Override
    public Resume createResume(ResumeRequest request) {
        enforceResumeLimit(request.userId(), request.subscriptionPlan());
        templateCatalogClient.validateUsableTemplate(request.templateId(), request.subscriptionPlan());
        Resume resume = new Resume();
        resume.setUserId(request.userId());
        resume.setTitle(request.title());
        resume.setTargetJobTitle(request.targetJobTitle());
        resume.setTemplateId(request.templateId());
        resume.setLanguage(request.language() == null ? "en" : request.language());
        resume.setAtsScore(0);
        resume.setStatus("DRAFT");
        resume.setPublic(false);
        resume.setViewCount(0);
        Resume saved = resumeRepository.save(resume);
        templateCatalogClient.incrementUsage(request.templateId());
        return saved;
    }

    @Override
    public Resume getResumeById(Long resumeId) {
        return resumeRepository.findById(resumeId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found"));
    }

    @Override
    public List<Resume> getResumes(Long userId, Long templateId, boolean publicOnly) {
        // Use indexed JPA queries for the common paths
        if (publicOnly) {
            return resumeRepository.findByIsPublicTrueOrderByViewCountDesc();
        }
        if (userId != null) {
            List<Resume> resumes = resumeRepository.findByUserIdOrderByUpdatedAtDesc(userId);
            if (templateId != null) {
                return resumes.stream()
                        .filter(r -> templateId.equals(r.getTemplateId()))
                        .toList();
            }
            return resumes;
        }
        // Admin: no userId filter — load all (rare path)
        return resumeRepository.findAll();
    }

    @Override
    public Resume updateResume(Long resumeId, ResumeRequest request) {
        Resume resume = getResumeById(resumeId);
        if (request.title() != null) {
            resume.setTitle(request.title());
        }
        if (request.targetJobTitle() != null) {
            resume.setTargetJobTitle(request.targetJobTitle());
        }
        if (request.templateId() != null) {
            templateCatalogClient.validateUsableTemplate(request.templateId(), request.subscriptionPlan());
            resume.setTemplateId(request.templateId());
        }
        if (request.language() != null) {
            resume.setLanguage(request.language());
        }
        return resumeRepository.save(resume);
    }

    @Override
    public Resume duplicateResume(Long resumeId, String subscriptionPlan) {
        Resume source = getResumeById(resumeId);
        enforceResumeLimit(source.getUserId(), subscriptionPlan);
        Resume copy = new Resume();
        copy.setUserId(source.getUserId());
        copy.setTitle(source.getTitle() + " Copy");
        copy.setTargetJobTitle(source.getTargetJobTitle());
        copy.setTemplateId(source.getTemplateId());
        copy.setLanguage(source.getLanguage());
        copy.setStatus("DRAFT");
        copy.setAtsScore(source.getAtsScore());
        copy.setPublic(false);
        copy.setViewCount(0);
        return resumeRepository.save(copy);
    }

    @Override
    public Resume updateAtsScore(Long resumeId, Integer atsScore) {
        Resume resume = getResumeById(resumeId);
        resume.setAtsScore(atsScore);
        if (atsScore != null && atsScore >= 60) {
            resume.setStatus("COMPLETE");
        }
        return resumeRepository.save(resume);
    }

    @Override
    public Resume publishResume(Long resumeId) {
        Resume resume = getResumeById(resumeId);
        resume.setPublic(true);
        return resumeRepository.save(resume);
    }

    @Override
    public Resume unpublishResume(Long resumeId) {
        Resume resume = getResumeById(resumeId);
        resume.setPublic(false);
        return resumeRepository.save(resume);
    }

    @Override
    public Resume incrementViewCount(Long resumeId) {
        Resume resume = getResumeById(resumeId);
        resume.setViewCount(resume.getViewCount() + 1);
        return resumeRepository.save(resume);
    }

    @Override
    public void deleteResume(Long resumeId) {
        getResumeById(resumeId);
        resumeRepository.deleteById(resumeId);
    }

    private void enforceResumeLimit(Long userId, String subscriptionPlan) {
        if ("PREMIUM".equalsIgnoreCase(subscriptionPlan)) {
            return;
        }
        // Use JPA count query instead of loading all resumes
        long existingResumes = resumeRepository.countByUserId(userId);
        if (existingResumes >= FREE_RESUME_LIMIT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Free users can create up to 3 resumes");
        }
    }
}
