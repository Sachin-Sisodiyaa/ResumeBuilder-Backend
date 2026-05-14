package com.resumeai.payment.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class AuthSubscriptionClient {
    private final WebClient webClient;
    private final String internalServiceKey;

    public AuthSubscriptionClient(
            @Qualifier("loadBalancedWebClientBuilder") WebClient.Builder webClientBuilder,
            @Value("${app.auth.base-url:http://auth-service}") String authBaseUrl,
            @Value("${app.auth.internal-service-key}") String internalServiceKey) {
        this.webClient = webClientBuilder.clone().baseUrl(authBaseUrl).build();
        this.internalServiceKey = internalServiceKey;
    }

    public void updateSubscription(Long userId, String plan) {
        webClient.put()
                .uri("/api/v1/auth/internal/subscription/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Service-Key", internalServiceKey)
                .bodyValue(Map.of("subscriptionPlan", plan))
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
