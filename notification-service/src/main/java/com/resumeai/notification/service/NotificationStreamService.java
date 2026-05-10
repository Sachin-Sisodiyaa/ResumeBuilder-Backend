package com.resumeai.notification.service;

import com.resumeai.notification.model.Notification;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Slf4j
public class NotificationStreamService {
    private final long streamTimeoutMillis;

    private final Map<Long, List<SseEmitter>> emittersByRecipient = new ConcurrentHashMap<>();

    public NotificationStreamService(
            @Value("${app.notification.stream-timeout-millis:1800000}") long streamTimeoutMillis) {
        this.streamTimeoutMillis = streamTimeoutMillis;
    }

    public SseEmitter subscribe(Long recipientId, long unreadCount) {
        SseEmitter emitter = new SseEmitter(streamTimeoutMillis);
        emittersByRecipient.computeIfAbsent(recipientId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(recipientId, emitter));
        emitter.onTimeout(() -> closeTimedOutEmitter(recipientId, emitter));
        emitter.onError(error -> removeEmitter(recipientId, emitter));

        send(emitter, recipientId, "unread-count", Map.of("unreadCount", unreadCount));
        return emitter;
    }

    public void publish(Notification notification, long unreadCount) {
        publish(notification.getRecipientId(), "notification", Map.of(
                "notification", notification,
                "unreadCount", unreadCount));
    }

    public void publishUnreadCount(Long recipientId, long unreadCount) {
        publish(recipientId, "unread-count", Map.of("unreadCount", unreadCount));
    }

    private void publish(Long recipientId, String eventName, Object payload) {
        List<SseEmitter> emitters = emittersByRecipient.getOrDefault(recipientId, List.of());
        emitters.forEach(emitter -> send(emitter, recipientId, eventName, payload));
    }

    private void send(SseEmitter emitter, Long recipientId, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (Exception ex) {
            log.debug("Closing notification stream for recipient {} after send failure: {}", recipientId, ex.getMessage());
            closeFailedEmitter(recipientId, emitter);
        }
    }

    private void closeFailedEmitter(Long recipientId, SseEmitter emitter) {
        removeEmitter(recipientId, emitter);
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // The client connection is already gone.
        }
    }

    private void closeTimedOutEmitter(Long recipientId, SseEmitter emitter) {
        removeEmitter(recipientId, emitter);
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // The async request may already be unusable after timeout.
        }
    }

    private void removeEmitter(Long recipientId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByRecipient.get(recipientId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByRecipient.remove(recipientId);
        }
    }
}
