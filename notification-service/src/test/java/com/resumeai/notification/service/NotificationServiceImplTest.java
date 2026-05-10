package com.resumeai.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resumeai.notification.dto.NotificationDtos.BulkNotificationRequest;
import com.resumeai.notification.dto.NotificationDtos.NotificationRequest;
import com.resumeai.notification.model.Notification;
import com.resumeai.notification.repository.NotificationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationEmailService notificationEmailService;
    @Mock
    private NotificationStreamService notificationStreamService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void sendCreatesUnreadNotification() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Notification notification = notificationService.send(
                new NotificationRequest(1L, "AI_DONE", "Done", "Message",
                        "APP", "1", "resume", "/resumes/1", null));

        assertFalse(notification.isRead());
        assertEquals("AI_DONE", notification.getType());
        assertNotNull(notification.getNotificationId());
    }

    @Test
    void sendJdbcFallbackWritesBothReadColumnsForMixedMysqlSchemas() {
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("missing read_status default"));

        notificationService.send(
                new NotificationRequest(1L, "BROADCAST", "Title", "Message",
                        "APP", null, null, "/notifications", null));

        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("`read`, read_status")),
                any(Object[].class));
    }

    @Test
    void sendJdbcFallbackCanUseLegacyReadColumnOnly() {
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("jpa down"));
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("read_status missing"))
                .thenReturn(1);

        Notification notification = notificationService.send(
                new NotificationRequest(1L, "BROADCAST", "Title", "Message",
                        null, null, null, null, null));

        assertEquals("APP", notification.getChannel());
        verify(jdbcTemplate).update(argThat(sql -> sql.contains("`read`, read_status")), any(Object[].class));
        verify(jdbcTemplate).update(argThat(sql -> !sql.contains("read_status") && sql.contains("`read`")),
                any(Object[].class));
    }

    @Test
    void sendJdbcFallbackCanUseReadStatusColumnOnly() {
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("jpa down"));
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("both missing"))
                .thenThrow(new RuntimeException("read missing"))
                .thenReturn(1);

        notificationService.send(
                new NotificationRequest(1L, "BROADCAST", "Title", "Message",
                        "APP", null, null, null, null));

        verify(jdbcTemplate).update(argThat(sql -> sql.contains("read_status, sent_at") && !sql.contains("`read`")),
                any(Object[].class));
    }

    @Test
    void sendRethrowsWhenAllPersistenceFallbacksFail() {
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("jpa down"));
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));

        NotificationRequest request = new NotificationRequest(
                1L, "BROADCAST", "Title", "Message", "APP", null, null, null, null);
        assertThrows(RuntimeException.class, () -> notificationService.send(request));
    }

    @Test
    void markAsReadUpdatesNotification() {
        Notification notification = new Notification();
        notification.setNotificationId("n1");
        notification.setRead(false);
        when(notificationRepository.findById("n1")).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Notification updated = notificationService.markAsRead("n1");

        assertTrue(updated.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAllReadDelegatesToBatchUpdate() {
        when(notificationRepository.markAllAsRead(7L)).thenReturn(3);

        int marked = notificationService.markAllRead(7L);

        assertEquals(3, marked);
        verify(notificationRepository).markAllAsRead(7L);
    }

    @Test
    void markAllReadDoesNotPublishWhenNothingChanged() {
        when(notificationRepository.markAllAsRead(7L)).thenReturn(0);

        assertEquals(0, notificationService.markAllRead(7L));
        verify(notificationStreamService, never()).publishUnreadCount(7L, 0);
    }

    @Test
    void sendBulkCreatesMultipleNotifications() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<Notification> notifications = notificationService.sendBulk(
                new BulkNotificationRequest(
                        List.of(1L, 2L), "PLAN_CHANGE", "Title", "Message",
                        "EMAIL", "rel", "plan", "/billing", "user@example.com"));

        assertEquals(2, notifications.size());
    }

    @Test
    void sendEmailChannelDispatchesEmail() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        notificationService.send(new NotificationRequest(
                1L, "EXPORT_READY", "Export Ready", "Your PDF is ready.",
                "EMAIL", "job-1", "export", "/exports/job-1", "user@example.com"));

        verify(notificationEmailService).sendNotificationEmail(
                "user@example.com", "Export Ready",
                "Your PDF is ready.", "/exports/job-1");
    }

    @Test
    void emailAndRealtimeFailuresDoNotRollbackSavedNotification() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.countByRecipientIdAndReadStatusFalse(1L)).thenReturn(1L);
        doThrow(new RuntimeException("smtp"))
                .when(notificationEmailService)
                .sendNotificationEmail(anyString(), anyString(), anyString(), any());
        doThrow(new RuntimeException("stream"))
                .when(notificationStreamService)
                .publish(any(Notification.class), anyLong());

        Notification notification = notificationService.send(new NotificationRequest(
                1L, "EXPORT_READY", "Export Ready", "Your PDF is ready.",
                "ALL", "job-1", "export", "/exports/job-1", "user@example.com"));

        assertEquals("EXPORT_READY", notification.getType());
    }

    @Test
    void queryAndCleanupDelegateToRepository() {
        Notification notification = new Notification();
        notification.setRecipientId(2L);
        notification.setRead(false);
        when(notificationRepository.findAll(any(Sort.class))).thenReturn(List.of(notification));
        when(notificationRepository.findByRecipientIdAndReadStatusFalseOrderBySentAtDesc(2L)).thenReturn(List.of(notification));
        when(notificationRepository.findByRecipientIdOrderBySentAtDesc(2L)).thenReturn(List.of(notification));
        when(notificationRepository.countByRecipientIdAndReadStatusFalse(2L)).thenReturn(1L);
        when(notificationRepository.deleteBySentAtBefore(any())).thenReturn(4);

        assertEquals(1, notificationService.getAll().size());
        assertEquals(1, notificationService.getByRecipient(2L, true).size());
        assertEquals(1, notificationService.getByRecipient(2L, false).size());
        assertEquals(1L, notificationService.getUnreadCount(2L));
        assertEquals(4, notificationService.cleanupExpiredNotifications());
    }

    @Test
    void deleteAndMissingPathsAreHandled() {
        Notification notification = new Notification();
        notification.setNotificationId("n1");
        when(notificationRepository.findById("n1")).thenReturn(Optional.of(notification));
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

        notificationService.deleteNotification("n1");
        verify(notificationRepository).deleteById("n1");
        assertThrows(ResponseStatusException.class, () -> notificationService.markAsRead("missing"));
        assertThrows(ResponseStatusException.class, () -> notificationService.deleteNotification("missing"));
    }

    @Test
    void quotaWarningOnlySendsAtThreshold() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertNull(notificationService.sendQuotaWarning(1L, "user@example.com", "AI", 3, 5));
        assertNull(notificationService.sendQuotaWarning(1L, "user@example.com", "AI", 1, 0));
        Notification warning = notificationService.sendQuotaWarning(1L, "user@example.com", "AI", 4, 5);

        assertNotNull(warning);
        assertEquals("QUOTA_WARNING", warning.getType());
        verify(notificationEmailService).sendNotificationEmail(
                "user@example.com", "Quota Warning: AI",
                "You have used 4/5 of your monthly AI quota (80%). Upgrade to Premium for unlimited access.",
                "/billing");
    }
}
