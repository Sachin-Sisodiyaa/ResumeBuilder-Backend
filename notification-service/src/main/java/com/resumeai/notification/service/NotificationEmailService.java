package com.resumeai.notification.service;

public interface NotificationEmailService {
    /**
     * Sends an email notification via JavaMailSender / AWS SES.
     * Gracefully degrades (logs only) when no mail sender is configured.
     */
    void sendNotificationEmail(String recipientEmail, String title,
                               String message, String actionUrl);
}
