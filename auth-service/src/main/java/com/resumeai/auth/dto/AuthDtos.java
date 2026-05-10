package com.resumeai.auth.dto;

import com.resumeai.auth.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Pattern(regexp = "^$|^\\d{10,15}$", message = "Phone must contain 10 to 15 digits") String phone,
            String role,
            String provider,
            String subscriptionPlan) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record TokenRefreshRequest(@NotBlank String refreshToken) {
    }

    public record ProfileUpdateRequest(
            @Size(min = 2, max = 120) String fullName,
            @Pattern(regexp = "^$|^\\d{10,15}$", message = "Phone must contain 10 to 15 digits") String phone,
            @Pattern(regexp = "^$|^https?://.+", message = "Picture URL must be http or https") String pictureUrl) {
    }

    public record PasswordChangeRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 128) String newPassword) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(@NotBlank @Email String email,
                                       @NotBlank @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits") String otp,
                                       @NotBlank @Size(min = 8, max = 128) String newPassword) {
    }

    public record SubscriptionUpdateRequest(@NotBlank @Pattern(regexp = "FREE|PREMIUM", flags = Pattern.Flag.CASE_INSENSITIVE)
                                            String subscriptionPlan) {
    }

    public record RoleUpdateRequest(@NotBlank @Pattern(regexp = "USER|ADMIN", flags = Pattern.Flag.CASE_INSENSITIVE)
                                    String role) {
    }

    public record MessageResponse(String message) {
    }

    public record AuthResponse(User user, String accessToken, String refreshToken) {
    }
}
