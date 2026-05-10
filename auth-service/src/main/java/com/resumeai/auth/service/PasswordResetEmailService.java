package com.resumeai.auth.service;

public interface PasswordResetEmailService {
    void sendPasswordResetOtp(String email, String fullName, String otp, long expiryMinutes);

    void sendWelcomeEmail(String email, String fullName);

    void sendAccountDeactivatedEmail(String email, String fullName);

    void sendPlanUpdatedEmail(String email, String fullName, String subscriptionPlan);
}
