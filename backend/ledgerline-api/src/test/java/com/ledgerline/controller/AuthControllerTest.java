package com.ledgerline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.dto.AuthResponse;
import com.ledgerline.dto.LoginRequest;
import com.ledgerline.dto.RegisterRequest;
import com.ledgerline.dto.UserProfileResponse;
import com.ledgerline.dto.UserSummaryDto;
import com.ledgerline.entity.UserStatus;
import com.ledgerline.exception.DuplicateResourceException;
import com.ledgerline.exception.GlobalExceptionHandler;
import com.ledgerline.security.CustomUserDetailsService;
import com.ledgerline.security.jwt.JwtAuthenticationFilter;
import com.ledgerline.security.jwt.JwtTokenProvider;
import com.ledgerline.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.ledgerline.config.JacksonConfig;
import com.ledgerline.config.SecurityConfig;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({JacksonConfig.class, SecurityConfig.class})
@DisplayName("Authentication Controller Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /api/v1/auth/register succeeds with 201 Created and does not return password")
    void testRegisterSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice_new",
                "alice@example.com",
                "ValidPassword123!",
                "Alice Smith"
        );

        UserProfileResponse response = new UserProfileResponse(
                10L,
                "alice_new",
                "alice@example.com",
                "Alice Smith",
                UserStatus.ACTIVE,
                List.of("ROLE_ACCOUNT_HOLDER"),
                Instant.now()
        );

        when(userService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.username").value("alice_new"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.fullName").value("Alice Smith"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ACCOUNT_HOLDER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register rejects weak password with 400 Bad Request")
    void testRegisterWeakPasswordRejected() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice_weak",
                "alice@example.com",
                "weak", // violates min length and character diversity
                "Alice Smith"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register rejects duplicate username or email with 409 Conflict")
    void testRegisterDuplicateConflict() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "duplicate_user",
                "duplicate@example.com",
                "ValidPassword123!",
                "Duplicate User"
        );

        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateResourceException("Username 'duplicate_user' is already taken."));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Username 'duplicate_user' is already taken."));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login succeeds with 200 OK and returns JWT Bearer token")
    void testLoginSuccess() throws Exception {
        LoginRequest request = new LoginRequest("alice_valid", "ValidPassword123!");
        UserSummaryDto userSummary = new UserSummaryDto(
                10L,
                "alice_valid",
                "alice@example.com",
                "Alice Smith",
                List.of("ROLE_ACCOUNT_HOLDER")
        );
        AuthResponse authResponse = new AuthResponse("mock.jwt.token", "Bearer", 86400000, userSummary);

        when(userService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("alice_valid"))
                .andExpect(jsonPath("$.user.roles[0]").value("ROLE_ACCOUNT_HOLDER"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login rejects invalid credentials with 401 Unauthorized")
    void testLoginBadCredentials() throws Exception {
        LoginRequest request = new LoginRequest("alice_valid", "WrongPassword123!");

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout succeeds with 200 OK")
    void testLogout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }
}

