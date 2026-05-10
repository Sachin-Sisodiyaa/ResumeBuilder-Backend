package com.resumeai.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resumeai.auth.model.User;
import com.resumeai.auth.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsyncNotificationServiceTest {

    private final PasswordResetEmailService emailService = org.mockito.Mockito.mock(PasswordResetEmailService.class);
    private final AdminNotificationClient notificationClient = org.mockito.Mockito.mock(AdminNotificationClient.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final AsyncNotificationService service =
            new AsyncNotificationService(emailService, notificationClient, userRepository);

    @Test
    void sendsWelcomeEmailAndNotification() {
        User user = user(1L, "USER", "user@example.com");

        service.sendWelcomeNotificationsAsync(user);

        verify(emailService).sendWelcomeEmail("user@example.com", "User 1");
        verify(notificationClient).notifyUser(any(User.class), anyMap());
    }

    @Test
    void welcomeNotificationSwallowsDownstreamFailures() {
        User user = user(1L, "USER", "user@example.com");
        doThrow(new RuntimeException("smtp")).when(emailService).sendWelcomeEmail(any(), any());
        doThrow(new RuntimeException("notification")).when(notificationClient).notifyUser(any(), anyMap());

        service.sendWelcomeNotificationsAsync(user);

        verify(emailService).sendWelcomeEmail("user@example.com", "User 1");
        verify(notificationClient).notifyUser(any(User.class), anyMap());
    }

    @Test
    void notifiesAdminsExceptTheNewUser() {
        User newUser = user(1L, "ADMIN", "new@example.com");
        User admin = user(2L, "ADMIN", "admin@example.com");
        User regular = user(3L, "USER", "regular@example.com");
        when(userRepository.findAll()).thenReturn(List.of(newUser, admin, regular));

        service.notifyAdminsOfNewRegistrationAsync(newUser);

        verify(notificationClient).notifyAdminsOfNewUser(List.of(admin), newUser);
    }

    @Test
    void adminNotificationSwallowsRepositoryFailures() {
        User newUser = user(1L, "USER", "new@example.com");
        when(userRepository.findAll()).thenThrow(new RuntimeException("db"));

        service.notifyAdminsOfNewRegistrationAsync(newUser);

        verify(notificationClient, never()).notifyAdminsOfNewUser(anyList(), any(User.class));
    }

    private User user(Long id, String role, String email) {
        User user = new User();
        user.setUserId(id);
        user.setFullName("User " + id);
        user.setEmail(email);
        user.setRole(role);
        user.setActive(true);
        user.setSubscriptionPlan("FREE");
        return user;
    }
}
