package com.resumeai.auth.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_logs",
       indexes = {
           @Index(name = "idx_audit_actor",  columnList = "actorId"),
           @Index(name = "idx_audit_action", columnList = "action"),
           @Index(name = "idx_audit_entity", columnList = "entityType,entityId")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long   id;

    /** userId who performed the action (null = system). */
    @Column
    private Long   actorId;

    @Column(length = 255)
    private String actorEmail;

    /** e.g. REGISTER, LOGIN, SUBSCRIPTION_CHANGE, DEACTIVATE */
    @Column(nullable = false, length = 100)
    private String action;

    /** e.g. User, Resume */
    @Column(length = 100)
    private String entityType;

    /** Affected entity's id as string */
    @Column(length = 100)
    private String entityId;

    @Column(columnDefinition = "TEXT")
    private String beforeValue;

    @Column(columnDefinition = "TEXT")
    private String afterValue;

    @Column(length = 50)
    private String ipAddress;

    @Column(nullable = false)
    private Instant timestamp;
}
