package com.resumeai.section.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "resume_sections",
       indexes = {
           @Index(name = "idx_sections_resume", columnList = "resumeId"),
           @Index(name = "idx_sections_type",   columnList = "sectionType")
       })
@Data
public class ResumeSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sectionId;

    @Column(nullable = false)
    private Long resumeId;

    @Column(nullable = false, length = 100)
    private String sectionType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean visible;

    @Column(nullable = false)
    private boolean aiGenerated;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
