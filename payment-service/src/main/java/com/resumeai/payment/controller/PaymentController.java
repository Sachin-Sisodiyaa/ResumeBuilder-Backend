package com.resumeai.payment.controller;

import com.resumeai.payment.dto.PaymentDtos.CreateOrderRequest;
import com.resumeai.payment.dto.PaymentDtos.CreateOrderResponse;
import com.resumeai.payment.dto.PaymentDtos.PaymentResponse;
import com.resumeai.payment.dto.PaymentDtos.VerifyPaymentRequest;
import com.resumeai.payment.model.PaymentOrder;
import com.resumeai.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponse createOrder(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateOrderRequest request) {
        return paymentService.createOrder(userId, request);
    }

    @PostMapping("/verify")
    public PaymentResponse verify(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody VerifyPaymentRequest request) {
        return paymentService.verifyPayment(userId, request);
    }

    @GetMapping
    public List<PaymentOrder> history(@RequestHeader("X-User-Id") Long userId) {
        return paymentService.listForUser(userId, userId);
    }

    @GetMapping("/admin")
    public List<PaymentOrder> allPayments(@RequestHeader("X-User-Role") String role) {
        return paymentService.listAllForAdmin(role);
    }

    @GetMapping("/admin/users/{userId}")
    public List<PaymentOrder> userPayments(
            @RequestHeader("X-User-Role") String role,
            @PathVariable("userId") Long userId) {
        return paymentService.listUserPaymentsForAdmin(role, userId);
    }
}
