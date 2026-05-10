package com.resumeai.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility for generating and validating signed JWT tokens (HS256).
 * Access tokens embed userId, email, and role as claims.
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    /** Build a signed HS256 access token. */
    public String generateToken(Long userId, String email, String role) {
        return generateToken(userId, email, role, "FREE");
    }

    public String generateToken(Long userId, String email, String role, String subscriptionPlan) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .claim("plan", subscriptionPlan)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** Extract userId claim from a valid token; returns null on any failure. */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            log.debug("Failed to extract userId from token: {}", e.getMessage());
            return null;
        }
    }

    /** Extract email claim. */
    public String getEmailFromToken(String token) {
        try {
            return parseClaims(token).get("email", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Extract role claim. */
    public String getRoleFromToken(String token) {
        try {
            return parseClaims(token).get("role", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Extract subscription plan claim. */
    public String getPlanFromToken(String token) {
        try {
            return parseClaims(token).get("plan", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns true if the token is structurally valid and not expired. */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
