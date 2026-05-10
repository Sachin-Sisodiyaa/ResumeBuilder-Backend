package com.resumeai.section.repository;

import com.resumeai.section.model.ResumeSection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link ResumeSection}.
 */
@Repository
public interface SectionRepository extends JpaRepository<ResumeSection, Long> {

    List<ResumeSection> findByResumeIdOrderByDisplayOrderAsc(Long resumeId);

    List<ResumeSection> findByResumeIdAndSectionType(Long resumeId, String sectionType);

    void deleteByResumeId(Long resumeId);

    long countByResumeId(Long resumeId);
}
