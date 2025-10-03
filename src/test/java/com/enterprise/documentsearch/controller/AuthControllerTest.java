package com.enterprise.documentsearch.controller;

import com.enterprise.documentsearch.model.User;
import com.enterprise.documentsearch.model.Role;
import com.enterprise.documentsearch.service.UserService;
import com.enterprise.documentsearch.security.JwtTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@WebMvcTest(AuthController.class)
@DisplayName("AuthController Unit Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenUtil jwtTokenUtil;

    @MockBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private Map<String, Object> loginRequest;
    private Map<String, Object> registerRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setTenantId("tenant_123");
        testUser.setActive(true);
        testUser.setEmailVerified(true);
        testUser.setRoles(Set.of(Role.USER));
        testUser.setCreatedDate(LocalDateTime.now());
        testUser.setLastModifiedDate(LocalDateTime.now());

        loginRequest = new HashMap<>();
        loginRequest.put("email", "test@example.com");
        loginRequest.put("password", "password123");
        loginRequest.put("tenantId", "tenant_123");

        registerRequest = new HashMap<>();
        registerRequest.put("username", "newuser");
        registerRequest.put("email", "newuser@example.com");
        registerRequest.put("password", "password123");
        registerRequest.put("firstName", "New");
        registerRequest.put("lastName", "User");
        registerRequest.put("tenantId", "tenant_123");
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void shouldLoginSuccessfully() throws Exception {
            // Given
            Authentication mockAuth = mock(Authentication.class);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(mockAuth);
            when(userService.findByEmailAndTenant("test@example.com", "tenant_123"))
                    .thenReturn(testUser);
            when(jwtTokenUtil.generateToken(any(UserDetails.class)))
                    .thenReturn("mock.jwt.token");

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("mock.jwt.token"))
                    .andExpect(jsonPath("$.user.username").value("testuser"))
                    .andExpect(jsonPath("$.user.email").value("test@example.com"))
                    .andExpect(jsonPath("$.user.tenantId").value("tenant_123"));

            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(userService).findByEmailAndTenant("test@example.com", "tenant_123");
            verify(jwtTokenUtil).generateToken(any(UserDetails.class));
        }

        @Test
        @DisplayName("Should fail login with invalid credentials")
        void shouldFailLoginWithInvalidCredentials() throws Exception {
            // Given
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Invalid credentials"));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid credentials"));

            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verifyNoInteractions(userService, jwtTokenUtil);
        }

        @Test
        @DisplayName("Should fail login with missing required fields")
        void shouldFailLoginWithMissingFields() throws Exception {
            // Given
            loginRequest.remove("email");

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authenticationManager, userService, jwtTokenUtil);
        }

        @Test
        @DisplayName("Should fail login with invalid email format")
        void shouldFailLoginWithInvalidEmail() throws Exception {
            // Given
            loginRequest.put("email", "invalid-email");

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authenticationManager, userService, jwtTokenUtil);
        }
    }

    @Nested
    @DisplayName("Register Tests")
    class RegisterTests {

        @Test
        @DisplayName("Should register user successfully")
        void shouldRegisterUserSuccessfully() throws Exception {
            // Given
            User newUser = new User();
            newUser.setId(2L);
            newUser.setUsername("newuser");
            newUser.setEmail("newuser@example.com");
            newUser.setFirstName("New");
            newUser.setLastName("User");
            newUser.setTenantId("tenant_123");
            newUser.setActive(true);
            newUser.setRoles(Set.of(Role.USER));

            when(userService.registerUser(any(User.class))).thenReturn(newUser);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("User registered successfully"))
                    .andExpect(jsonPath("$.user.username").value("newuser"))
                    .andExpect(jsonPath("$.user.email").value("newuser@example.com"));

            verify(userService).registerUser(any(User.class));
        }

        @Test
        @DisplayName("Should fail registration with existing email")
        void shouldFailRegistrationWithExistingEmail() throws Exception {
            // Given
            when(userService.registerUser(any(User.class)))
                    .thenThrow(new RuntimeException("Email already exists"));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Email already exists"));

            verify(userService).registerUser(any(User.class));
        }

        @Test
        @DisplayName("Should fail registration with missing required fields")
        void shouldFailRegistrationWithMissingFields() throws Exception {
            // Given
            registerRequest.remove("email");

            // When & Then
            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("Should fail registration with weak password")
        void shouldFailRegistrationWithWeakPassword() throws Exception {
            // Given
            registerRequest.put("password", "123");

            // When & Then
            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(userService);
        }
    }

    @Nested
    @DisplayName("Token Refresh Tests")
    class TokenRefreshTests {

        @Test
        @DisplayName("Should refresh token successfully")
        void shouldRefreshTokenSuccessfully() throws Exception {
            // Given
            Map<String, String> refreshRequest = new HashMap<>();
            refreshRequest.put("token", "old.jwt.token");

            when(jwtTokenUtil.extractUsername("old.jwt.token")).thenReturn("testuser");
            when(jwtTokenUtil.canTokenBeRefreshed("old.jwt.token")).thenReturn(true);
            when(userService.findByUsernameAndTenant("testuser", anyString())).thenReturn(testUser);
            when(jwtTokenUtil.refreshToken("old.jwt.token")).thenReturn("new.jwt.token");

            // When & Then
            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("new.jwt.token"));

            verify(jwtTokenUtil).extractUsername("old.jwt.token");
            verify(jwtTokenUtil).canTokenBeRefreshed("old.jwt.token");
            verify(jwtTokenUtil).refreshToken("old.jwt.token");
        }

        @Test
        @DisplayName("Should fail to refresh expired token")
        void shouldFailToRefreshExpiredToken() throws Exception {
            // Given
            Map<String, String> refreshRequest = new HashMap<>();
            refreshRequest.put("token", "expired.jwt.token");

            when(jwtTokenUtil.extractUsername("expired.jwt.token")).thenReturn("testuser");
            when(jwtTokenUtil.canTokenBeRefreshed("expired.jwt.token")).thenReturn(false);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Token cannot be refreshed"));

            verify(jwtTokenUtil).extractUsername("expired.jwt.token");
            verify(jwtTokenUtil).canTokenBeRefreshed("expired.jwt.token");
            verifyNoMoreInteractions(jwtTokenUtil);
        }
    }

    @Nested
    @DisplayName("Profile Tests")
    class ProfileTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should get user profile successfully")
        void shouldGetUserProfileSuccessfully() throws Exception {
            // Given
            when(userService.findByUsername("testuser")).thenReturn(testUser);

            // When & Then
            mockMvc.perform(get("/api/v1/auth/profile")
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andExpect(jsonPath("$.email").value("test@example.com"))
                    .andExpect(jsonPath("$.tenantId").value("tenant_123"));

            verify(userService).findByUsername("testuser");
        }

        @Test
        @DisplayName("Should fail to get profile without authentication")
        void shouldFailToGetProfileWithoutAuth() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/auth/profile"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(userService);
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should update user profile successfully")
        void shouldUpdateUserProfileSuccessfully() throws Exception {
            // Given
            Map<String, String> updateRequest = new HashMap<>();
            updateRequest.put("firstName", "Updated");
            updateRequest.put("lastName", "Name");

            User updatedUser = new User();
            updatedUser.setId(1L);
            updatedUser.setUsername("testuser");
            updatedUser.setEmail("test@example.com");
            updatedUser.setFirstName("Updated");
            updatedUser.setLastName("Name");
            updatedUser.setTenantId("tenant_123");

            when(userService.findByUsername("testuser")).thenReturn(testUser);
            when(userService.updateUser(any(User.class))).thenReturn(updatedUser);

            // When & Then
            mockMvc.perform(put("/api/v1/auth/profile")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpected(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Updated"))
                    .andExpect(jsonPath("$.lastName").value("Name"));

            verify(userService).findByUsername("testuser");
            verify(userService).updateUser(any(User.class));
        }
    }

    @Nested
    @DisplayName("Logout Tests")  
    class LogoutTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should logout successfully")
        void shouldLogoutSuccessfully() throws Exception {
            // Given
            Map<String, String> logoutRequest = new HashMap<>();
            logoutRequest.put("token", "valid.jwt.token");

            when(jwtTokenUtil.extractUsername("valid.jwt.token")).thenReturn("testuser");
            when(userService.findByUsername("testuser")).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/logout")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(logoutRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logout successful"));

            verify(jwtTokenUtil).extractUsername("valid.jwt.token");
            verify(userService).findByUsername("testuser");
        }

        @Test
        @DisplayName("Should fail logout without authentication")
        void shouldFailLogoutWithoutAuth() throws Exception {
            // Given
            Map<String, String> logoutRequest = new HashMap<>();
            logoutRequest.put("token", "valid.jwt.token");

            // When & Then
            mockMvc.perform(post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(logoutRequest)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(jwtTokenUtil, userService);
        }
    }
}