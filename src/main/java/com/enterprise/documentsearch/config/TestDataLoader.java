package com.enterprise.documentsearch.config;

import com.enterprise.documentsearch.model.Role;
import com.enterprise.documentsearch.model.User;
import com.enterprise.documentsearch.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;





import java.util.Set;

/**
 * Data initialization for test profile - creates sample users for testing
 */
// Temporarily disabled to prevent application shutdown issues
//@Component
//@Profile("disabled")
@RequiredArgsConstructor
@Slf4j
public class TestDataLoader {

    private final UserService userService;

    //@EventListener(ApplicationReadyEvent.class)
    public void loadTestData() {
        log.info("Loading test data...");
        
        try {
            // Create a test user if it doesn't exist
            if (!userService.existsByEmail("test@example.com")) {
                User testUser = User.builder()
                        .username("testuser")
                        .email("test@example.com")
                        .password("password123") // Will be encoded by UserService
                        .firstName("Test")
                        .lastName("User")
                        .tenantId("tenant_123")
                        .roles(Set.of(Role.USER))
                        .active(true)
                        .emailVerified(true)
                        .build();
                
                userService.createUser(testUser);
                log.info("Created test user: testuser (test@example.com) in tenant: tenant_123");
            }
            
            // Create an admin user if it doesn't exist
            if (!userService.existsByEmail("admin@example.com")) {
                User adminUser = User.builder()
                        .username("admin")
                        .email("admin@example.com")
                        .password("admin123") // Will be encoded by UserService
                        .firstName("Admin")
                        .lastName("User")
                        .tenantId("tenant_123")
                        .roles(Set.of(Role.ADMIN, Role.USER))
                        .active(true)
                        .emailVerified(true)
                        .build();
                
                userService.createUser(adminUser);
                log.info("Created admin user: admin (admin@example.com) in tenant: tenant_123");
            }
            
            log.info("Test data loaded successfully!");
            log.info("You can now login with:");
            log.info("  Regular user: email=test@example.com, password=password123, tenantId=tenant_123");
            log.info("  Admin user: email=admin@example.com, password=admin123, tenantId=tenant_123");
            
        } catch (Exception e) {
            log.error("Failed to load test data", e);
        }
    }
}