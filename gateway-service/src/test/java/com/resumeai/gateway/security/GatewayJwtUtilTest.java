package com.resumeai.gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GatewayJwtUtilTest {

    private static final String SECRET = "test-secret-key-for-gateway-tests-must-be-long-enough";

    private GatewayJwtUtil jwtUtil;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new GatewayJwtUtil();
        Field secret = GatewayJwtUtil.class.getDeclaredField("secret");
        secret.setAccessible(true);
        secret.set(jwtUtil, SECRET);
    }

    @Test
    void parsesValidJwtClaims() {
        String token = tokenFor("42");

        assertTrue(jwtUtil.isValid(token));
        assertEquals("42", jwtUtil.parse(token).getSubject());
        assertEquals("ADMIN", jwtUtil.parse(token).get("role"));
    }

    @Test
    void rejectsInvalidJwt() {
        assertFalse(jwtUtil.isValid("not-a-token"));
        assertFalse(jwtUtil.isValid(null));
    }

    private static String tokenFor(String subject) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claim("role", "ADMIN")
                .claim("plan", "PREMIUM")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key)
                .compact();
    }
}
