package com.resumeai.ai.repository;

import com.resumeai.ai.model.AiRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link AiRequest}.
 */
@Repository
public interface AiRequestRepository extends JpaRepository<AiRequest, String> {

    List<AiRequest> findByUserId(Long userId);

    List<AiRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AiRequest> findByUserIdAndCreatedAtAfter(Long userId, Instant since);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

    long countByUserIdAndRequestTypeAndCreatedAtAfter(Long userId, String requestType, Instant since);
}
