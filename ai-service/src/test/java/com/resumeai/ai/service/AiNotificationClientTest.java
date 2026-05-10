package com.resumeai.ai.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiNotificationClientTest {

    @Test
    void notifyUserPostsPayloadWhenNotificationServiceIsAvailable() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/notifications", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            AiNotificationClient client = new AiNotificationClient();
            setField(client, "notificationServiceUrl", "http://localhost:" + server.getAddress().getPort());

            assertDoesNotThrow(() -> client.notifyUser(Map.of("recipientId", 1L, "message", "done")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void notifyUserSwallowsDownstreamFailures() throws Exception {
        AiNotificationClient client = new AiNotificationClient();
        setField(client, "notificationServiceUrl", "http://localhost:1");

        assertDoesNotThrow(() -> client.notifyUser(Map.of("recipientId", 1L, "message", "done")));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
