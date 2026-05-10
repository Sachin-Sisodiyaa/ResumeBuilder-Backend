package com.resumeai.auth.service;

import com.resumeai.auth.model.User;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class AdminNotificationClient {
    private final RestTemplate restTemplate;
    private final String notificationBaseUrl;
    private volatile String lastFailureMessage = "";

    public AdminNotificationClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.notification.base-url:http://localhost:8088}") String notificationBaseUrl,
            @Value("${app.notification.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.notification.read-timeout-ms:10000}") long readTimeoutMs) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
        this.notificationBaseUrl = notificationBaseUrl;
    }

    public void notifyAdminsOfNewUser(List<User> admins, User newUser) {
        admins.stream()
                .filter(admin -> admin.getUserId() != null)
                .forEach(admin -> sendNewUserNotification(admin, newUser));
    }

    public void notifyPlanChange(User user, String newPlan) {
        notifyUser(user, Map.of(
                "type", "PLAN_CHANGE",
                "title", "Subscription plan changed",
                "message", "Your subscription has been updated to " + newPlan + ".",
                "channel", "ALL",
                "relatedId", String.valueOf(user.getUserId()),
                "relatedType", "User",
                "actionUrl", "/billing"
        ));
    }

    public boolean notifyUser(User recipient, Map<String, Object> details) {
        try {
            Map<String, Object> payload = new java.util.HashMap<>(details);
            payload.put("recipientId", recipient.getUserId());
            payload.put("recipientEmail", recipient.getEmail());
            
            restTemplate.postForObject(notificationBaseUrl + "/api/v1/notifications", payload, Object.class);
            return true;
        } catch (RestClientException ex) {
            lastFailureMessage = ex.getMessage();
            log.warn("Could not send {} notification to {}: {}",
                    details.get("type"), recipient.getEmail(), ex.getMessage());
            return false;
        }
    }

    public int notifyUsersBulk(List<User> recipients, Map<String, Object> details) {
        lastFailureMessage = "";
        if (recipients == null || recipients.isEmpty()) {
            return 0;
        }
        try {
            Map<String, Object> payload = new HashMap<>(details);
            payload.put("recipientIds", recipients.stream().map(User::getUserId).toList());
            Map<Long, String> recipientEmails = new HashMap<>();
            recipients.forEach(user -> recipientEmails.put(user.getUserId(), user.getEmail()));
            payload.put("recipientEmails", recipientEmails);

            List<?> response = restTemplate.postForObject(
                    notificationBaseUrl + "/api/v1/notifications/bulk",
                    payload,
                    List.class);
            return response == null ? 0 : response.size();
        } catch (RestClientException ex) {
            lastFailureMessage = ex.getMessage();
            log.warn("Could not send bulk {} notification to {} recipient(s): {}",
                    details.get("type"), recipients.size(), ex.getMessage());
            return (int) recipients.stream()
                    .filter(user -> notifyUser(user, details))
                    .count();
        }
    }

    public String getLastFailureMessage() {
        return lastFailureMessage == null ? "" : lastFailureMessage;
    }

    private void sendNewUserNotification(User admin, User newUser) {
        notifyUser(admin, Map.of(
                "type", "USER_REGISTERED",
                "title", "New user registered",
                "message", newUser.getFullName() + " (" + newUser.getEmail() + ") created a new account.",
                "channel", "APP",
                "relatedId", String.valueOf(newUser.getUserId()),
                "relatedType", "User",
                "actionUrl", "/admin/users"
        ));
    }
}
