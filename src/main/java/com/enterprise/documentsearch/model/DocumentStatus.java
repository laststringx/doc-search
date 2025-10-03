package com.enterprise.documentsearch.model;

/**
 * Document status enumeration
 */
public enum DocumentStatus {
    /**
     * Document is active and available
     */
    ACTIVE("Active"),
    
    /**
     * Document is in draft state
     */
    DRAFT("Draft"),
    
    /**
     * Document is archived
     */
    ARCHIVED("Archived"),
    
    /**
     * Document is deleted (soft delete)
     */
    DELETED("Deleted"),
    
    /**
     * Document is being processed
     */
    PROCESSING("Processing"),
    
    /**
     * Document processing failed
     */
    FAILED("Failed");

    private final String displayName;

    DocumentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}