package com.resumeai.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequestEntityConverter;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.MultiValueMap;

/**
 * Stateless JWT-based Spring Security configuration with OAuth2 social login.
 *
 * <p>Public endpoints (register, login, password-reset, OAuth2 initiator/callback)
 * are accessible without a token. All other endpoints require a valid Bearer JWT.
 * ADMIN-only endpoints require the ROLE_ADMIN authority.
 *
 * <p>OAuth2 providers (Google, GitHub, LinkedIn) are configured via
 * {@code spring.security.oauth2.client.registration.*} in application.yml.
 * After successful authorization the browser is redirected to the frontend by
 * {@link OAuth2AuthenticationSuccessHandler} carrying a ResumeAI JWT.
 *
 * <p>Session management is STATELESS for the REST API; Spring OAuth2 client
 * needs a minimal session only during the authorization-code flow (before the
 * redirect) — that session is destroyed immediately after the token exchange.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuthUserService oAuthUserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2FailureHandler;
    private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final LinkedInAuthorizationRequestResolver authorizationRequestResolver;
    private final InternalServiceAuthenticationFilter internalServiceAuthenticationFilter;

    @Bean
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            /*
             * OAuth2 authorization-code flow requires a short-lived session to store
             * the state/nonce between the redirect to the provider and the callback.
             * We use IF_REQUIRED (default) so a session is only created when needed.
             * The JWT filter enforces stateless auth for all regular API calls.
             */
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/oauth2/providers").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/profile-picture/**").permitAll()
                // OAuth2 initiation and callback routes
                .requestMatchers(
                    "/oauth2/**",
                    "/login/oauth2/**",
                    "/error",
                    "/actuator/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/v1/auth/internal/subscription/**").hasRole("SERVICE")
                .requestMatchers("/api/v1/auth/users/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            // ── OAuth2 social login ───────────────────────────────────────────
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(ep -> ep
                    .baseUri("/oauth2/authorization")
                    .authorizationRequestResolver(authorizationRequestResolver)
                    .authorizationRequestRepository(authorizationRequestRepository)
                )
                .redirectionEndpoint(ep -> ep
                    .baseUri("/login/oauth2/code/*")
                )
                .userInfoEndpoint(ep -> ep
                    .userService(oAuthUserService)
                )
                .tokenEndpoint(ep -> ep
                    .accessTokenResponseClient(accessTokenResponseClient())
                )
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
            )
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication is required.\"}"
                );
            }))
            .addFilterBefore(internalServiceAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient() {
        DefaultAuthorizationCodeTokenResponseClient client = new DefaultAuthorizationCodeTokenResponseClient();
        client.setRequestEntityConverter(new OAuth2AuthorizationCodeGrantRequestEntityConverter() {
            @Override
            protected MultiValueMap<String, String> createParameters(
                    OAuth2AuthorizationCodeGrantRequest authorizationGrantRequest) {
                MultiValueMap<String, String> parameters = super.createParameters(authorizationGrantRequest);
                var registration = authorizationGrantRequest.getClientRegistration();
                if ("linkedin".equalsIgnoreCase(registration.getRegistrationId())) {
                    parameters.set(OAuth2ParameterNames.CLIENT_ID, registration.getClientId());
                    parameters.set(OAuth2ParameterNames.CLIENT_SECRET, registration.getClientSecret());
                }
                return parameters;
            }
        });
        return client;
    }
}
