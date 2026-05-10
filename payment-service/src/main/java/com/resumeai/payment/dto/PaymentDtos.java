package com.resumeai.payment.dto;

import com.resumeai.payment.model.PaymentOrder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public final class PaymentDtos {
    private PaymentDtos() {
    }

    public record CreateOrderRequest(
            @Positive Long userId,
            @NotBlank @Pattern(regexp = "PREMIUM", flags = Pattern.Flag.CASE_INSENSITIVE) String plan,
            @Pattern(regexp = "MONTHLY|YEARLY", flags = Pattern.Flag.CASE_INSENSITIVE) String billingCycle,
            @Positive Long amountPaise) {
    }

    public record CreateOrderResponse(
            String keyId,
            String orderId,
            long amount,
            String currency,
            String plan,
            String billingCycle,
            String status) {
    }

    public record VerifyPaymentRequest(
            @NotBlank String razorpayOrderId,
            @NotBlank String razorpayPaymentId,
            @NotBlank String razorpaySignature) {
    }

    public record PaymentResponse(
            String orderId,
            String paymentId,
            Long userId,
            String plan,
            String billingCycle,
            long amount,
            String currency,
            PaymentOrder.Status status,
            String message) {
    }
}
