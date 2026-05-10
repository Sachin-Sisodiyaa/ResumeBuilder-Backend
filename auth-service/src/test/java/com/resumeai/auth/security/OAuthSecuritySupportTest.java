package com.resumeai.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.SerializationUtils;

class OAuthSecuritySupportTest {

    @Test
    void cookieRepositorySavesLoadsAndRemovesAuthorizationRequest() {
        CookieOAuth2AuthorizationRequestRepository repository =
                new CookieOAuth2AuthorizationRequestRepository();
        OAuth2AuthorizationRequest request = authorizationRequest("linkedin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(request, new MockHttpServletRequest(), response);

        Cookie cookie = response.getCookie("resumeai_oauth2_request");
        assertNotNull(cookie);
        assertTrue(cookie.isHttpOnly());
        assertEquals("/", cookie.getPath());

        MockHttpServletRequest callback = new MockHttpServletRequest();
        callback.setCookies(cookie);
        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(callback);
        assertEquals("state-1", loaded.getState());

        MockHttpServletResponse removal = new MockHttpServletResponse();
        assertEquals("state-1", repository.removeAuthorizationRequest(callback, removal).getState());
        assertEquals(0, removal.getCookie("resumeai_oauth2_request").getMaxAge());
    }

    @Test
    void cookieRepositoryHandlesMissingAndNullRequests() {
        CookieOAuth2AuthorizationRequestRepository repository =
                new CookieOAuth2AuthorizationRequestRepository();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertNull(repository.loadAuthorizationRequest(new MockHttpServletRequest()));
        repository.saveAuthorizationRequest(null, new MockHttpServletRequest(), response);

        Cookie cookie = response.getCookie("resumeai_oauth2_request");
        assertNotNull(cookie);
        assertEquals(0, cookie.getMaxAge());
    }

    @Test
    void cookieRepositoryIgnoresUnrelatedAndNonAuthorizationCookies() {
        CookieOAuth2AuthorizationRequestRepository repository =
                new CookieOAuth2AuthorizationRequestRepository();
        MockHttpServletRequest unrelated = new MockHttpServletRequest();
        unrelated.setCookies(new Cookie("other", "value"));

        assertNull(repository.loadAuthorizationRequest(unrelated));

        byte[] serializedText = SerializationUtils.serialize("not-an-authorization-request");
        MockHttpServletRequest invalidPayload = new MockHttpServletRequest();
        invalidPayload.setCookies(new Cookie("resumeai_oauth2_request",
                Base64.getUrlEncoder().encodeToString(serializedText)));

        assertNull(repository.loadAuthorizationRequest(invalidPayload));
    }

    @Test
    void failureHandlerMapsKnownOAuthErrors() throws IOException {
        OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler();
        ReflectionTestUtils.setField(handler, "redirectUri", "http://localhost:3000/auth/oauth2/callback");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error("authorization_request_not_found"), "expired"));

        assertTrue(response.getRedirectedUrl().contains("error=oauth_session_expired"));
        assertTrue(response.getRedirectedUrl().contains("message=expired"));
        assertTrue(handler.errorRedirect("oauth_failed").contains("error=oauth_failed"));
    }

    @Test
    void linkedInResolverRemovesNonceOnlyForLinkedIn() {
        LinkedInAuthorizationRequestResolver resolver = new LinkedInAuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(
                        registration("linkedin"),
                        registration("google")));

        OAuth2AuthorizationRequest linkedIn = resolver.resolve(request("/oauth2/authorization/linkedin"));
        OAuth2AuthorizationRequest google = resolver.resolve(request("/oauth2/authorization/google"));

        assertNotNull(linkedIn);
        assertFalse(linkedIn.getAttributes().containsKey("nonce"));
        assertFalse(linkedIn.getAdditionalParameters().containsKey("nonce"));
        assertNotNull(google);
        assertTrue(google.getAdditionalParameters().containsKey("nonce"));
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setServletPath(uri);
        return request;
    }

    private OAuth2AuthorizationRequest authorizationRequest(String registrationId) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://example.com/oauth2/authorize")
                .clientId("client")
                .redirectUri("http://localhost/login/oauth2/code/" + registrationId)
                .state("state-1")
                .attributes(attrs -> attrs.put(OAuth2ParameterNames.REGISTRATION_ID, registrationId))
                .additionalParameters(params -> params.put("nonce", "nonce-1"))
                .build();
    }

    private ClientRegistration registration(String id) {
        return ClientRegistration.withRegistrationId(id)
                .clientId(id + "-client")
                .clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/" + id)
                .scope(Set.of("openid", "profile", "email"))
                .authorizationUri("https://example.com/oauth2/authorize")
                .tokenUri("https://example.com/oauth2/token")
                .jwkSetUri("https://example.com/oauth2/jwks")
                .userInfoUri("https://example.com/oauth2/userinfo")
                .userNameAttributeName("sub")
                .clientName(id)
                .build();
    }
}
