package com.resumeai.notification.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumeai.notification.model.Notification;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationStreamServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void subscribesPublishesAndRemovesEmitterOnCompletion() {
        NotificationStreamService service = new NotificationStreamService(1_800_000L);

        SseEmitter emitter = service.subscribe(42L, 3);
        assertNotNull(emitter);

        Map<Long, ?> emitters = (Map<Long, ?>) ReflectionTestUtils.getField(service, "emittersByRecipient");
        assertTrue(emitters.containsKey(42L));

        Notification notification = new Notification();
        notification.setRecipientId(42L);
        notification.setNotificationId("n1");
        notification.setType("EXPORT_READY");
        notification.setTitle("Export ready");
        notification.setChannel("APP");

        service.publish(notification, 4);
        service.publishUnreadCount(42L, 5);

        ReflectionTestUtils.invokeMethod(service, "removeEmitter", 42L, emitter);
        assertTrue(((Map<Long, ?>) ReflectionTestUtils.getField(service, "emittersByRecipient")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void removesEmitterAfterSendFailure() {
        NotificationStreamService service = new NotificationStreamService(1_800_000L);
        SseEmitter emitter = new FailingEmitter();

        ((Map<Long, Object>) ReflectionTestUtils.getField(service, "emittersByRecipient"))
                .put(42L, new java.util.concurrent.CopyOnWriteArrayList<>(java.util.List.of(emitter)));

        ReflectionTestUtils.invokeMethod(service, "send", emitter, 42L, "notification", Map.of("value", "test"));

        assertTrue(((Map<Long, ?>) ReflectionTestUtils.getField(service, "emittersByRecipient")).isEmpty());
    }

    static class FailingEmitter extends SseEmitter {
        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("client disconnected");
        }
    }
}
