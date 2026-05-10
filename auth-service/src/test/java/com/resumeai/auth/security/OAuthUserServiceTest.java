package com.resumeai.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resumeai.auth.model.User;
import com.resumeai.auth.repository.UserRepository;
import com.resumeai.auth.service.AsyncNotificationService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link OAuthUserService}.
 *
 * <p>Tests call the package-private {@link OAuthUserService#processOAuth2User}
 * directly, which contains all provisioning logic but does NOT make HTTP calls.
 * This avoids the need to mock the remote UserInfo endpoint.
 */
@ExtendWith(MockitoExtension.class)
class OAuthUserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AsyncNotificationService asyncNotificationService;

    private OAuthUserService oAuthUserService;

    @BeforeEach
    void setUp() {
        oAuthUserService = new OAuthUserService(userRepository, asyncNotificationService);
    }

    // ── Google — new user ─────────────────────────────────────────────────────

    @Test
    void googleNewUserIsProvisionedAsFree() {
        when(userRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        oAuthUserService.processOAuth2User("google", googleAttrs(
                "alice@gmail.com", "Alice Smith",
                "https://pic.example.com/alice.jpg", "google-sub-123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertEquals("alice@gmail.com", saved.getEmail());
        assertEquals("Alice Smith", saved.getFullName());
        assertEquals("GOOGLE", saved.getProvider());
        assertEquals("FREE", saved.getSubscriptionPlan());
        assertEquals("https://pic.example.com/alice.jpg", saved.getPictureUrl());
        assertEquals("google-sub-123", saved.getOauthId());
        assertNotNull(saved.getCreatedAt());
    }

    // ── GitHub — new user (name blank → falls back to login) ─────────────────

    @Test
    void githubNewUserUsesLoginWhenNameIsBlank() {
        when(userRepository.findByEmail("dev@github.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email",      "dev@github.com");
        attrs.put("name",       "");              // blank — should fall back to login
        attrs.put("login",      "dev-handle");
        attrs.put("avatar_url", "https://avatars.github.com/dev");
        attrs.put("id",         42);

        oAuthUserService.processOAuth2User("github", attrs);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertEquals("dev-handle", saved.getFullName());
        assertEquals("GITHUB", saved.getProvider());
        assertEquals("https://avatars.github.com/dev", saved.getPictureUrl());
        assertEquals("42", saved.getOauthId());
    }

    // ── LinkedIn — name composed from first + last ────────────────────────────

    @Test
    void linkedinNewUserFullNameComposed() {
        when(userRepository.findByEmail("li@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email",               "li@example.com");
        attrs.put("localizedFirstName",  "John");
        attrs.put("localizedLastName",   "Doe");
        attrs.put("id",                  "li-123");

        oAuthUserService.processOAuth2User("linkedin", attrs);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("John Doe", captor.getValue().getFullName());
        assertEquals("LINKEDIN", captor.getValue().getProvider());
    }

    // ── Existing user — provider and picture updated ──────────────────────────

    @Test
    void linkedinOidcUserUsesNamePictureAndSub() {
        when(userRepository.findByEmail("oidc@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", "oidc@example.com");
        attrs.put("name", "Jane Recruiter");
        attrs.put("picture", "https://media.licdn.com/jane.jpg");
        attrs.put("sub", "linkedin-sub-456");

        oAuthUserService.processOAuth2User("linkedin", attrs);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertEquals("Jane Recruiter", saved.getFullName());
        assertEquals("LINKEDIN", saved.getProvider());
        assertEquals("https://media.licdn.com/jane.jpg", saved.getPictureUrl());
        assertEquals("linkedin-sub-456", saved.getOauthId());
    }

    @Test
    void existingUserProviderAndPictureAreUpdated() {
        User existing = new User();
        existing.setEmail("bob@gmail.com");
        existing.setProvider("LOCAL");
        existing.setPictureUrl(null);

        when(userRepository.findByEmail("bob@gmail.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        oAuthUserService.processOAuth2User("google", googleAttrs(
                "bob@gmail.com", "Bob", "https://pic.example.com/bob.jpg", "sub-bob"));

        verify(userRepository).save(existing);
        assertEquals("GOOGLE", existing.getProvider());
        assertEquals("https://pic.example.com/bob.jpg", existing.getPictureUrl());
        assertEquals("sub-bob", existing.getOauthId());
    }

    // ── Missing email → exception ─────────────────────────────────────────────

    @Test
    void missingEmailThrowsOAuth2Exception() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("name", "No Email User");
        // email key absent

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,
                () -> oAuthUserService.processOAuth2User("google", attrs)
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Test
    void unknownProviderFallsBackToEmailWhenNamePictureAndIdAreMissing() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", " unknown@example.com ");

        oAuthUserService.processOAuth2User("custom", attrs);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertEquals("unknown@example.com", saved.getEmail());
        assertEquals("unknown@example.com", saved.getFullName());
        assertEquals("CUSTOM", saved.getProvider());
        assertEquals("", saved.getPictureUrl());
        assertEquals(null, saved.getOauthId());
    }

    private Map<String, Object> googleAttrs(String email, String name,
                                             String picture, String sub) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email",   email);
        attrs.put("name",    name);
        attrs.put("picture", picture);
        attrs.put("sub",     sub);
        return attrs;
    }
}
