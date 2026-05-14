package com.resumeai.ai.service;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
public class AiNotificationClient {

    private final WebClient.Builder webClientBuilder;
    private String notificationServiceUrl;

    AiNotificationClient() {
        this(WebClient.builder(), "http://notification-service");
    }

    public AiNotificationClient(
            @Qualifier("loadBalancedWebClientBuilder") WebClient.Builder webClientBuilder,
            @Value("${app.notification.base-url:http://notification-service}") String notificationServiceUrl) {
        this.notificationServiceUrl = notificationServiceUrl;
        this.webClientBuilder = webClientBuilder;
    }

    public void notifyUser(Map<String, Object> payload) {
        log.info("Dispatching notification to {}: {}", notificationServiceUrl, payload);
        try {
            webClientBuilder.clone()
                    .baseUrl(notificationServiceUrl)
                    .build()
                    .post()
                    .uri("/api/v1/notifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Notification successfully dispatched to {}", notificationServiceUrl);
        } catch (Exception ex) {
            log.warn("AI→notification dispatch failed (is notification-service running on {}?): {}",
                    notificationServiceUrl, ex.getMessage());
        }
    }
}
