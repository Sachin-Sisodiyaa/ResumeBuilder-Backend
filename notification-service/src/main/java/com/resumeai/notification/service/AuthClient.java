package com.resumeai.notification.service;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class AuthClient {

    private final String authServiceUrl;

    public AuthClient(@Value("${app.auth.base-url:http://localhost:8081}") String authServiceUrl) {
        this.authServiceUrl = authServiceUrl;
    }

    /**
     * Fetches the user email by recipientId from auth-service.
     */
    public String fetchUserEmail(Long userId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> user = RestClient.create(authServiceUrl)
                    .get()
                    .uri("/api/v1/auth/profile/{userId}", userId)
                    .retrieve()
                    .body(Map.class);
            
            if (user != null && user.get("email") != null) {
                return (String) user.get("email");
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch email for userId {} from auth-service: {}", userId, ex.getMessage());
        }
        return null;
    }
}
