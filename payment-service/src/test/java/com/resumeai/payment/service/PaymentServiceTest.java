package com.resumeai.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resumeai.payment.dto.PaymentDtos.CreateOrderRequest;
import com.resumeai.payment.dto.PaymentDtos.VerifyPaymentRequest;
import com.resumeai.payment.model.PaymentOrder;
import com.resumeai.payment.repository.PaymentOrderRepository;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class PaymentServiceTest {
    private PaymentOrderRepository repository;
    private RazorpayOrderClient razorpayOrderClient;
    private RazorpaySignatureVerifier signatureVerifier;
    private AuthSubscriptionClient authSubscriptionClient;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(PaymentOrderRepository.class);
        razorpayOrderClient = org.mockito.Mockito.mock(RazorpayOrderClient.class);
        signatureVerifier = org.mockito.Mockito.mock(RazorpaySignatureVerifier.class);
        authSubscriptionClient = org.mockito.Mockito.mock(AuthSubscriptionClient.class);
        paymentService = new PaymentService(repository, razorpayOrderClient, signatureVerifier, authSubscriptionClient);
        ReflectionTestUtils.setField(paymentService, "keyId", "rzp_test_key");
        ReflectionTestUtils.setField(paymentService, "currency", "INR");
        ReflectionTestUtils.setField(paymentService, "premiumAmountPaise", 49900L);
        ReflectionTestUtils.setField(paymentService, "premiumMonthlyAmountPaise", 49900L);
        ReflectionTestUtils.setField(paymentService, "premiumYearlyAmountPaise", 499900L);
    }

    @Test
    void createOrderStoresRazorpayOrder() throws Exception {
        when(razorpayOrderClient.createOrder(org.mockito.ArgumentMatchers.eq(49900L),
                org.mockito.ArgumentMatchers.eq("INR"), anyString()))
                .thenReturn(new RazorpayOrderClient.RazorpayOrder("order_123", 49900L, "INR", "created"));
        when(repository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService.createOrder(42L, new CreateOrderRequest(42L, "PREMIUM", "MONTHLY", 49900L));

        assertThat(response.orderId()).isEqualTo("order_123");
        assertThat(response.keyId()).isEqualTo("rzp_test_key");
        assertThat(response.billingCycle()).isEqualTo("MONTHLY");
        ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentOrder.Status.CREATED);
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
        assertThat(captor.getValue().getBillingCycle()).isEqualTo("MONTHLY");
    }

    @Test
    void createYearlyOrderUsesYearlyAmount() throws Exception {
        when(razorpayOrderClient.createOrder(org.mockito.ArgumentMatchers.eq(499900L),
                org.mockito.ArgumentMatchers.eq("INR"), anyString()))
                .thenReturn(new RazorpayOrderClient.RazorpayOrder("order_yearly", 499900L, "INR", "created"));
        when(repository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService.createOrder(42L, new CreateOrderRequest(42L, "PREMIUM", "YEARLY", 499900L));

        assertThat(response.orderId()).isEqualTo("order_yearly");
        assertThat(response.amount()).isEqualTo(499900L);
        assertThat(response.billingCycle()).isEqualTo("YEARLY");
        ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(499900L);
        assertThat(captor.getValue().getBillingCycle()).isEqualTo("YEARLY");
    }

    @Test
    void createOrderRejectsMismatchedOfferAmount() {
        assertThatThrownBy(() -> paymentService.createOrder(42L,
                new CreateOrderRequest(42L, "PREMIUM", "YEARLY", 49900L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Selected offer amount does not match the chosen billing cycle");
    }

    @Test
    void verifyPaymentMarksOrderPaidAndUpdatesSubscription() {
        PaymentOrder order = new PaymentOrder();
        order.setRazorpayOrderId("order_123");
        order.setUserId(42L);
        order.setPlan("PREMIUM");
        order.setBillingCycle("YEARLY");
        order.setAmount(49900L);
        order.setCurrency("INR");
        order.setStatus(PaymentOrder.Status.CREATED);

        when(repository.findById("order_123")).thenReturn(Optional.of(order));
        when(signatureVerifier.isValid("order_123", "pay_123", "sig_123")).thenReturn(true);
        when(repository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService.verifyPayment(42L, new VerifyPaymentRequest("order_123", "pay_123", "sig_123"));

        assertThat(response.status()).isEqualTo(PaymentOrder.Status.PAID);
        assertThat(response.paymentId()).isEqualTo("pay_123");
        assertThat(response.billingCycle()).isEqualTo("YEARLY");
        verify(authSubscriptionClient).updateSubscription(42L, "PREMIUM");
    }

    @Test
    void createOrderValidatesUserPlanBillingAndClientFailures() throws Exception {
        assertThatThrownBy(() -> paymentService.createOrder(null,
                new CreateOrderRequest(null, "PREMIUM", "MONTHLY", 49900L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Authentication user id is missing");
        assertThatThrownBy(() -> paymentService.createOrder(42L,
                new CreateOrderRequest(99L, "PREMIUM", "MONTHLY", 49900L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("another user");
        assertThatThrownBy(() -> paymentService.createOrder(42L,
                new CreateOrderRequest(42L, "FREE", "MONTHLY", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported payment plan");
        assertThatThrownBy(() -> paymentService.createOrder(42L,
                new CreateOrderRequest(42L, "PREMIUM", "WEEKLY", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported billing cycle");

        when(razorpayOrderClient.createOrder(any(Long.class), anyString(), anyString()))
                .thenThrow(new java.io.IOException("down"));
        assertThatThrownBy(() -> paymentService.createOrder(42L,
                new CreateOrderRequest(42L, "PREMIUM", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unable to reach Razorpay");
    }

    @Test
    void verifyPaymentHandlesAlreadyPaidInvalidMissingAndForeignOrders() {
        PaymentOrder paid = order("order_paid", 42L, PaymentOrder.Status.PAID);
        when(repository.findById("order_paid")).thenReturn(Optional.of(paid));
        assertThat(paymentService.verifyPayment(42L, new VerifyPaymentRequest("order_paid", "pay", "sig")).message())
                .contains("already verified");

        PaymentOrder invalid = order("order_bad", 42L, PaymentOrder.Status.CREATED);
        when(repository.findById("order_bad")).thenReturn(Optional.of(invalid));
        when(signatureVerifier.isValid("order_bad", "pay", "bad")).thenReturn(false);
        when(repository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThatThrownBy(() -> paymentService.verifyPayment(42L, new VerifyPaymentRequest("order_bad", "pay", "bad")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("signature is invalid");
        assertThat(invalid.getStatus()).isEqualTo(PaymentOrder.Status.FAILED);

        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> paymentService.verifyPayment(42L, new VerifyPaymentRequest("missing", "pay", "sig")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Payment order not found");

        PaymentOrder foreign = order("order_foreign", 99L, PaymentOrder.Status.CREATED);
        when(repository.findById("order_foreign")).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> paymentService.verifyPayment(42L, new VerifyPaymentRequest("order_foreign", "pay", "sig")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("another user");
    }

    @Test
    void listEndpointsValidateOwnershipAndAdminRole() {
        when(repository.findByUserIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(order("order_1", 42L, PaymentOrder.Status.CREATED)));
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(order("order_1", 42L, PaymentOrder.Status.CREATED)));

        assertThat(paymentService.listForUser(42L, null)).hasSize(1);
        assertThat(paymentService.listUserPaymentsForAdmin("ADMIN", 42L)).hasSize(1);
        assertThat(paymentService.listAllForAdmin("ADMIN")).hasSize(1);
        assertThatThrownBy(() -> paymentService.listAllForAdmin("USER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Admin access");
    }

    private PaymentOrder order(String id, Long userId, PaymentOrder.Status status) {
        PaymentOrder order = new PaymentOrder();
        order.setRazorpayOrderId(id);
        order.setUserId(userId);
        order.setPlan("PREMIUM");
        order.setBillingCycle(null);
        order.setAmount(49900L);
        order.setCurrency("INR");
        order.setStatus(status);
        return order;
    }
}
