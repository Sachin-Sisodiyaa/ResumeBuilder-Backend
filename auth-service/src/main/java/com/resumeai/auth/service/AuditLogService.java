package com.resumeai.auth.service;

import com.resumeai.auth.model.AuditLog;
import com.resumeai.auth.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Persists and queries audit logs. */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLog recordAudit(Long actorId, String actorEmail, String action,
                                String entityType, String entityId,
                                String beforeValue, String afterValue) {
        AuditLog log = new AuditLog(
                null, actorId, actorEmail, action,
                entityType, entityId,
                beforeValue, afterValue,
                null, Instant.now());
        return auditLogRepository.save(log);
    }

    public AuditLog recordAudit(Long actorId, String actorEmail,
                                String action, String entityType, String entityId) {
        return recordAudit(actorId, actorEmail, action, entityType, entityId, null, null);
    }

    public List<AuditLog> getAll() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }

    public List<AuditLog> getByEntity(String entityType, String entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId);
    }

    public List<AuditLog> getByActor(Long actorId) {
        return auditLogRepository.findByActorIdOrderByTimestampDesc(actorId);
    }

    public List<AuditLog> getByAction(String action) {
        return auditLogRepository.findByActionIgnoreCaseOrderByTimestampDesc(action);
    }
}
