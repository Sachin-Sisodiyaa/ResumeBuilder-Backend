package com.resumeai.resume.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "resumes",
       indexes = {
           @Index(name = "idx_resumes_user", columnList = "userId"),
           @Index(name = "idx_resumes_public", columnList = "isPublic")
       })
@Data
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long resumeId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 255)
    private String targetJobTitle;

    @Column
    private Long templateId;

    @Column
    private Integer atsScore;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(length = 10)
    private String language;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(nullable = false)
    private long viewCount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
