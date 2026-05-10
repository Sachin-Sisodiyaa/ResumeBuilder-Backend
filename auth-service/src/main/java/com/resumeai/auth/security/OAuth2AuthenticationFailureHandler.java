package com.resumeai.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/auth/oauth2/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        log.warn("OAuth2 login failed: {}", exception.getMessage());
        response.sendRedirect(errorRedirect(resolveErrorCode(exception), exception.getMessage()));
    }

    String errorRedirect(String error) {
        return errorRedirect(error, null);
    }

    String errorRedirect(String error, String message) {
        String separator = redirectUri.contains("?") ? "&" : "?";
        String target = redirectUri + separator + "error=" + URLEncoder.encode(error, StandardCharsets.UTF_8);
        if (message == null || message.isBlank()) {
            return target;
        }
        return target + "&message=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
    }

    private String resolveErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException
                && oauthException.getError() != null
                && oauthException.getError().getErrorCode() != null) {
            String code = oauthException.getError().getErrorCode();
            if (code.contains("invalid_token_response")) {
                return "oauth_token_exchange_failed";
            }
            if (code.contains("authorization_request_not_found")) {
                return "oauth_session_expired";
            }
            if (code.contains("access_denied")) {
                return "oauth_access_denied";
            }
            return code;
        }
        return "oauth_failed";
    }
}
