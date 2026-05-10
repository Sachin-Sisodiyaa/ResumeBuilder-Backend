package com.resumeai.notification.service;

import com.resumeai.notification.dto.NotificationDtos.BulkNotificationRequest;
import com.resumeai.notification.dto.NotificationDtos.NotificationRequest;
import com.resumeai.notification.model.Notification;
import com.resumeai.notification.repository.NotificationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Core notification service for in-app, email, and realtime fan-out. */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationEmailService notificationEmailService;
    private final NotificationStreamService notificationStreamService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.notification.retention-hours:48}")
    private long retentionHours;

    @Override
    public Notification send(NotificationRequest request) {
        Notification notification = buildNotification(request);
        saveNotification(notification);
        dispatchEmail(notification);
        publishNotification(notification);
        return notification;
    }

    @Override
    public List<Notification> sendBulk(BulkNotificationRequest request) {
        List<Long> recipientIds = request.recipientIds() == null ? List.of() : request.recipientIds();
        Map<Long, String> recipientEmails = request.recipientEmails() == null
                ? Collections.emptyMap()
                : request.recipientEmails();
        return recipientIds.stream()
                .map(recipientId -> send(new NotificationRequest(
                        recipientId,
                        request.type(),
                        request.title(),
                        request.message(),
                        request.channel(),
                        request.relatedId(),
                        request.relatedType(),
                        request.actionUrl(),
                        recipientEmails.getOrDefault(recipientId, request.recipientEmail()))))
                .toList();
    }

    @Override
    public List<Notification> getAll() {
        return notificationRepository.findAll(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "sentAt"));
    }

    @Override
    public List<Notification> getByRecipient(Long recipientId, Boolean unreadOnly) {
        if (Boolean.TRUE.equals(unreadOnly)) {
            return notificationRepository.findByRecipientIdAndReadStatusFalseOrderBySentAtDesc(recipientId);
        }
        return notificationRepository.findByRecipientIdOrderBySentAtDesc(recipientId);
    }

    @Override
    public long getUnreadCount(Long recipientId) {
        return notificationRepository.countByRecipientIdAndReadStatusFalse(recipientId);
    }

    @Override
    public Notification markAsRead(String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Notification not found: " + notificationId));
        notification.setReadStatus(true);
        Notification updated = notificationRepository.save(notification);
        publishNotification(updated);
        return updated;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public int markAllRead(Long recipientId) {
        int marked = notificationRepository.markAllAsRead(recipientId);
        if (marked > 0) {
            notificationStreamService.publishUnreadCount(recipientId, 0);
        }
        return marked;
    }

    @Override
    public void deleteNotification(String notificationId) {
        notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Notification not found: " + notificationId));
        notificationRepository.deleteById(notificationId);
    }

    @Override
    @Scheduled(cron = "${app.notification.cleanup-cron:0 0 * * * *}")
    public int cleanupExpiredNotifications() {
        Instant cutoff = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
        int deleted = notificationRepository.deleteBySentAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Notification cleanup deleted {} notification(s) older than {} hours", deleted, retentionHours);
        }
        return deleted;
    }

    /** Sends QUOTA_WARNING once usage reaches 80% of monthly quota. */
    public Notification sendQuotaWarning(Long recipientId, String recipientEmail,
                                         String quotaType, int used, int limit) {
        int percent = limit == 0 ? 0 : (used * 100 / limit);
        if (percent < 80) return null;

        String title = "Quota Warning: " + quotaType;
        String msg = "You have used " + used + "/" + limit + " of your monthly "
                + quotaType + " quota (" + percent + "%). Upgrade to Premium for unlimited access.";

        return send(new NotificationRequest(
                recipientId, "QUOTA_WARNING", title, msg,
                "ALL", null, "quota", "/billing", recipientEmail));
    }

    private Notification buildNotification(NotificationRequest request) {
        Notification n = new Notification();
        n.setNotificationId(UUID.randomUUID().toString());
        n.setRecipientId(request.recipientId());
        n.setType(request.type());
        n.setTitle(request.title());
        n.setMessage(request.message());
        n.setChannel(request.channel() == null ? "APP" : request.channel());
        n.setRelatedId(request.relatedId());
        n.setRelatedType(request.relatedType());
        n.setActionUrl(request.actionUrl());
        n.setRecipientEmail(request.recipientEmail());
        n.setReadStatus(false);
        n.setSentAt(Instant.now());
        return n;
    }

    private void saveNotification(Notification notification) {
        try {
            notificationRepository.save(notification);
            notificationRepository.flush();
            return;
        } catch (RuntimeException ex) {
            log.warn("JPA notification save failed for {}. Trying JDBC fallback: {}",
                    notification.getNotificationId(), ex.getMessage());
        }

        try {
            insertNotificationWithBothReadColumns(notification);
        } catch (RuntimeException bothColumnsFailure) {
            log.warn("JDBC notification insert using both `read` and read_status failed for {}. Trying single-column fallbacks: {}",
                    notification.getNotificationId(), bothColumnsFailure.getMessage());
            try {
                jdbcTemplate.update("""
                        INSERT INTO notifications
                        (notification_id, recipient_id, type, title, message, channel,
                         related_id, related_type, action_url, recipient_email, `read`, sent_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        notification.getNotificationId(),
                        notification.getRecipientId(),
                        notification.getType(),
                        notification.getTitle(),
                        notification.getMessage(),
                        notification.getChannel(),
                        notification.getRelatedId(),
                        notification.getRelatedType(),
                        notification.getActionUrl(),
                        notification.getRecipientEmail(),
                        notification.isRead() ? 1 : 0,
                        java.sql.Timestamp.from(notification.getSentAt()));
            } catch (RuntimeException readColumnFailure) {
                log.warn("JDBC notification insert using only `read` failed for {}. Trying read_status only: {}",
                        notification.getNotificationId(), readColumnFailure.getMessage());
                try {
                    jdbcTemplate.update("""
                            INSERT INTO notifications
                            (notification_id, recipient_id, type, title, message, channel,
                             related_id, related_type, action_url, recipient_email, read_status, sent_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            notification.getNotificationId(),
                            notification.getRecipientId(),
                            notification.getType(),
                            notification.getTitle(),
                            notification.getMessage(),
                            notification.getChannel(),
                            notification.getRelatedId(),
                            notification.getRelatedType(),
                            notification.getActionUrl(),
                            notification.getRecipientEmail(),
                            notification.isRead() ? 1 : 0,
                            java.sql.Timestamp.from(notification.getSentAt()));
                } catch (RuntimeException fallbackFailure) {
                    log.error("Notification persistence failed for {} after JPA and JDBC fallback",
                            notification.getNotificationId(), fallbackFailure);
                    throw fallbackFailure;
                }
            }
        }
    }

    private void insertNotificationWithBothReadColumns(Notification notification) {
        jdbcTemplate.update("""
                INSERT INTO notifications
                (notification_id, recipient_id, type, title, message, channel,
                 related_id, related_type, action_url, recipient_email, `read`, read_status, sent_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                notification.getNotificationId(),
                notification.getRecipientId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getChannel(),
                notification.getRelatedId(),
                notification.getRelatedType(),
                notification.getActionUrl(),
                notification.getRecipientEmail(),
                notification.isRead() ? 1 : 0,
                notification.isRead() ? 1 : 0,
                java.sql.Timestamp.from(notification.getSentAt()));
    }

    private void dispatchEmail(Notification notification) {
        String channel = notification.getChannel();
        if (channel == null) return;
        boolean sendEmail = "EMAIL".equalsIgnoreCase(channel) || "ALL".equalsIgnoreCase(channel);
        if (sendEmail && notification.getRecipientEmail() != null) {
            try {
                notificationEmailService.sendNotificationEmail(
                        notification.getRecipientEmail(),
                        notification.getTitle(),
                        notification.getMessage(),
                        notification.getActionUrl());
            } catch (RuntimeException ex) {
                log.warn("Email dispatch failed for notification {} but in-app notification was kept: {}",
                        notification.getNotificationId(), ex.getMessage());
            }
        }
    }

    private void publishNotification(Notification notification) {
        try {
            notificationStreamService.publish(
                    notification,
                    notificationRepository.countByRecipientIdAndReadStatusFalse(notification.getRecipientId()));
        } catch (RuntimeException ex) {
            log.warn("Realtime notification publish failed for {} but in-app notification was kept: {}",
                    notification.getNotificationId(), ex.getMessage());
        }
    }
}
