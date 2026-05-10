package com.resumeai.template.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "resume_templates",
       indexes = {
           @Index(name = "idx_template_category", columnList = "category"),
           @Index(name = "idx_template_active",   columnList = "active")
       })
@Data
public class ResumeTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long templateId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1024)
    private String description;

    @Column(length = 1024)
    private String thumbnailUrl;

    @Column(columnDefinition = "LONGTEXT")
    private String htmlLayout;

    @Column(columnDefinition = "LONGTEXT")
    private String cssStyles;

    @Column(length = 100)
    private String category;

    @Column(nullable = false)
    private boolean premium;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private long usageCount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
