package com.resumeai.auth.security;

import com.resumeai.auth.model.User;
import com.resumeai.auth.dto.AuthDtos.AuthResponse;
import com.resumeai.auth.service.AuthService;
import com.resumeai.auth.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Called by Spring Security after a successful OAuth2 authorization-code exchange.
 *
 * <p>Generates a ResumeAI JWT for the authenticated social user and redirects
 * the browser to the frontend with the token embedded as a query parameter:
 * <pre>
 *   GET {frontend-url}/oauth2/callback?token={jwt}&amp;refreshToken={refresh}
 * </pre>
 *
 * <p>The frontend should read those params, store them, and then redirect
 * the user to the dashboard.  For SPA / mobile apps, swap the redirect for
 * a JSON response if preferred.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final OAuth2AuthenticationFailureHandler failureHandler;
    private final OAuthUserService oAuthUserService;

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth2/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            completeOAuthLogin(response, authentication);
        } catch (Exception ex) {
            log.error("OAuth2 success handling failed: {}", ex.getMessage(), ex);
            response.sendRedirect(failureHandler.errorRedirect("oauth_failed", "OAuth login could not be completed."));
        }
    }

    private void completeOAuthLogin(HttpServletResponse response, Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String registrationId = authentication instanceof OAuth2AuthenticationToken token
                ? token.getAuthorizedClientRegistrationId()
                : "google";

        User user;
        try {
            user = oAuthUserService.processOAuth2User(registrationId, oAuth2User.getAttributes());
        } catch (RuntimeException ex) {
            log.error("OAuth2 success handler: could not provision local user: {}", ex.getMessage());
            response.sendRedirect(redirectUri + "?error=user_not_found");
            return;
        }

        AuthResponse authResponse;
        try {
            authResponse = authService.createSession(user);
        } catch (ResponseStatusException ex) {
            log.warn("OAuth2 login rejected for {}: {}", user.getEmail(), ex.getReason());
            response.sendRedirect(failureHandler.errorRedirect("account_inactive"));
            return;
        }

        auditLogService.recordAudit(user.getUserId(), user.getEmail(),
                "OAUTH2_LOGIN", "User", String.valueOf(user.getUserId()),
                null, user.getProvider());

        log.info("OAuth2 login success for {} via {}", user.getEmail(), user.getProvider());

        String target = redirectUri
                + "?token=" + encode(authResponse.accessToken())
                + "&refreshToken=" + encode(authResponse.refreshToken())
                + "&provider=" + encode(user.getProvider());

        response.sendRedirect(target);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
