package com.enterprise.documentsearch.service;

import com.enterprise.documentsearch.model.User;
import com.enterprise.documentsearch.model.Role;
import com.enterprise.documentsearch.repository.UserRepository;
import com.enterprise.documentsearch.exception.ResourceNotFoundException;
import com.enterprise.documentsearch.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private String tenantId;

    @BeforeEach
    void setUp() {
        tenantId = "tenant_123";
        
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setTenantId(tenantId);
        testUser.setActive(true);
        testUser.setEmailVerified(true);
        testUser.setRoles(Set.of(Role.USER));
        testUser.setCreatedDate(LocalDateTime.now());
        testUser.setLastModifiedDate(LocalDateTime.now());
    }

    @Nested
    @DisplayName("User Registration Tests")
    class UserRegistrationTests {

        @Test
        @DisplayName("Should register user successfully")
        void shouldRegisterUserSuccessfully() {
            // Given
            User newUser = new User();
            newUser.setUsername("newuser");
            newUser.setEmail("new@example.com");
            newUser.setPassword("plainPassword");
            newUser.setFirstName("New");
            newUser.setLastName("User");
            newUser.setTenantId(tenantId);

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            User result = userService.registerUser(newUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testUser.getId());
            verify(userRepository).existsByEmail("new@example.com");
            verify(userRepository).existsByUsername("newuser");
            verify(passwordEncoder).encode("plainPassword");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception when email already exists")
        void shouldThrowExceptionWhenEmailExists() {
            // Given
            User newUser = new User();
            newUser.setEmail("existing@example.com");
            
            when(userRepository.existsByEmail(anyString())).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(newUser))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Email already exists");
            
            verify(userRepository).existsByEmail("existing@example.com");
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception when username already exists")
        void shouldThrowExceptionWhenUsernameExists() {
            // Given
            User newUser = new User();
            newUser.setEmail("new@example.com");
            newUser.setUsername("existinguser");
            
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByUsername(anyString())).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.registerUser(newUser))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Username already exists");
            
            verify(userRepository).existsByEmail("new@example.com");
            verify(userRepository).existsByUsername("existinguser");
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("User Retrieval Tests")
    class UserRetrievalTests {

        @Test
        @DisplayName("Should find user by ID successfully")
        void shouldFindUserByIdSuccessfully() {
            // Given
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            // When
            Optional<User> result = userService.getUserById(1L);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
            verify(userRepository).findById(1L);
        }

        @Test
        @DisplayName("Should return empty when user not found by ID")
        void shouldReturnEmptyWhenUserNotFoundById() {
            // Given
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            // When
            Optional<User> result = userService.getUserById(99L);

            // Then
            assertThat(result).isEmpty();
            verify(userRepository).findById(99L);
        }

        @Test
        @DisplayName("Should find user by username successfully")
        void shouldFindUserByUsernameSuccessfully() {
            // Given
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

            // When
            Optional<User> result = userService.getUserByUsername("testuser");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("testuser");
            verify(userRepository).findByUsername("testuser");
        }

        @Test
        @DisplayName("Should find user by email successfully")
        void shouldFindUserByEmailSuccessfully() {
            // Given
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            // When
            Optional<User> result = userService.getUserByEmail("test@example.com");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("test@example.com");
            verify(userRepository).findByEmail("test@example.com");
        }

        @Test
        @DisplayName("Should find user by email and tenant successfully")
        void shouldFindUserByEmailAndTenantSuccessfully() {
            // Given
            when(userRepository.findByEmailAndTenant("test@example.com", tenantId))
                    .thenReturn(Optional.of(testUser));

            // When
            User result = userService.findByEmailAndTenant("test@example.com", tenantId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("test@example.com");
            assertThat(result.getTenantId()).isEqualTo(tenantId);
            verify(userRepository).findByEmailAndTenant("test@example.com", tenantId);
        }

        @Test
        @DisplayName("Should return null when user not found by email and tenant")
        void shouldReturnNullWhenUserNotFoundByEmailAndTenant() {
            // Given
            when(userRepository.findByEmailAndTenant("notfound@example.com", tenantId))
                    .thenReturn(Optional.empty());

            // When
            User result = userService.findByEmailAndTenant("notfound@example.com", tenantId);

            // Then
            assertThat(result).isNull();
            verify(userRepository).findByEmailAndTenant("notfound@example.com", tenantId);
        }
    }

    @Nested
    @DisplayName("User Management Tests")
    class UserManagementTests {

        @Test
        @DisplayName("Should create user successfully")
        void shouldCreateUserSuccessfully() {
            // Given
            User newUser = new User();
            newUser.setUsername("newuser");
            newUser.setEmail("new@example.com");
            newUser.setTenantId(tenantId);
            
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            User result = userService.createUser(newUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(testUser.getId());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should update user successfully")
        void shouldUpdateUserSuccessfully() {
            // Given
            testUser.setFirstName("Updated");
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            User result = userService.updateUser(1L, testUser);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirstName()).isEqualTo("Updated");
            verify(userRepository).findById(1L);
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception when updating non-existent user")
        void shouldThrowExceptionWhenUpdatingNonExistentUser() {
            // Given
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.updateUser(99L, testUser))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");
            
            verify(userRepository).findById(99L);
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should delete user successfully")
        void shouldDeleteUserSuccessfully() {
            // Given
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            doNothing().when(userRepository).delete(any(User.class));

            // When
            userService.deleteUser(1L);

            // Then
            verify(userRepository).findById(1L);
            verify(userRepository).delete(testUser);
        }
    }

    @Nested
    @DisplayName("Tenant-based Tests")
    class TenantBasedTests {

        @Test
        @DisplayName("Should get users by tenant ID with pagination")
        void shouldGetUsersByTenantIdWithPagination() {
            // Given
            List<User> users = Arrays.asList(testUser);
            Page<User> userPage = new PageImpl<>(users);
            Pageable pageable = PageRequest.of(0, 10);
            
            when(userRepository.findByTenantId(tenantId, pageable)).thenReturn(userPage);

            // When
            Page<User> result = userService.getUsersByTenantId(tenantId, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTenantId()).isEqualTo(tenantId);
            verify(userRepository).findByTenantId(tenantId, pageable);
        }

        @Test
        @DisplayName("Should get user count by tenant ID")
        void shouldGetUserCountByTenantId() {
            // Given
            when(userRepository.countByTenantId(tenantId)).thenReturn(5L);

            // When
            long result = userService.getUserCountByTenantId(tenantId);

            // Then
            assertThat(result).isEqualTo(5L);
            verify(userRepository).countByTenantId(tenantId);
        }

        @Test
        @DisplayName("Should get active user count by tenant ID")
        void shouldGetActiveUserCountByTenantId() {
            // Given
            when(userRepository.countByTenantIdAndActiveTrue(tenantId)).thenReturn(3L);

            // When
            long result = userService.getActiveUserCountByTenantId(tenantId);

            // Then
            assertThat(result).isEqualTo(3L);
            verify(userRepository).countByTenantIdAndActiveTrue(tenantId);
        }
    }

    @Nested
    @DisplayName("UserDetailsService Tests")
    class UserDetailsServiceTests {

        @Test
        @DisplayName("Should load user by username successfully")
        void shouldLoadUserByUsernameSuccessfully() {
            // Given
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

            // When
            UserDetails result = userService.loadUserByUsername("testuser");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("testuser");
            assertThat(result.getPassword()).isEqualTo("encodedPassword");
            assertThat(result.isEnabled()).isTrue();
            verify(userRepository).findByUsername("testuser");
        }

        @Test
        @DisplayName("Should throw exception when user not found by username")
        void shouldThrowExceptionWhenUserNotFoundByUsername() {
            // Given
            when(userRepository.findByUsername("notfound")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.loadUserByUsername("notfound"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("User not found: notfound");
            
            verify(userRepository).findByUsername("notfound");
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should check if email exists")
        void shouldCheckIfEmailExists() {
            // Given
            when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

            // When
            boolean result = userService.existsByEmail("test@example.com");

            // Then
            assertThat(result).isTrue();
            verify(userRepository).existsByEmail("test@example.com");
        }

        @Test
        @DisplayName("Should check if username exists")
        void shouldCheckIfUsernameExists() {
            // Given
            when(userRepository.existsByUsername("testuser")).thenReturn(true);

            // When
            boolean result = userService.existsByUsername("testuser");

            // Then
            assertThat(result).isTrue();
            verify(userRepository).existsByUsername("testuser");
        }
    }
}