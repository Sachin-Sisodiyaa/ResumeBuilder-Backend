package com.resumeai.payment.service;

import java.io.IOException;

public interface RazorpayOrderClient {
    RazorpayOrder createOrder(long amount, String currency, String receipt) throws IOException, InterruptedException;

    record RazorpayOrder(String id, long amount, String currency, String status) {
    }
}
