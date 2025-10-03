package com.enterprise.documentsearch.model;

/**
 * User roles enum defining different access levels in the system
 */
public enum Role {
    /**
     * Regular user with basic document access
     */
    USER("User"),
    
    /**
     * Manager with additional document management capabilities
     */
    MANAGER("Manager"),
    
    /**
     * Administrator with tenant-level administrative access
     */
    ADMIN("Administrator"),
    
    /**
     * Super administrator with system-wide access
     */
    SUPER_ADMIN("Super Administrator");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if this role has higher or equal privileges than the given role
     */
    public boolean hasHigherOrEqualPrivileges(Role other) {
        return this.ordinal() >= other.ordinal();
    }

    /**
     * Check if this role can manage users with the given role
     */
    public boolean canManage(Role other) {
        return this.ordinal() > other.ordinal();
    }
}