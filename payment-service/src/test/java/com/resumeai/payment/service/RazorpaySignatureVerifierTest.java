package com.resumeai.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RazorpaySignatureVerifierTest {
    @Test
    void validatesExpectedHmacSignature() {
        RazorpaySignatureVerifier verifier = new RazorpaySignatureVerifier("secret");

        boolean valid = verifier.isValid(
                "order_123",
                "pay_123",
                "13f113268a0357923e6390e6773754dc39c991f05a999bcaf04c161c59aeaaf8");

        assertThat(valid).isTrue();
        assertThat(verifier.isValid("order_123", "pay_123", "bad")).isFalse();
    }
}
