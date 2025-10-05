package com.enterprise.documentsearch.service;

import com.enterprise.documentsearch.model.Role;
import com.enterprise.documentsearch.model.User;
import com.enterprise.documentsearch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * Service class for User entity operations and authentication
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);
        
        return userRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /**
     * Create a new user
     */
    public User createUser(User user) {
        log.info("Creating new user: {}", user.getUsername());
        
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists: " + user.getUsername());
        }
        
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists: " + user.getEmail());
        }
        
        // Encode password
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        // Set default role if none provided
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            user.setRoles(Set.of(Role.USER));
        }
        
        // Set user as active by default
        user.setActive(true);
        
        return userRepository.save(user);
    }

    /**
     * Update user information
     */
    public User updateUser(Long userId, User userUpdates) {
        log.info("Updating user with id: {}", userId);
        
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        
        // Update allowed fields
        if (userUpdates.getFirstName() != null) {
            existingUser.setFirstName(userUpdates.getFirstName());
        }
        if (userUpdates.getLastName() != null) {
            existingUser.setLastName(userUpdates.getLastName());
        }
        if (userUpdates.getEmail() != null && !userUpdates.getEmail().equals(existingUser.getEmail())) {
            if (userRepository.existsByEmail(userUpdates.getEmail())) {
                throw new RuntimeException("Email already exists: " + userUpdates.getEmail());
            }
            existingUser.setEmail(userUpdates.getEmail());
            existingUser.setEmailVerified(false); // Reset email verification
        }
        
        return userRepository.save(existingUser);
    }

    /**
     * Update user password
     */
    public void updatePassword(Long userId, String newPassword) {
        log.info("Updating password for user id: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Update user roles (admin only)
     */
    public User updateUserRoles(Long userId, Set<Role> roles) {
        log.info("Updating roles for user id: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        
        user.setRoles(roles);
        return userRepository.save(user);
    }

    /**
     * Activate/deactivate user
     */
    public User updateUserStatus(Long userId, boolean active) {
        log.info("Updating status for user id: {} to {}", userId, active);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        
        user.setActive(active);
        return userRepository.save(user);
    }

    /**
     * Update last login date
     */
    public void updateLastLoginDate(String username) {
        userRepository.findByUsernameOrEmail(username)
                .ifPresent(user -> {
                    user.setLastLoginDate(LocalDateTime.now());
                    userRepository.save(user);
                });
    }

    /**
     * Get user by ID
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    /**
     * Get user by username
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Get user by email
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    /**
     * Find user by email/username and tenant ID
     */
    @Transactional(readOnly = true)
    public User findByEmailAndTenant(String emailOrUsername, String tenantId) {
        return userRepository.findByEmailAndTenant(emailOrUsername, tenantId).orElse(null);
    }

    /**
     * Get users by tenant ID with pagination
     */
    @Transactional(readOnly = true)
    public Page<User> getUsersByTenantId(String tenantId, Pageable pageable) {
        return userRepository.findByTenantId(tenantId, pageable);
    }

    /**
     * Get active users by tenant ID with pagination
     */
    @Transactional(readOnly = true)
    public Page<User> getActiveUsersByTenantId(String tenantId, Pageable pageable) {
        return userRepository.findByTenantIdAndActive(tenantId, true, pageable);
    }

    /**
     * Search users by tenant ID and search term
     */
    @Transactional(readOnly = true)
    public Page<User> searchUsers(String tenantId, String searchTerm, Pageable pageable) {
        return userRepository.findByTenantIdAndSearchTerm(tenantId, searchTerm, pageable);
    }

    /**
     * Delete user (soft delete by deactivating)
     */
    public void deleteUser(Long userId) {
        log.info("Deleting user with id: {}", userId);
        updateUserStatus(userId, false);
    }

    /**
     * Check if username exists
     */
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Check if email exists
     */
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Get user count by tenant ID
     */
    @Transactional(readOnly = true)
    public long getUserCountByTenantId(String tenantId) {
        return userRepository.countByTenantId(tenantId);
    }

    /**
     * Get active user count by tenant ID
     */
    @Transactional(readOnly = true)
    public long getActiveUserCountByTenantId(String tenantId) {
        return userRepository.countByTenantIdAndActive(tenantId, true);
    }
}