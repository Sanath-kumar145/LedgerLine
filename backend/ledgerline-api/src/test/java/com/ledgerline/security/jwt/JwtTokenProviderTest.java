package com.ledgerline.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JWT Token Provider Tests")
class JwtTokenProviderTest {

    private static final String TEST_SECRET = "TestSecretKeyForLedgerLineMustBeAtLeast256BitsLongForHMACSHA256SecureSigning!";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("Fail initialization when secret is null, empty, or blank")
    void testMissingSecretRejection() {
        IllegalArgumentException exNull = assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenProvider(null, EXPIRATION_MS));
        assertTrue(exNull.getMessage().contains("JWT signing secret must be configured"));

        IllegalArgumentException exEmpty = assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenProvider("", EXPIRATION_MS));
        assertTrue(exEmpty.getMessage().contains("JWT signing secret must be configured"));

        IllegalArgumentException exBlank = assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenProvider("    ", EXPIRATION_MS));
        assertTrue(exBlank.getMessage().contains("JWT signing secret must be configured"));
    }

    @Test
    @DisplayName("Fail initialization when secret is shorter than 32 bytes (256 bits)")
    void testShortSecretRejection() {
        String shortSecret28Bytes = "short-secret-under-32-bytes!";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenProvider(shortSecret28Bytes, EXPIRATION_MS));
        assertTrue(ex.getMessage().contains("at least 32 bytes (256 bits) of entropy"));
    }

    @Test
    @DisplayName("Accept valid 32-byte and 64-byte secrets")
    void testValidSecretAcceptance() {
        // Exactly 32 bytes
        String secret32Bytes = "12345678901234567890123456789012";
        assertDoesNotThrow(() -> {
            JwtTokenProvider provider = new JwtTokenProvider(secret32Bytes, EXPIRATION_MS);
            String token = provider.generateToken(1L, "valid_user", List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));
            assertTrue(provider.validateToken(token));
        });

        // 64-byte test secret
        assertDoesNotThrow(() -> new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS));
    }

    @Test
    @DisplayName("Generate token and verify claims extraction")
    void testTokenGenerationAndExtraction() {
        Long userId = 42L;
        String username = "alice_test";
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")
        );

        String token = tokenProvider.generateToken(userId, username, authorities);

        assertNotNull(token);
        assertTrue(tokenProvider.validateToken(token));
        assertEquals(username, tokenProvider.getUsernameFromToken(token));
        assertEquals(userId, tokenProvider.getUserIdFromToken(token));

        List<String> roles = tokenProvider.getRolesFromToken(token);
        assertNotNull(roles);
        assertEquals(1, roles.size());
        assertTrue(roles.contains("ROLE_ACCOUNT_HOLDER"));
    }

    @Test
    @DisplayName("Reject expired token")
    void testExpiredTokenRejection() {
        // Provider with negative expiration to simulate an already expired token
        JwtTokenProvider expiredProvider = new JwtTokenProvider(TEST_SECRET, -10000);
        String expiredToken = expiredProvider.generateToken(1L, "bob_expired", List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));

        assertFalse(tokenProvider.validateToken(expiredToken), "Expired token must be rejected");
    }

    @Test
    @DisplayName("Reject tampered token with altered signature")
    void testTamperedTokenRejection() {
        String token = tokenProvider.generateToken(1L, "charlie_tamper", List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));
        String tamperedToken = token.substring(0, token.length() - 5) + "ABCDE";

        assertFalse(tokenProvider.validateToken(tamperedToken), "Token with invalid signature must be rejected");
    }

    @Test
    @DisplayName("Reject malformed or empty token")
    void testMalformedTokenRejection() {
        assertFalse(tokenProvider.validateToken("not-a-jwt-token"));
        assertFalse(tokenProvider.validateToken(""));
        assertFalse(tokenProvider.validateToken(null));
    }
}

