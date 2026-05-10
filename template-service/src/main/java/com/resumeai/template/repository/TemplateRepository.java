package com.resumeai.template.repository;

import com.resumeai.template.model.ResumeTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRepository extends JpaRepository<ResumeTemplate, Long> {

    List<ResumeTemplate> findByActiveTrueOrderByUsageCountDesc();

    List<ResumeTemplate> findByCategoryAndActiveTrue(String category);

    List<ResumeTemplate> findByPremiumAndActiveTrue(boolean premium);
}
