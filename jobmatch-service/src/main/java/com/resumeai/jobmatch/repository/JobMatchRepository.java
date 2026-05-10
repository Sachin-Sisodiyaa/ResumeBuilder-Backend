package com.resumeai.jobmatch.repository;

import com.resumeai.jobmatch.model.JobMatch;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobMatchRepository extends JpaRepository<JobMatch, String> {

    List<JobMatch> findByUserIdOrderByMatchedAtDesc(Long userId);

    List<JobMatch> findByResumeIdOrderByMatchScoreDesc(Long resumeId);

    List<JobMatch> findByUserIdAndBookmarkedTrueOrderByMatchedAtDesc(Long userId);

    List<JobMatch> findTop5ByUserIdOrderByMatchScoreDesc(Long userId);

    long deleteByBookmarkedFalseAndMatchedAtBefore(Instant cutoff);
}
