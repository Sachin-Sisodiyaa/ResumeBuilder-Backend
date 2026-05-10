package com.resumeai.notification.controller;

import com.resumeai.notification.dto.NotificationDtos.BulkNotificationRequest;
import com.resumeai.notification.dto.NotificationDtos.NotificationRequest;
import com.resumeai.notification.model.Notification;
import com.resumeai.notification.service.NotificationService;
import com.resumeai.notification.service.NotificationStreamService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationStreamService notificationStreamService;

    @PostMapping
    public Notification send(@RequestBody NotificationRequest request) {
        try {
            return notificationService.send(request);
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(NotificationController.class)
                    .error("Failed to send notification: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @PostMapping("/bulk")
    public List<Notification> sendBulk(@RequestBody BulkNotificationRequest request) {
        return notificationService.sendBulk(request);
    }

    @GetMapping
    public List<Notification> list(@RequestParam(value = "recipientId", required = false) Long recipientId,
                                   @RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly) {
        return recipientId == null ? notificationService.getAll() : notificationService.getByRecipient(recipientId, unreadOnly);
    }

    @GetMapping("/unread-count/{recipientId}")
    public Map<String, Long> unread(@PathVariable("recipientId") Long recipientId) {
        return Map.of("unreadCount", notificationService.getUnreadCount(recipientId));
    }

    @GetMapping(value = "/stream/{recipientId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable("recipientId") Long recipientId) {
        return notificationStreamService.subscribe(recipientId, notificationService.getUnreadCount(recipientId));
    }

    @PutMapping("/{notificationId}/read")
    public Notification read(@PathVariable("notificationId") String notificationId) {
        return notificationService.markAsRead(notificationId);
    }

    @PutMapping("/read-all/{recipientId}")
    public Map<String, Integer> readAll(@PathVariable("recipientId") Long recipientId) {
        return Map.of("marked", notificationService.markAllRead(recipientId));
    }

    @DeleteMapping("/{notificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("notificationId") String notificationId) {
        notificationService.deleteNotification(notificationId);
    }
}
