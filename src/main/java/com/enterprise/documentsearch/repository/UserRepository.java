package com.enterprise.documentsearch.repository;

import com.enterprise.documentsearch.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for User entity operations
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by username or email
     */
    @Query("SELECT u FROM User u WHERE u.username = :usernameOrEmail OR u.email = :usernameOrEmail")
    Optional<User> findByUsernameOrEmail(@Param("usernameOrEmail") String usernameOrEmail);

    /**
     * Check if username exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Find users by tenant ID
     */
    Page<User> findByTenantId(String tenantId, Pageable pageable);

    /**
     * Find active users by tenant ID
     */
    Page<User> findByTenantIdAndActive(String tenantId, boolean active, Pageable pageable);

    /**
     * Count users by tenant ID
     */
    long countByTenantId(String tenantId);

    /**
     * Count active users by tenant ID
     */
    long countByTenantIdAndActive(String tenantId, boolean active);

    /**
     * Find users by tenant ID and username containing (case insensitive)
     */
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND " +
           "(LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findByTenantIdAndSearchTerm(@Param("tenantId") String tenantId,
                                          @Param("search") String search,
                                          Pageable pageable);
}