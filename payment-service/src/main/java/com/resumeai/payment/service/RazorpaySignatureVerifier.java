package com.resumeai.payment.service;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RazorpaySignatureVerifier {
    private final String keySecret;

    public RazorpaySignatureVerifier(@Value("${app.razorpay.key-secret}") String keySecret) {
        this.keySecret = keySecret;
    }

    public boolean isValid(String orderId, String paymentId, String signature) {
        if (!StringUtils.hasText(keySecret)) {
            throw new IllegalStateException("RAZORPAY_KEY_SECRET is not configured");
        }
        String payload = orderId + "|" + paymentId;
        String expected = hmacSha256(payload, keySecret);
        return constantTimeEquals(expected, signature);
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify Razorpay signature", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        int diff = a.length ^ b.length;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
