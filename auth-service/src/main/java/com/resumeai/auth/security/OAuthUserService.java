package com.resumeai.auth.security;

import com.resumeai.auth.model.User;
import com.resumeai.auth.repository.UserRepository;
import com.resumeai.auth.service.AsyncNotificationService;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Loads or provisions a local {@link User} record from the OAuth2 provider
 * attributes returned after a successful social login.
 *
 * <p>Supported providers:
 * <ul>
 *   <li><b>google</b>   — attributes: {@code email}, {@code name}, {@code picture}, {@code sub}</li>
 *   <li><b>github</b>   — attributes: {@code email}, {@code login}, {@code name}, {@code avatar_url}</li>
 *   <li><b>linkedin</b> — OIDC attributes: {@code email}, {@code name}, {@code picture}, {@code sub}</li>
 * </ul>
 *
 * <p>On first login the user is auto-registered with a FREE subscription plan.
 * Subsequent logins reuse the existing record (email is the unique key).
 *
 * <p>The provisioning logic lives in {@link #processOAuth2User(String, Map)}
 * which is package-private so it can be unit-tested without mocking HTTP calls.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OAuthUserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final AsyncNotificationService asyncNotificationService;

    // ─── Spring Security entry point ──────────────────────────────────────────

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // super.loadUser() exchanges the authorization code for user-info via HTTP
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        processOAuth2User(registrationId, oAuth2User.getAttributes());

        return oAuth2User;
    }

    // ─── Core provisioning — package-private for testability ─────────────────

    /**
     * Maps provider attributes to a local {@link User} and either creates or
     * updates it. Extracted so unit tests can call this directly without
     * triggering the HTTP UserInfo call in {@link #loadUser}.
     *
     * @param registrationId provider name, e.g. "google", "github", "linkedin"
     * @param attributes     raw attributes map returned by the provider
     */
    User processOAuth2User(String registrationId, Map<String, Object> attributes) {
        String email    = normalizeEmail(extractEmail(attributes));
        String fullName = extractName(registrationId, attributes);

        if (email == null || email.isBlank()) {
            log.warn("OAuth2 user from provider '{}' has no email — login rejected", registrationId);
            throw new OAuth2AuthenticationException(
                    "Email not provided by OAuth2 provider: " + registrationId);
        }

        String pictureUrl = extractPicture(registrationId, attributes);
        String oauthId    = extractSub(attributes);
        String provider   = registrationId.toUpperCase();

        return userRepository.findByEmail(email)
                .map(existing -> {
                    // Keep provider up-to-date; enrich picture/sub if not already set
                    existing.setProvider(provider);
                    if (pictureUrl != null && !pictureUrl.isBlank()) existing.setPictureUrl(pictureUrl);
                    if (oauthId    != null && !oauthId.isBlank())    existing.setOauthId(oauthId);
                    User saved = userRepository.save(existing);
                    log.info("OAuth2 login: existing user {} linked to provider {}", email, registrationId);
                    return saved;
                })
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setFullName(fullName != null && !fullName.isBlank() ? fullName : email);
                    newUser.setPasswordHash("");          // no password for OAuth users
                    newUser.setRole("USER");
                    newUser.setProvider(provider);
                    newUser.setOauthId(oauthId);
                    newUser.setPictureUrl(pictureUrl);
                    newUser.setSubscriptionPlan("FREE");
                    newUser.setActive(true);
                    newUser.setCreatedAt(Instant.now());
                    User saved = userRepository.save(newUser);
                    log.info("OAuth2 login: new user provisioned for {} via {}", email, registrationId);
                    // Fire-and-forget: runs on background thread so OAuth redirect is never delayed
                    asyncNotificationService.sendWelcomeNotificationsAsync(saved);
                    return saved;
                });
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    // ─── Attribute extraction ─────────────────────────────────────────────────

    private String extractEmail(Map<String, Object> attrs) {
        // All three providers expose "email" at the top level
        Object email = attrs.get("email");
        return email == null ? "" : String.valueOf(email).trim();
    }

    private String extractName(String provider, Map<String, Object> attrs) {
        return switch (provider.toLowerCase()) {
            case "google" -> String.valueOf(attrs.getOrDefault("name", ""));
            case "github" -> {
                String name = String.valueOf(attrs.getOrDefault("name", ""));
                yield name.isBlank() ? String.valueOf(attrs.getOrDefault("login", "")) : name;
            }
            case "linkedin" -> {
                String name = String.valueOf(attrs.getOrDefault("name", ""));
                if (!name.isBlank()) {
                    yield name;
                }
                String first = String.valueOf(attrs.getOrDefault("given_name",
                        attrs.getOrDefault("localizedFirstName", "")));
                String last  = String.valueOf(attrs.getOrDefault("family_name",
                        attrs.getOrDefault("localizedLastName", "")));
                yield (first + " " + last).trim();
            }
            default -> String.valueOf(attrs.getOrDefault("name", ""));
        };
    }

    private String extractSub(Map<String, Object> attrs) {
        // "sub" for Google/OIDC; "id" for GitHub and legacy LinkedIn payloads
        if (attrs.containsKey("sub")) return String.valueOf(attrs.get("sub"));
        if (attrs.containsKey("id"))  return String.valueOf(attrs.get("id"));
        return null;
    }

    private String extractPicture(String provider, Map<String, Object> attrs) {
        return switch (provider.toLowerCase()) {
            case "google" -> String.valueOf(attrs.getOrDefault("picture",    ""));
            case "github" -> String.valueOf(attrs.getOrDefault("avatar_url", ""));
            case "linkedin" -> String.valueOf(attrs.getOrDefault("picture",  ""));
            default       -> "";
        };
    }
}
