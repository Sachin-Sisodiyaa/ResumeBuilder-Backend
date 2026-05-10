package com.resumeai.resume.service;

import com.resumeai.resume.dto.ResumeDtos.ResumeRequest;
import com.resumeai.resume.model.Resume;
import java.util.List;

public interface ResumeService {
    Resume createResume(ResumeRequest request);
    Resume getResumeById(Long resumeId);
    List<Resume> getResumes(Long userId, Long templateId, boolean publicOnly);
    Resume updateResume(Long resumeId, ResumeRequest request);
    Resume duplicateResume(Long resumeId, String subscriptionPlan);
    Resume updateAtsScore(Long resumeId, Integer atsScore);
    Resume publishResume(Long resumeId);
    Resume unpublishResume(Long resumeId);
    Resume incrementViewCount(Long resumeId);
    void deleteResume(Long resumeId);
}
