package com.resumeai.export.service;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class ExportNotificationClient {

    @Value("${app.notification.base-url:http://localhost:8088}")
    private String notificationServiceUrl;

    public void notifyUser(Map<String, Object> payload) {
        try {
            RestClient.create(notificationServiceUrl)
                    .post()
                    .uri("/api/v1/notifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.debug("Notification dispatch skipped: {}", ex.getMessage());
        }
    }
}
