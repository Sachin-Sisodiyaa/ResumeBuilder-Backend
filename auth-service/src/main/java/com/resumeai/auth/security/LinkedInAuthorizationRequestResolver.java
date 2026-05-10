package com.resumeai.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

@Component
public class LinkedInAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String NONCE = "nonce";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public LinkedInAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return customize(delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null || !isLinkedIn(authorizationRequest)) {
            return authorizationRequest;
        }

        Map<String, Object> attributes = new LinkedHashMap<>(authorizationRequest.getAttributes());
        attributes.remove(NONCE);

        Map<String, Object> additionalParameters =
                new LinkedHashMap<>(authorizationRequest.getAdditionalParameters());
        additionalParameters.remove(NONCE);

        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .attributes(attrs -> {
                    attrs.clear();
                    attrs.putAll(attributes);
                })
                .additionalParameters(params -> {
                    params.clear();
                    params.putAll(additionalParameters);
                })
                .build();
    }

    private boolean isLinkedIn(OAuth2AuthorizationRequest authorizationRequest) {
        Object registrationId = authorizationRequest.getAttribute(OAuth2ParameterNames.REGISTRATION_ID);
        return "linkedin".equalsIgnoreCase(String.valueOf(registrationId));
    }
}
