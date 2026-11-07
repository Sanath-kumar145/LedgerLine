package com.ledgerline.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.controller.AdminController;
import com.ledgerline.controller.UserController;
import com.ledgerline.dto.UserSummaryDto;
import com.ledgerline.security.jwt.JwtTokenProvider;
import com.ledgerline.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.ledgerline.config.JacksonConfig;
import com.ledgerline.config.SecurityConfig;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminController.class, UserController.class})
@AutoConfigureMockMvc
@Import({JacksonConfig.class, SecurityConfig.class})
@DisplayName("Role-Based Access Control (RBAC) Tests")
class RoleAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    @DisplayName("ROLE_ADMIN can access authorized /api/v1/admin/users endpoint")
    void testAdminCanAccessAdminEndpoint() throws Exception {
        UserSummaryDto user1 = new UserSummaryDto(1L, "alice", "alice@example.com", "Alice Smith", List.of("ROLE_ACCOUNT_HOLDER"));
        when(userService.getAllUsers()).thenReturn(List.of(user1));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    @WithMockUser(username = "regular_holder", roles = {"ACCOUNT_HOLDER"})
    @DisplayName("ROLE_ACCOUNT_HOLDER is denied access to /api/v1/admin/users (403 Forbidden)")
    void testAccountHolderCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "compliance_auditor", roles = {"AUDITOR"})
    @DisplayName("ROLE_AUDITOR is denied access to /api/v1/admin/users (403 Forbidden)")
    void testAuditorCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated / Anonymous user is denied access to /api/v1/admin/users (401 Unauthorized)")
    void testAnonymousCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated / Anonymous user is denied access to /api/v1/users/me (401 Unauthorized)")
    void testAnonymousCannotAccessProtectedUserEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }
}

