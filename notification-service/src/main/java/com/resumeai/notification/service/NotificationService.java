package com.resumeai.notification.service;

import com.resumeai.notification.dto.NotificationDtos.BulkNotificationRequest;
import com.resumeai.notification.dto.NotificationDtos.NotificationRequest;
import com.resumeai.notification.model.Notification;
import java.util.List;

public interface NotificationService {
    Notification send(NotificationRequest request);
    List<Notification> sendBulk(BulkNotificationRequest request);
    Notification markAsRead(String notificationId);
    int markAllRead(Long recipientId);
    List<Notification> getByRecipient(Long recipientId, Boolean unreadOnly);
    long getUnreadCount(Long recipientId);
    void deleteNotification(String notificationId);
    int cleanupExpiredNotifications();
    List<Notification> getAll();
}
