package com.resumeai.payment.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class AuthSubscriptionClient {
    private final RestTemplate restTemplate;
    private final String authBaseUrl;
    private final String internalServiceKey;

    public AuthSubscriptionClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.auth.base-url}") String authBaseUrl,
            @Value("${app.auth.internal-service-key}") String internalServiceKey) {
        this.restTemplate = restTemplateBuilder.build();
        this.authBaseUrl = authBaseUrl;
        this.internalServiceKey = internalServiceKey;
    }

    public void updateSubscription(Long userId, String plan) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Service-Key", internalServiceKey);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("subscriptionPlan", plan), headers);
        restTemplate.put(authBaseUrl + "/api/v1/auth/internal/subscription/" + userId, entity);
    }
}
