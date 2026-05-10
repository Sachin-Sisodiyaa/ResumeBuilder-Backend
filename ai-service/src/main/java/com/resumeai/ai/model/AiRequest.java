package com.resumeai.ai.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ai_requests",
       indexes = {
           @Index(name = "idx_ai_user",   columnList = "userId"),
           @Index(name = "idx_ai_type",   columnList = "requestType"),
           @Index(name = "idx_ai_created", columnList = "createdAt")
       })
@Data
public class AiRequest {

    @Id
    @Column(length = 36)
    private String requestId;

    @Column(nullable = false)
    private Long userId;

    @Column
    private Long resumeId;

    @Column(nullable = false, length = 100)
    private String requestType;

    @Column(columnDefinition = "TEXT")
    private String inputPrompt;

    @Column(columnDefinition = "LONGTEXT")
    private String aiResponse;

    @Column(length = 100)
    private String model;

    @Column(nullable = false)
    private int tokensUsed;

    @Column(length = 50)
    private String status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant completedAt;
}
