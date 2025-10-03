package com.enterprise.documentsearch.security;

import com.enterprise.documentsearch.model.User;
import com.enterprise.documentsearch.model.Role;
import com.enterprise.documentsearch.repository.UserRepository;
import com.enterprise.documentsearch.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Security Integration Tests")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private User adminUser;
    private String tenantId = "tenant_123";

    @BeforeEach
    void setUp() {
        // Clean up database
        userRepository.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setTenantId(tenantId);
        testUser.setActive(true);
        testUser.setEmailVerified(true);
        testUser.setRoles(Set.of(Role.USER));
        testUser.setCreatedDate(LocalDateTime.now());
        testUser.setLastModifiedDate(LocalDateTime.now());
        testUser = userRepository.save(testUser);

        // Create admin user
        adminUser = new User();
        adminUser.setUsername("adminuser");
        adminUser.setEmail("admin@example.com");
        adminUser.setPassword(passwordEncoder.encode("adminpass123"));
        adminUser.setFirstName("Admin");
        adminUser.setLastName("User");
        adminUser.setTenantId(tenantId);
        adminUser.setActive(true);
        adminUser.setEmailVerified(true);
        adminUser.setRoles(Set.of(Role.ADMIN, Role.USER));
        adminUser.setCreatedDate(LocalDateTime.now());
        adminUser.setLastModifiedDate(LocalDateTime.now());
        adminUser = userRepository.save(adminUser);
    }

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @Test
        @Transactional
        @DisplayName("Should authenticate user with valid credentials")
        void shouldAuthenticateUserWithValidCredentials() throws Exception {
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("email", "test@example.com");
            loginRequest.put("password", "password123");
            loginRequest.put("tenantId", tenantId);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.user.username").value("testuser"))
                    .andExpect(jsonPath("$.user.email").value("test@example.com"))
                    .andExpect(jsonPath("$.user.tenantId").value(tenantId));
        }

        @Test
        @DisplayName("Should reject authentication with invalid password")
        void shouldRejectAuthenticationWithInvalidPassword() throws Exception {
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("email", "test@example.com");
            loginRequest.put("password", "wrongpassword");
            loginRequest.put("tenantId", tenantId);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject authentication with non-existent user")
        void shouldRejectAuthenticationWithNonExistentUser() throws Exception {
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("email", "nonexistent@example.com");
            loginRequest.put("password", "password123");
            loginRequest.put("tenantId", tenantId);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject authentication with wrong tenant")
        void shouldRejectAuthenticationWithWrongTenant() throws Exception {
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("email", "test@example.com");
            loginRequest.put("password", "password123");
            loginRequest.put("tenantId", "wrong_tenant");

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject authentication for inactive user")
        void shouldRejectAuthenticationForInactiveUser() throws Exception {
            // Deactivate user
            testUser.setActive(false);
            userRepository.save(testUser);

            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("email", "test@example.com");
            loginRequest.put("password", "password123");
            loginRequest.put("tenantId", tenantId);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("JWT Token Tests")
    class JwtTokenTests {

        @Test
        @DisplayName("Should validate JWT token correctly")
        void shouldValidateJwtTokenCorrectly() {
            // Generate token
            String token = jwtTokenUtil.generateToken(testUser);

            // Validate token
            assertThat(jwtTokenUtil.validateToken(token, testUser)).isTrue();
            assertThat(jwtTokenUtil.extractUsername(token)).isEqualTo("testuser");
            assertThat(jwtTokenUtil.extractTenantId(token)).isEqualTo(tenantId);
        }

        @Test
        @DisplayName("Should reject expired JWT token")
        void shouldRejectExpiredJwtToken() {
            // This would require creating a token with past expiration
            // In a real scenario, you might mock the clock or use a test configuration
            String expiredToken = jwtTokenUtil.generateExpiredToken(testUser);
            
            assertThat(jwtTokenUtil.isTokenExpired(expiredToken)).isTrue();
            assertThat(jwtTokenUtil.validateToken(expiredToken, testUser)).isFalse();
        }

        @Test
        @DisplayName("Should reject tampered JWT token")
        void shouldRejectTamperedJwtToken() {
            String validToken = jwtTokenUtil.generateToken(testUser);
            String tamperedToken = validToken.substring(0, validToken.length() - 5) + "XXXXX";
            
            assertThat(jwtTokenUtil.validateToken(tamperedToken, testUser)).isFalse();
        }

        @Test
        @DisplayName("Should refresh JWT token successfully")
        void shouldRefreshJwtTokenSuccessfully() throws Exception {
            String originalToken = jwtTokenUtil.generateToken(testUser);
            
            Map<String, String> refreshRequest = new HashMap<>();
            refreshRequest.put("token", originalToken);

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }
    }

    @Nested  
    @DisplayName("Authorization Tests")
    class AuthorizationTests {

        @Test
        @DisplayName("Should allow access to protected endpoints with valid token")
        void shouldAllowAccessToProtectedEndpointsWithValidToken() throws Exception {
            String token = jwtTokenUtil.generateToken(testUser);

            mockMvc.perform(get("/api/v1/auth/profile")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("testuser"));
        }

        @Test
        @DisplayName("Should deny access to protected endpoints without token")
        void shouldDenyAccessToProtectedEndpointsWithoutToken() throws Exception {
            mockMvc.perform(get("/api/v1/auth/profile"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should deny access to protected endpoints with invalid token")
        void shouldDenyAccessToProtectedEndpointsWithInvalidToken() throws Exception {
            mockMvc.perform(get("/api/v1/auth/profile")
                    .header("Authorization", "Bearer invalid.token.here"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should deny access to protected endpoints with expired token")
        void shouldDenyAccessToProtectedEndpointsWithExpiredToken() throws Exception {
            String expiredToken = jwtTokenUtil.generateExpiredToken(testUser);

            mockMvc.perform(get("/api/v1/auth/profile")
                    .header("Authorization", "Bearer " + expiredToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should enforce role-based access control")
        void shouldEnforceRoleBasedAccessControl() throws Exception {
            String userToken = jwtTokenUtil.generateToken(testUser);
            String adminToken = jwtTokenUtil.generateToken(adminUser);

            // Admin endpoint should be accessible by admin
            mockMvc.perform(get("/api/v1/admin/users")
                    .header("Authorization", "Bearer " + adminToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isOk());

            // Admin endpoint should not be accessible by regular user
            mockMvc.perform(get("/api/v1/admin/users")
                    .header("Authorization", "Bearer " + userToken)
                    .header("X-Tenant-ID", tenantId))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("CORS Tests")
    class CorsTests {

        @Test
        @DisplayName("Should handle CORS preflight requests")
        void shouldHandleCorsPreflightRequests() throws Exception {
            mockMvc.perform(options("/api/v1/auth/login")
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "Content-Type,Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                    .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS"))
                    .andExpect(header().string("Access-Control-Allow-Headers", "*"));
        }

        @Test
        @DisplayName("Should include CORS headers in responses")
        void shouldIncludeCorsHeadersInResponses() throws Exception {
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("email", "test@example.com");
            loginRequest.put("password", "password123");
            loginRequest.put("tenantId", tenantId);

            mockMvc.perform(post("/api/v1/auth/login")
                    .header("Origin", "http://localhost:3000")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "*"));
        }
    }

    @Nested
    @DisplayName("Tenant Isolation Security Tests")
    class TenantIsolationSecurityTests {

        @Test
        @Transactional
        @DisplayName("Should prevent cross-tenant data access")
        void shouldPreventCrossTenantDataAccess() throws Exception {
            // Create user in different tenant
            User otherTenantUser = new User();
            otherTenantUser.setUsername("otheruser");
            otherTenantUser.setEmail("other@example.com");
            otherTenantUser.setPassword(passwordEncoder.encode("password123"));
            otherTenantUser.setFirstName("Other");
            otherTenantUser.setLastName("User");
            otherTenantUser.setTenantId("tenant_456");
            otherTenantUser.setActive(true);
            otherTenantUser.setEmailVerified(true);
            otherTenantUser.setRoles(Set.of(Role.USER));
            otherTenantUser.setCreatedDate(LocalDateTime.now());
            otherTenantUser.setLastModifiedDate(LocalDateTime.now());
            otherTenantUser = userRepository.save(otherTenantUser);

            String otherTenantToken = jwtTokenUtil.generateToken(otherTenantUser);

            // Try to access documents with wrong tenant ID
            mockMvc.perform(get("/api/v1/documents")
                    .header("Authorization", "Bearer " + otherTenantToken)
                    .header("X-Tenant-ID", tenantId)) // Wrong tenant ID
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should validate tenant ID in JWT token")
        void shouldValidateTenantIdInJwtToken() throws Exception {
            String token = jwtTokenUtil.generateToken(testUser);

            // Try to access with different tenant ID than in token
            mockMvc.perform(get("/api/v1/documents")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Tenant-ID", "different_tenant"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Password Security Tests")
    class PasswordSecurityTests {

        @Test
        @Transactional
        @DisplayName("Should enforce password strength requirements")
        void shouldEnforcePasswordStrengthRequirements() throws Exception {
            Map<String, Object> registerRequest = new HashMap<>();
            registerRequest.put("username", "newuser");
            registerRequest.put("email", "new@example.com");
            registerRequest.put("password", "123"); // Weak password
            registerRequest.put("firstName", "New");
            registerRequest.put("lastName", "User");
            registerRequest.put("tenantId", tenantId);

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Password must be at least 8 characters long"));
        }

        @Test
        @DisplayName("Should hash passwords properly")
        void shouldHashPasswordsProperly() {
            String rawPassword = "password123";
            String hashedPassword = passwordEncoder.encode(rawPassword);

            assertThat(hashedPassword).isNotEqualTo(rawPassword);
            assertThat(passwordEncoder.matches(rawPassword, hashedPassword)).isTrue();
            assertThat(passwordEncoder.matches("wrongpassword", hashedPassword)).isFalse();
        }
    }

    @Nested
    @DisplayName("Session Management Tests")
    class SessionManagementTests {

        @Test
        @Transactional
        @DisplayName("Should handle logout properly")
        void shouldHandleLogoutProperly() throws Exception {
            String token = jwtTokenUtil.generateToken(testUser);

            Map<String, String> logoutRequest = new HashMap<>();
            logoutRequest.put("token", token);

            mockMvc.perform(post("/api/v1/auth/logout")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(logoutRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logout successful"));
        }

        @Test
        @DisplayName("Should track user login activity")
        void shouldTrackUserLoginActivity() throws Exception {
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("email", "test@example.com");
            loginRequest.put("password", "password123");
            loginRequest.put("tenantId", tenantId);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk());

            // Verify last login date was updated
            User updatedUser = userRepository.findById(testUser.getId()).orElse(null);
            assertThat(updatedUser).isNotNull();
            assertThat(updatedUser.getLastLoginDate()).isNotNull();
        }
    }
}