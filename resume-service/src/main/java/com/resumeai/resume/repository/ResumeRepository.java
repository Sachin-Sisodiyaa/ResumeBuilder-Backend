package com.resumeai.resume.repository;

import com.resumeai.resume.model.Resume;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Resume}.
 */
@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findByUserId(Long userId);

    List<Resume> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<Resume> findByIsPublicTrueOrderByViewCountDesc();

    long countByUserId(Long userId);
}
