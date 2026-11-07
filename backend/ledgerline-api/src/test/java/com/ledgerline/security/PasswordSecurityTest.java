package com.ledgerline.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Password Security Tests")
class PasswordSecurityTest {

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Work factor 12 as defined in Security Architecture
        passwordEncoder = new BCryptPasswordEncoder(12);
    }

    @Test
    @DisplayName("Stored password must not equal plaintext password")
    void testStoredPasswordIsNotPlaintext() {
        String rawPassword = "StrongPassword123!@#";
        String encodedHash = passwordEncoder.encode(rawPassword);

        assertNotNull(encodedHash);
        assertNotEquals(rawPassword, encodedHash, "Password hash must not equal plaintext password");
        assertTrue(encodedHash.startsWith("$2a$12$") || encodedHash.startsWith("$2b$12$"),
                "Password must be encoded with BCrypt strength 12 ($2a$12$ or $2b$12$)");
    }

    @Test
    @DisplayName("Password encoder verifies matching password and rejects incorrect password")
    void testPasswordVerification() {
        String correctPassword = "CorrectPassword2026$";
        String wrongPassword = "WrongPassword2026$";
        String encodedHash = passwordEncoder.encode(correctPassword);

        assertTrue(passwordEncoder.matches(correctPassword, encodedHash),
                "Password encoder must match the original raw password against its hash");
        assertFalse(passwordEncoder.matches(wrongPassword, encodedHash),
                "Password encoder must reject an incorrect password");
    }

    @Test
    @DisplayName("Unique salts: identical plaintext passwords produce different hashes")
    void testSaltUniqueness() {
        String password = "SamePasswordEveryTime1!";
        String hash1 = passwordEncoder.encode(password);
        String hash2 = passwordEncoder.encode(password);

        assertNotEquals(hash1, hash2, "BCrypt must generate a unique cryptographically random salt for each hash");
        assertTrue(passwordEncoder.matches(password, hash1));
        assertTrue(passwordEncoder.matches(password, hash2));
    }
}

