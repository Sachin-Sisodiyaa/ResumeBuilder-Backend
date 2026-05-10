package com.resumeai.ai.service;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class AiNotificationClient {

    @Value("${app.notification.base-url:http://localhost:8088}")
    private String notificationServiceUrl;

    public void notifyUser(Map<String, Object> payload) {
        log.info("Dispatching notification to {}: {}", notificationServiceUrl, payload);
        try {
            RestClient.create(notificationServiceUrl)
                    .post()
                    .uri("/api/v1/notifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Notification successfully dispatched to {}", notificationServiceUrl);
        } catch (Exception ex) {
            log.warn("AI→notification dispatch failed (is notification-service running on {}?): {}",
                    notificationServiceUrl, ex.getMessage());
        }
    }
}
