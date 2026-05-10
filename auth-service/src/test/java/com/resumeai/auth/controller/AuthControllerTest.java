package com.resumeai.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resumeai.auth.dto.AuthDtos.AuthResponse;
import com.resumeai.auth.dto.AuthDtos.LoginRequest;
import com.resumeai.auth.dto.AuthDtos.MessageResponse;
import com.resumeai.auth.dto.AuthDtos.ProfileUpdateRequest;
import com.resumeai.auth.dto.AuthDtos.RegisterRequest;
import com.resumeai.auth.dto.AuthDtos.SubscriptionUpdateRequest;
import com.resumeai.auth.dto.AuthDtos.TokenRefreshRequest;
import com.resumeai.auth.model.AuditLog;
import com.resumeai.auth.model.User;
import com.resumeai.auth.service.AdminNotificationClient;
import com.resumeai.auth.service.AuditLogService;
import com.resumeai.auth.service.AuthService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AdminNotificationClient adminNotificationClient;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, auditLogService, adminNotificationClient);
        ReflectionTestUtils.setField(controller, "oauthAuthorizationBaseUrl", "http://localhost:8081");
        ReflectionTestUtils.setField(controller, "internalServiceKey", "secret-key");
    }

    @Test
    void publicAuthEndpointsDelegateAndAudit() {
        User user = user(9L, "user@example.com", "FREE", true);
        AuthResponse response = new AuthResponse(user, "access", "refresh");
        when(authService.register(any())).thenReturn(response);
        when(authService.login(any())).thenReturn(response);
        when(authService.refresh(any())).thenReturn(response);
        when(authService.logout(any())).thenReturn(new MessageResponse("bye"));

        assertEquals(response, controller.register(new RegisterRequest(
                "User", "user@example.com", "password123", "9999999999", "LOCAL", "USER", "FREE")));
        assertEquals(response, controller.login(new LoginRequest("user@example.com", "password123")));
        assertEquals(response, controller.refresh(new TokenRefreshRequest("refresh")));
        assertEquals("bye", controller.logout(new TokenRefreshRequest("refresh")).message());

        verify(auditLogService).recordAudit(9L, "user@example.com", "REGISTER", "User", "9", null, null);
        verify(auditLogService).recordAudit(9L, "user@example.com", "LOGIN", "User", "9", null, null);
    }

    @Test
    void profilePasswordSubscriptionAndDeactivateUseAuthenticatedUser() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("me@example.com", "n/a");
        auth.setDetails("7");
        User before = user(7L, "me@example.com", "FREE", true);
        User after = user(7L, "me@example.com", "PREMIUM", true);
        when(authService.getUserById(7L)).thenReturn(before);
        when(authService.updateProfile(any(), any())).thenReturn(after);
        when(authService.updateSubscription(any(), any())).thenReturn(after);
        when(authService.deactivateAccount(7L)).thenReturn(after);

        assertEquals(before, controller.getOwnProfile(auth));
        assertEquals(after, controller.updateOwnProfile(auth, new ProfileUpdateRequest("Me", "123", "")));
        assertEquals(after, controller.updateOwnSubscription(auth, new SubscriptionUpdateRequest("PREMIUM")));
        assertEquals(after, controller.deactivateOwnAccount(auth));

        assertEquals("7", controller.validate(auth).get("userId"));
    }

    @Test
    void internalSubscriptionRejectsBadKeyAndAcceptsGoodKey() {
        User before = user(1L, "a@example.com", "FREE", true);
        User after = user(1L, "a@example.com", "PREMIUM", true);
        when(authService.getUserById(1L)).thenReturn(before);
        when(authService.updateSubscription(any(), any())).thenReturn(after);

        assertThrows(ResponseStatusException.class,
                () -> controller.updateSubscriptionFromInternalService(
                        1L, new SubscriptionUpdateRequest("PREMIUM"), "wrong"));
        assertEquals(after, controller.updateSubscriptionFromInternalService(
                1L, new SubscriptionUpdateRequest("PREMIUM"), "secret-key"));
    }

    @Test
    void adminQueriesAndOauthProviderListWork() {
        User free = user(1L, "free@example.com", "FREE", true);
        User premium = user(2L, "premium@example.com", "PREMIUM", false);
        when(authService.getAllUsers()).thenReturn(List.of(free, premium));
        when(auditLogService.getAll()).thenReturn(List.of(new AuditLog()));
        when(auditLogService.getByAction("LOGIN")).thenReturn(List.of(new AuditLog()));

        assertEquals(2, controller.users().size());
        assertEquals(List.of(premium), controller.usersByPlan("premium"));
        assertEquals(2L, controller.analytics().get("totalUsers"));
        assertEquals(1L, controller.analytics().get("inactiveUsers"));
        assertEquals(1, controller.auditLogs(null, null, null, null).size());
        assertEquals(1, controller.auditLogs(null, null, null, "LOGIN").size());
        assertEquals("linkedin", controller.oauthProviders().get(1).get("provider"));
    }

    @Test
    void currentUserIdRejectsMissingDetails() {
        assertThrows(ResponseStatusException.class, () -> controller.getOwnProfile(null));
    }

    private User user(Long id, String email, String plan, boolean active) {
        User user = new User();
        user.setUserId(id);
        user.setEmail(email);
        user.setFullName("User");
        user.setRole("USER");
        user.setSubscriptionPlan(plan);
        user.setActive(active);
        return user;
    }
}
