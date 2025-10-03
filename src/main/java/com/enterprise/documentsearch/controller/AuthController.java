package com.enterprise.documentsearch.controller;

import com.enterprise.documentsearch.model.Role;
import com.enterprise.documentsearch.model.User;
import com.enterprise.documentsearch.security.JwtTokenUtil;
import com.enterprise.documentsearch.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Authentication controller for user registration, login, and token management
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User authentication and registration endpoints")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtTokenUtil jwtTokenUtil;

    /**
     * User login endpoint
     */
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and return JWT token")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Login attempt for user: {}", loginRequest.getUsernameOrEmail());

        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsernameOrEmail(),
                    loginRequest.getPassword()
                )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            User user = (User) authentication.getPrincipal();

            // Update last login date
            userService.updateLastLoginDate(user.getUsername());

            // Generate tokens
            String jwt = jwtTokenUtil.generateToken(authentication);
            String refreshToken = jwtTokenUtil.generateRefreshToken(user.getUsername());

            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("refreshToken", refreshToken);
            response.put("type", "Bearer");
            response.put("expiresIn", jwtTokenUtil.getExpirationTime());
            response.put("user", createUserResponse(user));

            log.info("User {} logged in successfully", user.getUsername());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Login failed for user: {}", loginRequest.getUsernameOrEmail(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid credentials");
            error.put("message", "Username/email or password is incorrect");
            return ResponseEntity.status(401).body(error);
        }
    }

    /**
     * User registration endpoint
     */
    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register a new user account")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        log.info("Registration attempt for user: {}", registerRequest.getUsername());

        try {
            // Check if username or email already exists
            if (userService.existsByUsername(registerRequest.getUsername())) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Username already exists");
                error.put("message", "Please choose a different username");
                return ResponseEntity.badRequest().body(error);
            }

            if (userService.existsByEmail(registerRequest.getEmail())) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Email already exists");
                error.put("message", "An account with this email already exists");
                return ResponseEntity.badRequest().body(error);
            }

            // Create new user
            User user = User.builder()
                .username(registerRequest.getUsername())
                .email(registerRequest.getEmail())
                .password(registerRequest.getPassword())
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .tenantId(registerRequest.getTenantId())
                .roles(Set.of(Role.USER))
                .active(true)
                .emailVerified(false)
                .build();

            User savedUser = userService.createUser(user);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "User registered successfully");
            response.put("user", createUserResponse(savedUser));

            log.info("User {} registered successfully", savedUser.getUsername());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Registration failed for user: {}", registerRequest.getUsername(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Registration failed");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Refresh token endpoint
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Generate new access token using refresh token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        String refreshToken = refreshTokenRequest.getRefreshToken();

        try {
            if (jwtTokenUtil.validateToken(refreshToken)) {
                String username = jwtTokenUtil.getUsernameFromToken(refreshToken);
                // Verify user still exists and is active
                userService.getUserByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

                String newToken = jwtTokenUtil.generateTokenFromUsername(username);

                Map<String, Object> response = new HashMap<>();
                response.put("token", newToken);
                response.put("type", "Bearer");
                response.put("expiresIn", jwtTokenUtil.getExpirationTime());

                log.info("Token refreshed for user: {}", username);
                return ResponseEntity.ok(response);
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid refresh token");
                error.put("message", "Refresh token is expired or invalid");
                return ResponseEntity.status(401).body(error);
            }
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Token refresh failed");
            error.put("message", e.getMessage());
            return ResponseEntity.status(401).body(error);
        }
    }

    /**
     * Logout endpoint (client-side token removal)
     */
    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Logout user (client should remove token)")
    public ResponseEntity<?> logout() {
        // With JWT, logout is typically handled client-side by removing the token
        // Server-side logout would require token blacklisting which we'll implement later
        SecurityContextHolder.clearContext();
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Get current user profile
     */
    @GetMapping("/profile")
    @Operation(summary = "Get user profile", description = "Get current authenticated user profile")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Not authenticated");
            return ResponseEntity.status(401).body(error);
        }

        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(createUserResponse(user));
    }

    /**
     * Create user response object (without sensitive information)
     */
    private Map<String, Object> createUserResponse(User user) {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("id", user.getId());
        userResponse.put("username", user.getUsername());
        userResponse.put("email", user.getEmail());
        userResponse.put("firstName", user.getFirstName());
        userResponse.put("lastName", user.getLastName());
        userResponse.put("fullName", user.getFullName());
        userResponse.put("tenantId", user.getTenantId());
        userResponse.put("roles", user.getRoles());
        userResponse.put("active", user.getActive());
        userResponse.put("emailVerified", user.getEmailVerified());
        userResponse.put("createdDate", user.getCreatedDate());
        userResponse.put("lastLoginDate", user.getLastLoginDate());
        return userResponse;
    }

    // Request DTOs
    public static class LoginRequest {
        @jakarta.validation.constraints.NotBlank(message = "Username or email is required")
        private String usernameOrEmail;
        
        @jakarta.validation.constraints.NotBlank(message = "Password is required")
        private String password;

        // Getters and setters
        public String getUsernameOrEmail() { return usernameOrEmail; }
        public void setUsernameOrEmail(String usernameOrEmail) { this.usernameOrEmail = usernameOrEmail; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class RegisterRequest {
        @jakarta.validation.constraints.NotBlank(message = "Username is required")
        @jakarta.validation.constraints.Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        private String username;
        
        @jakarta.validation.constraints.NotBlank(message = "Email is required")
        @jakarta.validation.constraints.Email(message = "Email must be valid")
        private String email;
        
        @jakarta.validation.constraints.NotBlank(message = "Password is required")
        @jakarta.validation.constraints.Size(min = 8, message = "Password must be at least 8 characters")
        private String password;
        
        @jakarta.validation.constraints.NotBlank(message = "First name is required")
        private String firstName;
        
        @jakarta.validation.constraints.NotBlank(message = "Last name is required")
        private String lastName;
        
        @jakarta.validation.constraints.NotBlank(message = "Tenant ID is required")
        private String tenantId;

        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    }

    public static class RefreshTokenRequest {
        @jakarta.validation.constraints.NotBlank(message = "Refresh token is required")
        private String refreshToken;

        // Getters and setters
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }
}