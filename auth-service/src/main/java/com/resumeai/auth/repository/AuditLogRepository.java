package com.resumeai.auth.repository;

import com.resumeai.auth.model.AuditLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link AuditLog}.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId);

    List<AuditLog> findByActorIdOrderByTimestampDesc(Long actorId);

    List<AuditLog> findByActionIgnoreCaseOrderByTimestampDesc(String action);

    List<AuditLog> findAllByOrderByTimestampDesc();

    List<AuditLog> findByTimestampAfter(Instant since);
}
