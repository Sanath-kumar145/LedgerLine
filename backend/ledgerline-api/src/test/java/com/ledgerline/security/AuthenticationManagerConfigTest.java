package com.ledgerline.security;

import com.ledgerline.config.JacksonConfig;
import com.ledgerline.config.SecurityConfig;
import com.ledgerline.security.jwt.JwtAuthenticationFilter;
import com.ledgerline.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {SecurityConfig.class, JacksonConfig.class})
@DisplayName("AuthenticationManager Architecture & Provider Verification Test")
class AuthenticationManagerConfigTest {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("Verify AuthenticationManager is ProviderManager with DaoAuthenticationProvider and BCrypt(12)")
    void testAuthenticationManagerWiring() {
        assertNotNull(authenticationManager, "AuthenticationManager bean must be configured");
        assertInstanceOf(ProviderManager.class, authenticationManager, "AuthenticationManager must be an instance of ProviderManager");

        ProviderManager providerManager = (ProviderManager) authenticationManager;
        boolean hasDaoProvider = providerManager.getProviders().stream()
                .anyMatch(p -> p instanceof DaoAuthenticationProvider);
        assertTrue(hasDaoProvider, "ProviderManager must contain DaoAuthenticationProvider");

        assertNotNull(passwordEncoder, "PasswordEncoder bean must be configured");
        assertInstanceOf(BCryptPasswordEncoder.class, passwordEncoder, "PasswordEncoder must be BCrypt");
    }

    @Test
    @DisplayName("Verify AuthenticationManager authenticates valid user via CustomUserDetailsService and PasswordEncoder")
    void testAuthenticationManagerSuccess() {
        String username = "testuser";
        String rawPassword = "SecurePassword123!";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        UserDetails userDetails = new User(
                username,
                encodedPassword,
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);

        Authentication authResult = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, rawPassword)
        );

        assertNotNull(authResult);
        assertTrue(authResult.isAuthenticated());
        assertEquals(username, authResult.getName());
    }

    @Test
    @DisplayName("Verify AuthenticationManager rejects bad credentials via DaoAuthenticationProvider")
    void testAuthenticationManagerRejectsBadPassword() {
        String username = "testuser";
        String rawPassword = "CorrectPassword123!";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        UserDetails userDetails = new User(
                username,
                encodedPassword,
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);

        assertThrows(BadCredentialsException.class, () ->
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(username, "WrongPassword!")
                )
        );
    }

    @Test
    @DisplayName("Verify AuthenticationManager handles non-existent user cleanly")
    void testAuthenticationManagerNonExistentUser() {
        when(userDetailsService.loadUserByUsername("ghost")).thenThrow(new UsernameNotFoundException("User not found"));

        assertThrows(BadCredentialsException.class, () ->
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken("ghost", "AnyPassword123!")
                )
        );
    }
}
