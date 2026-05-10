package com.resumeai.notification.dto;

import java.util.List;
import java.util.Map;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record NotificationRequest(Long recipientId, String type, String title, String message,
                                      String channel, String relatedId, String relatedType, String actionUrl,
                                      String recipientEmail) {
    }

    public record BulkNotificationRequest(List<Long> recipientIds, String type, String title, String message,
                                          String channel, String relatedId, String relatedType, String actionUrl,
                                          String recipientEmail, Map<Long, String> recipientEmails) {
        public BulkNotificationRequest(List<Long> recipientIds, String type, String title, String message,
                                       String channel, String relatedId, String relatedType, String actionUrl,
                                       String recipientEmail) {
            this(recipientIds, type, title, message, channel, relatedId, relatedType, actionUrl,
                    recipientEmail, Map.of());
        }
    }
}
