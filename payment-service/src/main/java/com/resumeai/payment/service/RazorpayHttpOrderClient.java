package com.resumeai.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RazorpayHttpOrderClient implements RazorpayOrderClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String keyId;
    private final String keySecret;

    public RazorpayHttpOrderClient(
            ObjectMapper objectMapper,
            @Value("${app.razorpay.key-id}") String keyId,
            @Value("${app.razorpay.key-secret}") String keySecret) {
        this.objectMapper = objectMapper;
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public RazorpayOrder createOrder(long amount, String currency, String receipt)
            throws IOException, InterruptedException {
        if (!StringUtils.hasText(keySecret)) {
            throw new IllegalStateException("RAZORPAY_KEY_SECRET is not configured");
        }

        String body = objectMapper.writeValueAsString(Map.of(
                "amount", amount,
                "currency", currency,
                "receipt", receipt,
                "payment_capture", 1
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.razorpay.com/v1/orders"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + basicAuth())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Razorpay order creation failed with status " + response.statusCode());
        }

        JsonNode json = objectMapper.readTree(response.body());
        return new RazorpayOrder(
                json.path("id").asText(),
                json.path("amount").asLong(),
                json.path("currency").asText(),
                json.path("status").asText()
        );
    }

    private String basicAuth() {
        String raw = keyId + ":" + keySecret;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
