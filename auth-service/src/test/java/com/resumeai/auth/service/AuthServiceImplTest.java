package com.resumeai.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resumeai.auth.dto.AuthDtos.ForgotPasswordRequest;
import com.resumeai.auth.dto.AuthDtos.LoginRequest;
import com.resumeai.auth.dto.AuthDtos.PasswordChangeRequest;
import com.resumeai.auth.dto.AuthDtos.ProfileUpdateRequest;
import com.resumeai.auth.dto.AuthDtos.RegisterRequest;
import com.resumeai.auth.dto.AuthDtos.ResetPasswordRequest;
import com.resumeai.auth.dto.AuthDtos.RoleUpdateRequest;
import com.resumeai.auth.dto.AuthDtos.SubscriptionUpdateRequest;
import com.resumeai.auth.dto.AuthDtos.TokenRefreshRequest;
import com.resumeai.auth.model.User;
import com.resumeai.auth.repository.UserRepository;
import com.resumeai.auth.security.JwtUtil;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetEmailService passwordResetEmailService;
    @Mock
    private AdminNotificationClient adminNotificationClient;
    @Mock
    private AsyncNotificationService asyncNotificationService;

    /** Real BCrypt encoder — intentionally NOT mocked so password logic is exercised. */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** Real JwtUtil with test secret, NOT mocked. */
    private JwtUtil jwtUtil;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() throws Exception {
        // Seed-admin guard: admin lookup returns empty so seeding is skipped
        when(userRepository.findByEmail("admin@resumeai.local")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getUserId() == null) {
                user.setUserId(1L);
            }
            return user;
        });

        // Create a real JwtUtil with a test secret
        jwtUtil = new JwtUtil();
        setField(jwtUtil, "secret",
                "test-secret-key-for-unit-tests-must-be-long-enough-for-hs256");
        setField(jwtUtil, "expirationMs", 3_600_000L);

        authService = new AuthServiceImpl(userRepository, passwordResetEmailService, adminNotificationClient,
                asyncNotificationService, passwordEncoder, jwtUtil);
        setField(authService, "resetPasswordBaseUrl", "http://localhost:3000/reset-password");
        setField(authService, "resetPasswordExpiryMinutes", 30L);
    }

    // -------------------------------------------------------------------------
    // Register
    // -------------------------------------------------------------------------

    @Test
    void registerCreatesActiveUserWithTokens() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        var response = authService.register(
                new RegisterRequest("User", "user@example.com", "secret", "999", null, null, null));

        assertEquals("user@example.com", response.user().getEmail());
        assertEquals("FREE", response.user().getSubscriptionPlan());
        assertNotNull(response.accessToken());
        verify(userRepository).save(any(User.class));
        // Welcome + admin notifications are dispatched asynchronously via AsyncNotificationService
        verify(asyncNotificationService).sendWelcomeNotificationsAsync(any(User.class));
        verify(asyncNotificationService).notifyAdminsOfNewRegistrationAsync(any(User.class));
    }

    @Test
    void registerIgnoresClientRoleAndPlan() {
        when(userRepository.findByEmail("admin-attempt@example.com")).thenReturn(Optional.empty());

        var response = authService.register(
                new RegisterRequest("User", "admin-attempt@example.com", "secret", "999", "ADMIN", null, "PREMIUM"));

        assertEquals("USER", response.user().getRole());
        assertEquals("FREE", response.user().getSubscriptionPlan());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        User existing = new User();
        existing.setEmail("dup@example.com");
        when(userRepository.findByEmail("dup@example.com")).thenReturn(Optional.of(existing));

        RegisterRequest req1 = new RegisterRequest("X", "dup@example.com", "p", null, null, null, null);
        assertThrows(ResponseStatusException.class,
                () -> authService.register(req1));
    }

    @Test
    void registerMapsDatabaseDuplicateToConflict() {
        when(userRepository.findByEmail("race@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        RegisterRequest req2 = new RegisterRequest("Race", "race@example.com", "p", null, null, null, null);
        assertThrows(ResponseStatusException.class,
                () -> authService.register(req2));
    }

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    @Test
    void loginReturnsAuthResponseForValidCredentials() {
        User user = new User();
        user.setUserId(7L);
        user.setEmail("user@example.com");
        user.setPasswordHash(passwordEncoder.encode("secret"));
        user.setRole("USER");
        user.setActive(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        var response = authService.login(new LoginRequest("user@example.com", "secret"));

        assertEquals(7L, response.user().getUserId());
        assertNotNull(response.refreshToken());
        // Access token must be a valid JWT
        assertNotNull(response.accessToken());
        assertFalse(response.accessToken().isBlank());
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User();
        user.setUserId(8L);
        user.setEmail("user@example.com");
        user.setPasswordHash(passwordEncoder.encode("correct"));
        user.setActive(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        LoginRequest loginReq = new LoginRequest("user@example.com", "wrong");
        assertThrows(ResponseStatusException.class,
                () -> authService.login(loginReq));
    }

    @Test
    void loginRejectsMissingAndInactiveUsers() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        User inactive = user(12L);
        inactive.setActive(false);
        when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(inactive));

        LoginRequest missingReq = new LoginRequest("missing@example.com", "secret");
        assertThrows(ResponseStatusException.class,
                () -> authService.login(missingReq));
        
        LoginRequest inactiveReq = new LoginRequest("inactive@example.com", "secret");
        assertThrows(ResponseStatusException.class,
                () -> authService.login(inactiveReq));
        assertThrows(ResponseStatusException.class, () -> authService.createSession(inactive));
    }

    @Test
    void refreshAndLogoutManageRefreshTokens() {
        User user = user(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        var session = authService.createSession(user);
        var refreshed = authService.refresh(new TokenRefreshRequest(session.refreshToken()));
        assertEquals(7L, refreshed.user().getUserId());

        assertEquals("Logged out successfully.", authService.logout(new TokenRefreshRequest(session.refreshToken())).message());
        TokenRefreshRequest refreshReq = new TokenRefreshRequest(session.refreshToken());
        assertThrows(ResponseStatusException.class,
                () -> authService.refresh(refreshReq));
    }

    // -------------------------------------------------------------------------
    // Deactivate
    // -------------------------------------------------------------------------

    @Test
    void deactivateMarksUserInactive() {
        User user = new User();
        user.setUserId(9L);
        user.setActive(true);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));

        User updated = authService.deactivateAccount(9L);

        assertFalse(updated.isActive());
        verify(userRepository).save(user);
    }

    @Test
    void accountManagementUpdatesExpectedFields() {
        User user = user(9L);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        user.setPictureUrl("old.png");
        User unchangedProfile = authService.updateProfile(9L, new ProfileUpdateRequest("", "", null));
        assertEquals("old.png", unchangedProfile.getPictureUrl());

        User profile = authService.updateProfile(9L, new ProfileUpdateRequest("New Name", "111", ""));
        assertEquals("New Name", profile.getFullName());
        assertNull(profile.getPictureUrl());

        User premium = authService.updateSubscription(9L, new SubscriptionUpdateRequest("premium"));
        assertEquals("PREMIUM", premium.getSubscriptionPlan());

        User samePlan = authService.updateSubscription(9L, new SubscriptionUpdateRequest("PREMIUM"));
        assertEquals("PREMIUM", samePlan.getSubscriptionPlan());

        User admin = authService.updateRole(9L, new RoleUpdateRequest("admin"));
        assertEquals("ADMIN", admin.getRole());
        assertEquals("PREMIUM", admin.getSubscriptionPlan());

        User active = authService.reactivateAccount(9L);
        assertTrue(active.isActive());

        authService.deleteAccount(9L);
        verify(userRepository).deleteById(9L);
    }

    @Test
    void protectedAdminCannotBeChangedOrDeleted() throws Exception {
        User admin = user(99L);
        admin.setEmail("admin@example.com");
        admin.setRole("ADMIN");
        setField(authService, "protectedAdminEmail", "admin@example.com");
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));

        RoleUpdateRequest roleRequest = new RoleUpdateRequest("USER");
        assertThrows(ResponseStatusException.class, () -> authService.updateRole(99L, roleRequest));
        assertThrows(ResponseStatusException.class, () -> authService.deactivateAccount(99L));
        assertThrows(ResponseStatusException.class, () -> authService.deleteAccount(99L));
    }

    @Test
    void bootstrapAdminCreatesAdminOnlyWhenEnabledAndConfigured() throws Exception {
        setField(authService, "bootstrapAdminEnabled", true);
        setField(authService, "bootstrapAdminEmail", "admin@example.com");
        setField(authService, "bootstrapAdminPassword", "AdminPass123!");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());

        authService.init();

        verify(userRepository).save(argThat(user ->
                "admin@example.com".equals(user.getEmail())
                        && "ADMIN".equals(user.getRole())
                        && "PREMIUM".equals(user.getSubscriptionPlan())));
    }

    @Test
    void bootstrapAdminSkipsWhenDisabledOrExisting() throws Exception {
        authService.init();

        setField(authService, "bootstrapAdminEnabled", true);
        setField(authService, "bootstrapAdminEmail", "admin@example.com");
        setField(authService, "bootstrapAdminPassword", "AdminPass123!");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(user(2L)));

        authService.init();
        verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
    }

    @Test
    void bootstrapAdminFailsFastWhenEnabledWithoutCredentials() throws Exception {
        setField(authService, "bootstrapAdminEnabled", true);
        setField(authService, "bootstrapAdminEmail", "");
        setField(authService, "bootstrapAdminPassword", "");

        assertThrows(IllegalStateException.class, () -> authService.init());
    }

    @Test
    void profilePictureUploadAndLoadValidateStoragePaths() throws Exception {
        Path tempDir = Files.createTempDirectory("profile-pictures");
        setField(authService, "profilePictureStorageDir", tempDir.toString());
        setField(authService, "profilePicturePublicBaseUrl", "http://cdn.example.com/");
        setField(authService, "profilePictureMaxBytes", 1024L);
        User user = user(44L);
        when(userRepository.findById(44L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile picture = new MockMultipartFile(
                "file", "avatar.PNG", "image/png", "image-bytes".getBytes());

        User updated = authService.uploadProfilePicture(44L, picture);

        assertTrue(updated.getPictureUrl()
                .startsWith("http://cdn.example.com/api/v1/auth/profile-picture/user-44-"));
        String fileName = updated.getPictureUrl().substring(updated.getPictureUrl().lastIndexOf('/') + 1);
        assertNotNull(authService.loadProfilePicture(fileName));
        assertThrows(ResponseStatusException.class, () -> authService.loadProfilePicture("../secret.png"));
        assertThrows(ResponseStatusException.class, () -> authService.loadProfilePicture("missing.png"));
    }

    @Test
    void profilePictureValidationRejectsBadInputs() {
        User user = user(45L);
        when(userRepository.findById(45L)).thenReturn(Optional.of(user));
        setUncheckedField(authService, "profilePictureMaxBytes", 3L);

        assertThrows(ResponseStatusException.class, () -> authService.uploadProfilePicture(45L, null));
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        assertThrows(ResponseStatusException.class, () -> authService.uploadProfilePicture(45L, empty));
        MockMultipartFile tooLarge = new MockMultipartFile("file", "large.png", "image/png", "large".getBytes());
        assertThrows(ResponseStatusException.class, () -> authService.uploadProfilePicture(45L, tooLarge));

        setUncheckedField(authService, "profilePictureMaxBytes", 1024L);
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "plain".getBytes());
        assertThrows(ResponseStatusException.class, () -> authService.uploadProfilePicture(45L, textFile));
    }

    @Test
    void profilePictureExtensionFallsBackToContentType() throws Exception {
        Path tempDir = Files.createTempDirectory("profile-pictures-fallback");
        setField(authService, "profilePictureStorageDir", tempDir.toString());
        setField(authService, "profilePicturePublicBaseUrl", "");
        setField(authService, "profilePictureMaxBytes", 1024L);
        User user = user(46L);
        when(userRepository.findById(46L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        for (String contentType : List.of("image/webp", "image/gif", "image/jpeg")) {
            MockMultipartFile picture = new MockMultipartFile(
                    "file", "avatar", contentType, "image-bytes".getBytes());

            User updated = authService.uploadProfilePicture(46L, picture);

            assertTrue(updated.getPictureUrl().startsWith("/api/v1/auth/profile-picture/user-46-"));
        }
    }

    @Test
    void profileValidationAndPasswordChangePaths() {
        User user = user(9L);
        user.setPasswordHash(passwordEncoder.encode("old"));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(ResponseStatusException.class, () -> authService.getUserById(404L));
        SubscriptionUpdateRequest subReq = new SubscriptionUpdateRequest("GOLD");
        assertThrows(ResponseStatusException.class,
                () -> authService.updateSubscription(9L, subReq));
        RoleUpdateRequest roleReq = new RoleUpdateRequest("OWNER");
        assertThrows(ResponseStatusException.class,
                () -> authService.updateRole(9L, roleReq));
        PasswordChangeRequest passReq = new PasswordChangeRequest("bad", "new");
        assertThrows(ResponseStatusException.class,
                () -> authService.changePassword(9L, passReq));

        User changed = authService.changePassword(9L, new PasswordChangeRequest("old", "new"));
        assertTrue(passwordEncoder.matches("new", changed.getPasswordHash()));
    }

    // -------------------------------------------------------------------------
    // Password reset
    // -------------------------------------------------------------------------

    @Test
    void forgotPasswordSendsResetEmailForActiveUser() {
        User user = new User();
        user.setUserId(11L);
        user.setEmail("user@example.com");
        user.setFullName("User");
        user.setActive(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        var response = authService.requestPasswordReset(new ForgotPasswordRequest("user@example.com"));

        assertEquals("If the email is registered, a password reset OTP has been sent.", response.message());
        verify(passwordResetEmailService).sendPasswordResetOtp(
                eq("user@example.com"),
                eq("User"),
                any(String.class),
                eq(30L)
        );
    }

    @Test
    void forgotPasswordDoesNotRevealMissingOrInactiveUsers() {
        User inactive = user(20L);
        inactive.setActive(false);
        when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(inactive));
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertEquals("If the email is registered, a password reset OTP has been sent.",
                authService.requestPasswordReset(new ForgotPasswordRequest("inactive@example.com")).message());
        assertEquals("If the email is registered, a password reset OTP has been sent.",
                authService.requestPasswordReset(new ForgotPasswordRequest("missing@example.com")).message());
    }

    @Test
    void resetPasswordUpdatesStoredHashForValidToken() {
        User user = new User();
        user.setUserId(15L);
        user.setEmail("user@example.com");
        user.setPasswordHash(passwordEncoder.encode("old-secret"));
        user.setActive(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(15L)).thenReturn(Optional.of(user));

        authService.requestPasswordReset(new ForgotPasswordRequest("user@example.com"));
        // Key format is "email:otp" — extract just the OTP part
        String tokenKey = extractTokenFromService(authService);
        String otp = tokenKey.contains(":") ? tokenKey.substring(tokenKey.lastIndexOf(':') + 1) : tokenKey;

        var response = authService.resetPassword(
                new ResetPasswordRequest("user@example.com", otp, "new-secret"));

        assertEquals("Password has been reset successfully.", response.message());
        // Password must now match the new value via BCrypt
        assertTrue(passwordEncoder.matches("new-secret", user.getPasswordHash()));
    }

    @Test
    void resetPasswordRejectsUnknownToken() {
        ResetPasswordRequest resetReq = new ResetPasswordRequest("user@example.com", "000000", "new-secret");
        assertThrows(ResponseStatusException.class,
                () -> authService.resetPassword(resetReq));
    }

    @Test
    void resetPasswordRejectsInactiveAndMismatchedUsers() {
        User user = user(21L);
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(21L)).thenReturn(Optional.of(user));

        authService.requestPasswordReset(new ForgotPasswordRequest("user@example.com"));
        String otp = otpFromStoredToken();

        user.setActive(false);
        ResetPasswordRequest req1 = new ResetPasswordRequest("user@example.com", otp, "new-secret");
        assertThrows(ResponseStatusException.class,
                () -> authService.resetPassword(req1));

        user.setActive(true);
        user.setEmail("other@example.com");
        ResetPasswordRequest req2 = new ResetPasswordRequest("user@example.com", otp, "new-secret");
        assertThrows(ResponseStatusException.class,
                () -> authService.resetPassword(req2));
    }

    @Test
    void redisBackedPasswordResetStoresReadsAndCleansTokens() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AuthServiceImpl redisAuthService = new AuthServiceImpl(userRepository, passwordResetEmailService,
                adminNotificationClient, asyncNotificationService, passwordEncoder, jwtUtil, redisProvider);
        setField(redisAuthService, "resetPasswordExpiryMinutes", 30L);

        User user = user(22L);
        user.setEmail("redis@example.com");
        when(userRepository.findByEmail("redis@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(22L)).thenReturn(Optional.of(user));
        when(valueOperations.get("auth:password-reset:user:22")).thenReturn("old-token");

        redisAuthService.requestPasswordReset(new ForgotPasswordRequest("redis@example.com"));
        String token = extractTokenFromService(redisAuthService);
        String otp = token.substring(token.lastIndexOf(':') + 1);
        long expiresAt = Instant.now().plusSeconds(300).toEpochMilli();
        when(valueOperations.get("auth:password-reset:token:" + token)).thenReturn("22:" + expiresAt);
        when(valueOperations.get("auth:password-reset:user:22")).thenReturn(token);

        var response = redisAuthService.resetPassword(new ResetPasswordRequest("redis@example.com", otp, "new"));

        assertEquals("Password has been reset successfully.", response.message());
        verify(redisTemplate, times(2)).delete("auth:password-reset:token:old-token");
        verify(redisTemplate).delete("auth:password-reset:token:" + token);
        verify(redisTemplate, times(2)).delete("auth:password-reset:user:22");
    }

    @Test
    void redisPasswordResetFailuresFallBackSafely() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AuthServiceImpl redisAuthService = new AuthServiceImpl(userRepository, passwordResetEmailService,
                adminNotificationClient, asyncNotificationService, passwordEncoder, jwtUtil, redisProvider);
        setField(redisAuthService, "resetPasswordExpiryMinutes", 30L);

        User user = user(23L);
        user.setEmail("fallback@example.com");
        when(userRepository.findByEmail("fallback@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(23L)).thenReturn(Optional.of(user));
        when(valueOperations.get(any(String.class))).thenThrow(new IllegalStateException("redis down"));

        redisAuthService.requestPasswordReset(new ForgotPasswordRequest("fallback@example.com"));
        String otp = extractTokenFromService(redisAuthService).substring(
                extractTokenFromService(redisAuthService).lastIndexOf(':') + 1);

        assertEquals("Password has been reset successfully.",
                redisAuthService.resetPassword(new ResetPasswordRequest("fallback@example.com", otp, "new")).message());

        doThrow(new IllegalStateException("redis down")).when(redisTemplate).delete(any(String.class));
        redisAuthService.changePassword(23L, new PasswordChangeRequest("new", "next"));
    }

    // -------------------------------------------------------------------------
    // JWT validation
    // -------------------------------------------------------------------------

    @Test
    void generatedTokenIsValidAndContainsCorrectClaims() {
        // Verify that JwtUtil generates a parseable token
        String token = jwtUtil.generateToken(42L, "test@example.com", "USER");

        assertTrue(jwtUtil.validateToken(token));
        assertEquals(42L, jwtUtil.getUserIdFromToken(token));
        assertEquals("test@example.com", jwtUtil.getEmailFromToken(token));
        assertEquals("USER", jwtUtil.getRoleFromToken(token));
        assertEquals("FREE", jwtUtil.getPlanFromToken(token));
        assertFalse(jwtUtil.validateToken("not-a-token"));
        assertNull(jwtUtil.getUserIdFromToken("not-a-token"));
        assertNull(jwtUtil.getEmailFromToken("not-a-token"));
        assertNull(jwtUtil.getRoleFromToken("not-a-token"));
        assertNull(jwtUtil.getPlanFromToken("not-a-token"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setUncheckedField(Object target, String fieldName, Object value) {
        try {
            setField(target, fieldName, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        try {
            return cls.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (cls.getSuperclass() != null) {
                return findField(cls.getSuperclass(), name);
            }
            throw e;
        }
    }

    private User user(Long id) {
        User user = new User();
        user.setUserId(id);
        user.setEmail("user" + id + "@example.com");
        user.setFullName("User " + id);
        user.setPasswordHash(passwordEncoder.encode("secret"));
        user.setRole("USER");
        user.setSubscriptionPlan("FREE");
        user.setActive(true);
        return user;
    }

    @SuppressWarnings("unchecked")
    private static String extractTokenFromService(AuthServiceImpl svc) {
        try {
            Field field = AuthServiceImpl.class.getDeclaredField("passwordResetTokens");
            field.setAccessible(true);
            return ((java.util.Map<String, ?>) field.get(svc)).keySet().iterator().next();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private String otpFromStoredToken() {
        String tokenKey = extractTokenFromService(authService);
        return tokenKey.contains(":") ? tokenKey.substring(tokenKey.lastIndexOf(':') + 1) : tokenKey;
    }
}
