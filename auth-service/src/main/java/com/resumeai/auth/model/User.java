package com.resumeai.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "users",
       indexes = @Index(name = "idx_users_email", columnList = "email"))
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false)
    @JsonIgnore
    private String passwordHash;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false, length = 50)
    private String role;

    /** OAuth2 provider name or LOCAL. e.g. "GOOGLE", "GITHUB", "LINKEDIN", "LOCAL" */
    @Column(length = 50)
    private String provider;

    /** Provider-specific subject/sub identifier — null for LOCAL users. */
    @Column(length = 255)
    private String oauthId;

    /** Profile picture URL from the OAuth2 provider — null for LOCAL users. */
    @Column(length = 1024)
    private String pictureUrl;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, length = 50)
    private String subscriptionPlan;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
