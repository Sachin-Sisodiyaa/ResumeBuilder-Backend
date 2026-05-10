package com.resumeai.auth.service;

import com.resumeai.auth.model.User;
import com.resumeai.auth.repository.UserRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Dispatches post-registration side-effects (welcome email, in-app notification,
 * admin alert) on a background thread so the HTTP register response is never
 * delayed by downstream service latency or timeouts.
 *
 * <p>All methods are annotated {@link Async} and swallow every exception so that
 * a notification failure can never cause a registration to appear as an error.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncNotificationService {

    private final PasswordResetEmailService passwordResetEmailService;
    private final AdminNotificationClient adminNotificationClient;
    private final UserRepository userRepository;

    /**
     * Sends the welcome email + in-app welcome notification to the newly registered user.
     * Runs asynchronously; any failure is logged and silently suppressed.
     */
    @Async
    public void sendWelcomeNotificationsAsync(User user) {
        // 1. Welcome email via SMTP
        try {
            passwordResetEmailService.sendWelcomeEmail(user.getEmail(), user.getFullName());
        } catch (RuntimeException ex) {
            log.warn("Welcome email could not be sent to {}: {}", user.getEmail(), ex.getMessage());
        }

        // 2. In-app + email welcome notification via notification-service
        try {
            adminNotificationClient.notifyUser(user, Map.of(
                    "type", "WELCOME",
                    "title", "Welcome to ResumeAI",
                    "message", "Hi " + user.getFullName() + ",\n\nWelcome to ResumeAI! We are thrilled to have you with us.\n\n"
                            + "Your account is now active. You can begin building professional resumes, utilizing our AI assistant "
                            + "to optimize your content, performing ATS checks to ensure your applications stand out, and exporting "
                            + "your documents in a variety of professional formats.\n\nWe look forward to helping you reach your career goals!",
                    "channel", "ALL",
                    "relatedId", String.valueOf(user.getUserId()),
                    "relatedType", "User",
                    "actionUrl", "/dashboard"
            ));
        } catch (RuntimeException ex) {
            log.warn("Welcome notification could not be sent to {}: {}", user.getEmail(), ex.getMessage());
        }
    }

    /**
     * Notifies all admin users that a new user has registered.
     * Runs asynchronously; any failure is logged and silently suppressed.
     */
    @Async
    public void notifyAdminsOfNewRegistrationAsync(User newUser) {
        try {
            List<User> admins = userRepository.findAll().stream()
                    .filter(candidate -> "ADMIN".equalsIgnoreCase(candidate.getRole()))
                    .filter(candidate -> !java.util.Objects.equals(candidate.getUserId(), newUser.getUserId()))
                    .toList();
            adminNotificationClient.notifyAdminsOfNewUser(admins, newUser);
        } catch (RuntimeException ex) {
            log.warn("Admin notification could not be created for new user {}: {}",
                    newUser.getEmail(), ex.getMessage());
        }
    }
}
