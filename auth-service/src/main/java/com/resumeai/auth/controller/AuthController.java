package com.resumeai.auth.controller;

import com.resumeai.auth.dto.AuthDtos.AuthResponse;

import com.resumeai.auth.dto.AuthDtos.ForgotPasswordRequest;
import com.resumeai.auth.dto.AuthDtos.LoginRequest;
import com.resumeai.auth.dto.AuthDtos.MessageResponse;
import com.resumeai.auth.dto.AuthDtos.PasswordChangeRequest;
import com.resumeai.auth.dto.AuthDtos.ProfileUpdateRequest;
import com.resumeai.auth.dto.AuthDtos.RegisterRequest;
import com.resumeai.auth.dto.AuthDtos.ResetPasswordRequest;
import com.resumeai.auth.dto.AuthDtos.RoleUpdateRequest;
import com.resumeai.auth.dto.AuthDtos.SubscriptionUpdateRequest;
import com.resumeai.auth.dto.AuthDtos.TokenRefreshRequest;
import com.resumeai.auth.model.AuditLog;
import com.resumeai.auth.model.User;
import com.resumeai.auth.service.AdminNotificationClient;
import com.resumeai.auth.service.AuditLogService;
import com.resumeai.auth.service.AuthService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Auth/User REST resource — exposes /api/v1/auth.
 *
 * Public: register, login, refresh, forgot-password, reset-password.
 * Authenticated: profile, password, subscription, deactivate.
 * Admin-only: /users/**, audit-logs.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final AdminNotificationClient adminNotificationClient;

    @Value("${app.oauth2.authorization-base-url:http://localhost:8081}")
    private String oauthAuthorizationBaseUrl;

    @Value("${app.internal-service-key}")
    private String internalServiceKey;

    // ------------------------------------------------------------------ public

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse resp = authService.register(request);
        recordAuditSafely(resp.user().getUserId(), resp.user().getEmail(),
                "REGISTER", "User", String.valueOf(resp.user().getUserId()));
        return resp;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthResponse resp = authService.login(request);
        recordAuditSafely(resp.user().getUserId(), resp.user().getEmail(),
                "LOGIN", "User", String.valueOf(resp.user().getUserId()));
        return resp;
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public MessageResponse logout(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.logout(request);
    }

    @GetMapping("/validate")
    public Map<String, Object> validate(Authentication authentication) {
        return Map.of(
                "authenticated", authentication != null && authentication.isAuthenticated(),
                "userId", authentication == null ? "" : authentication.getDetails(),
                "email", authentication == null ? "" : authentication.getName()
        );
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.requestPasswordReset(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    // ---------------------------------------------------- authenticated user

    @GetMapping("/profile/{userId}")
    @PreAuthorize("#userId == authentication.details or hasRole('ADMIN')")
    public User getProfile(@PathVariable("userId") Long userId) {
        return authService.getUserById(userId);
    }

    @GetMapping("/profile")
    public User getOwnProfile(Authentication authentication) {
        return authService.getUserById(currentUserId(authentication));
    }

    @PutMapping("/profile/{userId}")
    @PreAuthorize("#userId == authentication.details or hasRole('ADMIN')")
    public User updateProfile(@PathVariable("userId") Long userId,
                               @Valid @RequestBody ProfileUpdateRequest request) {
        User updated = authService.updateProfile(userId, request);
        recordAuditSafely(userId, updated.getEmail(),
                "PROFILE_UPDATE", "User", String.valueOf(userId));
        return updated;
    }

    @PutMapping("/profile")
    public User updateOwnProfile(Authentication authentication,
                                 @Valid @RequestBody ProfileUpdateRequest request) {
        Long userId = currentUserId(authentication);
        User updated = authService.updateProfile(userId, request);
        recordAuditSafely(userId, updated.getEmail(),
                "PROFILE_UPDATE", "User", String.valueOf(userId));
        return updated;
    }

    @PostMapping(value = "/profile/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public User uploadOwnProfilePicture(Authentication authentication,
                                        @RequestParam("file") MultipartFile file) {
        Long userId = currentUserId(authentication);
        User updated = authService.uploadProfilePicture(userId, file);
        recordAuditSafely(userId, updated.getEmail(),
                "PROFILE_PICTURE_UPLOAD", "User", String.valueOf(userId));
        return updated;
    }

    @PostMapping(value = "/profile/{userId}/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("#userId == authentication.details or hasRole('ADMIN')")
    public User uploadProfilePicture(@PathVariable("userId") Long userId,
                                     @RequestParam("file") MultipartFile file) {
        User updated = authService.uploadProfilePicture(userId, file);
        recordAuditSafely(userId, updated.getEmail(),
                "PROFILE_PICTURE_UPLOAD", "User", String.valueOf(userId));
        return updated;
    }

    @GetMapping("/profile-picture/{fileName:.+}")
    public ResponseEntity<Resource> getProfilePicture(@PathVariable("fileName") String fileName) {
        Resource resource = authService.loadProfilePicture(fileName);
        return ResponseEntity.ok()
                .contentType(mediaTypeFor(fileName))
                .body(resource);
    }

    @PutMapping("/password/{userId}")
    @PreAuthorize("#userId == authentication.details or hasRole('ADMIN')")
    public User changePassword(@PathVariable("userId") Long userId,
                               @Valid @RequestBody PasswordChangeRequest request) {
        User updated = authService.changePassword(userId, request);
        recordAuditSafely(userId, updated.getEmail(),
                "PASSWORD_CHANGE", "User", String.valueOf(userId));
        return updated;
    }

    @PutMapping("/password")
    public User changeOwnPassword(Authentication authentication,
                                  @Valid @RequestBody PasswordChangeRequest request) {
        Long userId = currentUserId(authentication);
        User updated = authService.changePassword(userId, request);
        recordAuditSafely(userId, updated.getEmail(),
                "PASSWORD_CHANGE", "User", String.valueOf(userId));
        return updated;
    }

    @PutMapping("/subscription/{userId}")
    @PreAuthorize("#userId == authentication.details or hasRole('ADMIN')")
    public User updateSubscription(@PathVariable("userId") Long userId,
                                    @Valid @RequestBody SubscriptionUpdateRequest request) {
        User before = authService.getUserById(userId);
        User updated = authService.updateSubscription(userId, request);
        recordAuditSafely(userId, updated.getEmail(),
                "SUBSCRIPTION_CHANGE", "User", String.valueOf(userId),
                before.getSubscriptionPlan(), updated.getSubscriptionPlan());
        return updated;
    }

    @PutMapping("/subscription")
    public User updateOwnSubscription(Authentication authentication,
                                      @Valid @RequestBody SubscriptionUpdateRequest request) {
        Long userId = currentUserId(authentication);
        User before = authService.getUserById(userId);
        User updated = authService.updateSubscription(userId, request);
        recordAuditSafely(userId, updated.getEmail(),
                "SUBSCRIPTION_CHANGE", "User", String.valueOf(userId),
                before.getSubscriptionPlan(), updated.getSubscriptionPlan());
        return updated;
    }

    @PutMapping("/internal/subscription/{userId}")
    public User updateSubscriptionFromInternalService(
            @PathVariable("userId") Long userId,
            @RequestBody SubscriptionUpdateRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Internal-Service-Key", required = false) String serviceKey) {
        if (!java.util.Objects.equals(internalServiceKey, serviceKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal service key");
        }
        User before = authService.getUserById(userId);
        User updated = authService.updateSubscription(userId, request);
        recordAuditSafely(userId, updated.getEmail(),
                "SUBSCRIPTION_PAYMENT", "User", String.valueOf(userId),
                before.getSubscriptionPlan(), updated.getSubscriptionPlan());
        return updated;
    }

    @PutMapping("/deactivate/{userId}")
    @PreAuthorize("#userId == authentication.details or hasRole('ADMIN')")
    public User deactivate(@PathVariable("userId") Long userId) {
        User updated = authService.deactivateAccount(userId);
        recordAuditSafely(userId, updated.getEmail(),
                "DEACTIVATE", "User", String.valueOf(userId));
        return updated;
    }

    @PutMapping("/deactivate")
    public User deactivateOwnAccount(Authentication authentication) {
        Long userId = currentUserId(authentication);
        User updated = authService.deactivateAccount(userId);
        recordAuditSafely(userId, updated.getEmail(),
                "DEACTIVATE", "User", String.valueOf(userId));
        return updated;
    }

    @PutMapping("/reactivate/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public User reactivate(@PathVariable("userId") Long userId) {
        User updated = authService.reactivateAccount(userId);
        recordAuditSafely(userId, updated.getEmail(),
                "REACTIVATE", "User", String.valueOf(userId));
        return updated;
    }

    @PutMapping("/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public User updateRole(@PathVariable("userId") Long userId,
                           @Valid @RequestBody RoleUpdateRequest request) {
        User before = authService.getUserById(userId);
        User updated = authService.updateRole(userId, request);
        recordAuditSafely(userId, updated.getEmail(),
                "ROLE_CHANGE", "User", String.valueOf(userId),
                before.getRole(), updated.getRole());
        return updated;
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable("userId") Long userId) {
        deleteUserById(userId);
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAdminUser(@PathVariable("userId") Long userId) {
        deleteUserById(userId);
    }

    private void deleteUserById(Long userId) {
        User user = authService.getUserById(userId);
        recordAuditSafely(userId, user.getEmail(),
                "DELETE_ACCOUNT", "User", String.valueOf(userId));
        authService.deleteAccount(userId);
    }

    // ----------------------------------------------------------- admin-only

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> users() {
        return authService.getAllUsers();
    }

    @GetMapping("/users/by-plan")
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> usersByPlan(@RequestParam("plan") String plan) {
        return authService.getAllUsers().stream()
                .filter(u -> plan.equalsIgnoreCase(u.getSubscriptionPlan()))
                .toList();
    }

    @GetMapping("/users/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> analytics() {
        List<User> all = authService.getAllUsers();
        long total   = all.size();
        long premium = all.stream().filter(u -> "PREMIUM".equalsIgnoreCase(u.getSubscriptionPlan())).count();
        long free    = all.stream().filter(u -> "FREE".equalsIgnoreCase(u.getSubscriptionPlan())).count();
        long active  = all.stream().filter(User::isActive).count();
        return Map.of(
                "totalUsers", total,
                "premiumUsers", premium,
                "freeUsers", free,
                "activeUsers", active,
                "inactiveUsers", total - active
        );
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLog> auditLogs(
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "entityId", required = false) String entityId,
            @RequestParam(value = "actorId", required = false) Long actorId,
            @RequestParam(value = "action", required = false) String action) {
        if (entityType != null && entityId != null) {
            return auditLogService.getByEntity(entityType, entityId);
        }
        if (actorId != null) {
            return auditLogService.getByActor(actorId);
        }
        if (action != null) {
            return auditLogService.getByAction(action);
        }
        return auditLogService.getAll();
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> broadcast(@RequestBody Map<String, String> request) {
        String title = request.get("title");
        String message = request.get("message");
        String plan = request.get("plan"); // null or empty means ALL
        
        List<User> targets = authService.getAllUsers().stream()
                .filter(User::isActive)
                .filter(u -> plan == null || plan.isBlank() || "ALL".equalsIgnoreCase(plan) || plan.equalsIgnoreCase(u.getSubscriptionPlan()))
                .toList();
        
        int sentCount = adminNotificationClient.notifyUsersBulk(targets, Map.of(
                "type", "BROADCAST",
                "title", title,
                "message", message,
                "channel", "ALL",
                "relatedId", "broadcast",
                "relatedType", "broadcast",
                "actionUrl", "/dashboard"
        ));
        
        return Map.of(
                "status", sentCount == targets.size() ? "SUCCESS" : "PARTIAL_FAILURE",
                "sentToCount", sentCount,
                "failedCount", targets.size() - sentCount,
                "targetCount", targets.size(),
                "deliveryMessage", adminNotificationClient.getLastFailureMessage(),
                "planFilter", plan == null ? "ALL" : plan
        );
    }

    // ----------------------------------------------------------- oauth2 info

    /**
     * Returns the list of configured OAuth2 providers and their login initiation URLs.
     * The frontend can call this to render dynamic "Sign in with X" buttons.
     *
     * <p>Example response:
     * <pre>
     * [
     *   { "provider": "google",   "loginUrl": "/oauth2/authorization/google"   },
     *   { "provider": "github",   "loginUrl": "/oauth2/authorization/github"   },
     *   { "provider": "linkedin", "loginUrl": "/oauth2/authorization/linkedin" }
     * ]
     * </pre>
     */
    @GetMapping("/oauth2/providers")
    public List<Map<String, String>> oauthProviders() {
        return List.of(
            Map.of("provider", "google",   "loginUrl", oauthAuthorizationBaseUrl + "/oauth2/authorization/google"),
            Map.of("provider", "linkedin", "loginUrl", oauthAuthorizationBaseUrl + "/oauth2/authorization/linkedin")
        );
    }

    private Long currentUserId(Authentication authentication) {
        Object details = authentication == null ? null : authentication.getDetails();
        if (details instanceof Long userId) {
            return userId;
        }
        if (details instanceof Number number) {
            return number.longValue();
        }
        if (details instanceof String value) {
            return Long.valueOf(value);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication user id is missing");
    }

    private void recordAuditSafely(Long actorId, String actorEmail, String action,
                                   String entityType, String entityId) {
        recordAuditSafely(actorId, actorEmail, action, entityType, entityId, null, null);
    }

    private void recordAuditSafely(Long actorId, String actorEmail, String action,
                                   String entityType, String entityId,
                                   String beforeValue, String afterValue) {
        try {
            auditLogService.recordAudit(actorId, actorEmail, action, entityType, entityId, beforeValue, afterValue);
        } catch (RuntimeException ex) {
            log.warn("Audit log write failed for {} {} by {} but request continues: {}",
                    action, entityId, actorEmail, ex.getMessage());
        }
    }

    private MediaType mediaTypeFor(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
