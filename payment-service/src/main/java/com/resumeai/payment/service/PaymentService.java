package com.resumeai.payment.service;

import com.resumeai.payment.dto.PaymentDtos.CreateOrderRequest;
import com.resumeai.payment.dto.PaymentDtos.CreateOrderResponse;
import com.resumeai.payment.dto.PaymentDtos.PaymentResponse;
import com.resumeai.payment.dto.PaymentDtos.VerifyPaymentRequest;
import com.resumeai.payment.model.PaymentOrder;
import com.resumeai.payment.repository.PaymentOrderRepository;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentOrderRepository repository;
    private final RazorpayOrderClient razorpayOrderClient;
    private final RazorpaySignatureVerifier signatureVerifier;
    private final AuthSubscriptionClient authSubscriptionClient;

    @Value("${app.razorpay.key-id}")
    private String keyId;

    @Value("${app.razorpay.currency}")
    private String currency;

    @Value("${app.razorpay.premium-amount-paise}")
    private long premiumAmountPaise;

    @Value("${app.razorpay.premium-monthly-amount-paise:${app.razorpay.premium-amount-paise}}")
    private long premiumMonthlyAmountPaise;

    @Value("${app.razorpay.premium-yearly-amount-paise:499900}")
    private long premiumYearlyAmountPaise;

    private static final long PREMIUM_MONTHLY_OFFER_AMOUNT_PAISE = 49900L;
    private static final long PREMIUM_YEARLY_OFFER_AMOUNT_PAISE = 499900L;
    private static final String BILLING_MONTHLY = "MONTHLY";

    @Transactional
    public CreateOrderResponse createOrder(Long authenticatedUserId, CreateOrderRequest request) {
        Long userId = resolveUserId(authenticatedUserId, request.userId());
        String plan = request.plan().toUpperCase();
        String billingCycle = normalizeBillingCycle(request.billingCycle());
        long amount = amountForPlan(plan, billingCycle, request.amountPaise());
        try {
            RazorpayOrderClient.RazorpayOrder razorpayOrder = razorpayOrderClient.createOrder(
                    amount, currency, "resumeai-" + userId + "-" + billingCycle.toLowerCase() + "-" + System.currentTimeMillis());
            PaymentOrder order = new PaymentOrder();
            order.setRazorpayOrderId(razorpayOrder.id());
            order.setUserId(userId);
            order.setPlan(plan);
            order.setBillingCycle(billingCycle);
            order.setAmount(razorpayOrder.amount());
            order.setCurrency(razorpayOrder.currency());
            order.setStatus(PaymentOrder.Status.CREATED);
            repository.save(order);
            return new CreateOrderResponse(keyId, order.getRazorpayOrderId(), order.getAmount(),
                    order.getCurrency(), order.getPlan(), order.getBillingCycle(), razorpayOrder.status());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Razorpay", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay request interrupted", ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    @Transactional
    public PaymentResponse verifyPayment(Long authenticatedUserId, VerifyPaymentRequest request) {
        PaymentOrder order = repository.findById(request.razorpayOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found"));
        requireOwnOrder(authenticatedUserId, order);

        if (order.getStatus() == PaymentOrder.Status.PAID) {
            return toResponse(order, "Payment already verified.");
        }

        boolean valid;
        try {
            valid = signatureVerifier.isValid(
                    request.razorpayOrderId(),
                    request.razorpayPaymentId(),
                    request.razorpaySignature());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
        if (!valid) {
            order.setStatus(PaymentOrder.Status.FAILED);
            order.setFailureReason("Razorpay signature mismatch");
            repository.save(order);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment signature is invalid");
        }

        order.setRazorpayPaymentId(request.razorpayPaymentId());
        order.setStatus(PaymentOrder.Status.PAID);
        order.setFailureReason(null);
        PaymentOrder saved = repository.save(order);
        authSubscriptionClient.updateSubscription(saved.getUserId(), saved.getPlan());
        return toResponse(saved, "Payment verified and subscription updated.");
    }

    public List<PaymentOrder> listForUser(Long authenticatedUserId, Long userId) {
        Long resolvedUserId = resolveUserId(authenticatedUserId, userId);
        return repository.findByUserIdOrderByCreatedAtDesc(resolvedUserId);
    }

    public List<PaymentOrder> listAllForAdmin(String role) {
        requireAdmin(role);
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public List<PaymentOrder> listUserPaymentsForAdmin(String role, Long userId) {
        requireAdmin(role);
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required");
        }
    }

    private long amountForPlan(String plan, String billingCycle, Long requestedAmountPaise) {
        if ("PREMIUM".equalsIgnoreCase(plan)) {
            long configuredAmount;
            long offerAmount;
            if ("YEARLY".equalsIgnoreCase(billingCycle)) {
                configuredAmount = premiumYearlyAmountPaise;
                offerAmount = PREMIUM_YEARLY_OFFER_AMOUNT_PAISE;
            } else {
                configuredAmount = premiumMonthlyAmountPaise > 0 ? premiumMonthlyAmountPaise : premiumAmountPaise;
                offerAmount = PREMIUM_MONTHLY_OFFER_AMOUNT_PAISE;
            }
            if (requestedAmountPaise != null && requestedAmountPaise != offerAmount) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Selected offer amount does not match the chosen billing cycle");
            }
            if (requestedAmountPaise != null) {
                return requestedAmountPaise;
            }
            return configuredAmount > 0 ? configuredAmount : offerAmount;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment plan");
    }

    private String normalizeBillingCycle(String billingCycle) {
        if (billingCycle == null || billingCycle.isBlank()) {
            return BILLING_MONTHLY;
        }
        String normalized = billingCycle.toUpperCase();
        if (BILLING_MONTHLY.equals(normalized) || "YEARLY".equals(normalized)) {
            return normalized;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported billing cycle");
    }

    private Long resolveUserId(Long authenticatedUserId, Long requestedUserId) {
        if (authenticatedUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication user id is missing");
        }
        if (requestedUserId != null && !authenticatedUserId.equals(requestedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot manage payment for another user");
        }
        return authenticatedUserId;
    }

    private void requireOwnOrder(Long authenticatedUserId, PaymentOrder order) {
        if (authenticatedUserId == null || !authenticatedUserId.equals(order.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot verify payment for another user");
        }
    }

    private PaymentResponse toResponse(PaymentOrder order, String message) {
        return new PaymentResponse(
                order.getRazorpayOrderId(),
                order.getRazorpayPaymentId(),
                order.getUserId(),
                order.getPlan(),
                order.getBillingCycle() == null ? BILLING_MONTHLY : order.getBillingCycle(),
                order.getAmount(),
                order.getCurrency(),
                order.getStatus(),
                message);
    }
}
