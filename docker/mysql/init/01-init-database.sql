-- Production-ready database initialization script
-- Optimized for 10M+ documents with tenant isolation

-- Set global settings for performance
SET GLOBAL innodb_buffer_pool_size = 1073741824; -- 1GB
SET GLOBAL max_connections = 500;
SET GLOBAL query_cache_type = 0; -- Disable query cache for better concurrency

-- Create database with optimized settings
CREATE DATABASE IF NOT EXISTS document_search 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

USE document_search;

-- Create optimized users table with partitioning preparation
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_date TIMESTAMP NULL,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    -- Indexes for performance
    INDEX idx_users_tenant_id (tenant_id),
    INDEX idx_users_email (email),
    INDEX idx_users_username (username),
    INDEX idx_users_tenant_email (tenant_id, email),
    INDEX idx_users_active (active),
    INDEX idx_users_created_date (created_date)
) ENGINE=InnoDB 
  ROW_FORMAT=DYNAMIC
  PARTITION BY HASH(CRC32(tenant_id)) PARTITIONS 8;

-- Create user_roles table
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role ENUM('USER', 'MANAGER', 'ADMIN', 'SUPER_ADMIN') NOT NULL,
    
    PRIMARY KEY (user_id, role),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_roles_user_id (user_id)
) ENGINE=InnoDB;

-- Create documents table optimized for 10M+ documents
CREATE TABLE documents (
    id BIGINT AUTO_INCREMENT,
    tenant_id VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    content LONGTEXT,
    file_name VARCHAR(255),
    file_type VARCHAR(100),
    file_size BIGINT,
    author VARCHAR(255),
    tags VARCHAR(500),
    status ENUM('ACTIVE', 'DRAFT', 'ARCHIVED', 'DELETED', 'PROCESSING', 'FAILED') NOT NULL DEFAULT 'ACTIVE',
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    -- Composite primary key for better partitioning
    PRIMARY KEY (id, tenant_id),
    
    -- Indexes for high-performance queries
    INDEX idx_documents_tenant_id (tenant_id),
    INDEX idx_documents_tenant_created (tenant_id, created_date DESC),
    INDEX idx_documents_tenant_status (tenant_id, status),
    INDEX idx_documents_tenant_author (tenant_id, author),
    INDEX idx_documents_title (title(100)),
    INDEX idx_documents_file_type (file_type),
    INDEX idx_documents_created_date (created_date),
    
    -- Full-text search indexes
    FULLTEXT INDEX idx_documents_content_fulltext (content),
    FULLTEXT INDEX idx_documents_title_content_fulltext (title, content),
    FULLTEXT INDEX idx_documents_multi_fulltext (title, content, tags)
    
) ENGINE=InnoDB 
  ROW_FORMAT=DYNAMIC
  PARTITION BY HASH(CRC32(tenant_id)) PARTITIONS 16;

-- Create document_analytics table for metrics
CREATE TABLE document_analytics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    document_id BIGINT NOT NULL,
    event_type ENUM('VIEW', 'DOWNLOAD', 'SEARCH', 'SHARE') NOT NULL,
    user_id BIGINT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_analytics_tenant_date (tenant_id, created_date),
    INDEX idx_analytics_document (document_id),
    INDEX idx_analytics_user (user_id),
    INDEX idx_analytics_event_type (event_type)
) ENGINE=InnoDB
  PARTITION BY RANGE (UNIX_TIMESTAMP(created_date)) (
    PARTITION p2024_q4 VALUES LESS THAN (UNIX_TIMESTAMP('2025-01-01')),
    PARTITION p2025_q1 VALUES LESS THAN (UNIX_TIMESTAMP('2025-04-01')),
    PARTITION p2025_q2 VALUES LESS THAN (UNIX_TIMESTAMP('2025-07-01')),
    PARTITION p2025_q3 VALUES LESS THAN (UNIX_TIMESTAMP('2025-10-01')),
    PARTITION p2025_q4 VALUES LESS THAN (UNIX_TIMESTAMP('2026-01-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
  );

-- Create search_queries table for analytics and optimization
CREATE TABLE search_queries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    query_text VARCHAR(1000) NOT NULL,
    result_count INT NOT NULL DEFAULT 0,
    response_time_ms INT NOT NULL DEFAULT 0,
    user_id BIGINT,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_search_tenant_date (tenant_id, created_date),
    INDEX idx_search_query_text (query_text(100)),
    INDEX idx_search_response_time (response_time_ms),
    INDEX idx_search_user (user_id)
) ENGINE=InnoDB;

-- Create tenant_settings table for tenant-specific configurations
CREATE TABLE tenant_settings (
    tenant_id VARCHAR(100) PRIMARY KEY,
    max_documents BIGINT NOT NULL DEFAULT 1000000,
    max_file_size_mb INT NOT NULL DEFAULT 50,
    allowed_file_types JSON,
    rate_limit_per_minute INT NOT NULL DEFAULT 1000,
    search_timeout_ms INT NOT NULL DEFAULT 500,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_tenant_settings_created (created_date)
) ENGINE=InnoDB;

-- Insert default tenant settings
INSERT INTO tenant_settings (tenant_id, allowed_file_types) VALUES 
('tenant_123', JSON_ARRAY('pdf', 'doc', 'docx', 'txt', 'md', 'html', 'xml', 'json'));

-- Create stored procedures for common operations
DELIMITER //

-- Procedure to get tenant document statistics
CREATE PROCEDURE GetTenantDocumentStats(IN p_tenant_id VARCHAR(100))
BEGIN
    SELECT 
        COUNT(*) as total_documents,
        COUNT(CASE WHEN status = 'ACTIVE' THEN 1 END) as active_documents,
        COUNT(CASE WHEN status = 'DRAFT' THEN 1 END) as draft_documents,
        SUM(CASE WHEN file_size IS NOT NULL THEN file_size ELSE 0 END) as total_size_bytes,
        AVG(file_size) as avg_file_size,
        COUNT(DISTINCT author) as unique_authors,
        MIN(created_date) as oldest_document,
        MAX(created_date) as newest_document
    FROM documents 
    WHERE tenant_id = p_tenant_id AND status != 'DELETED';
END //

-- Procedure for efficient document search with pagination
CREATE PROCEDURE SearchDocuments(
    IN p_tenant_id VARCHAR(100),
    IN p_search_query VARCHAR(1000),
    IN p_limit INT,
    IN p_offset INT
)
BEGIN
    SELECT 
        id,
        title,
        file_name,
        file_type,
        file_size,
        author,
        created_date,
        last_modified_date,
        MATCH(title, content, tags) AGAINST(p_search_query IN NATURAL LANGUAGE MODE) as relevance_score
    FROM documents 
    WHERE tenant_id = p_tenant_id 
    AND status = 'ACTIVE'
    AND MATCH(title, content, tags) AGAINST(p_search_query IN NATURAL LANGUAGE MODE)
    ORDER BY relevance_score DESC, created_date DESC
    LIMIT p_limit OFFSET p_offset;
END //

DELIMITER ;

-- Create views for common queries
CREATE VIEW active_documents_by_tenant AS
SELECT 
    tenant_id,
    COUNT(*) as document_count,
    SUM(file_size) as total_size,
    COUNT(DISTINCT author) as author_count,
    MAX(created_date) as last_document_date
FROM documents 
WHERE status = 'ACTIVE'
GROUP BY tenant_id;

-- Performance optimization: Analyze tables
ANALYZE TABLE users, user_roles, documents, document_analytics, search_queries, tenant_settings;

-- Create database user with appropriate permissions
CREATE USER IF NOT EXISTS 'docuser'@'%' IDENTIFIED BY 'DocSearch2025!';
GRANT SELECT, INSERT, UPDATE, DELETE ON document_search.* TO 'docuser'@'%';
GRANT EXECUTE ON document_search.* TO 'docuser'@'%';
FLUSH PRIVILEGES;

-- Show final table status
SELECT 
    TABLE_NAME,
    ENGINE,
    TABLE_ROWS,
    AVG_ROW_LENGTH,
    DATA_LENGTH,
    INDEX_LENGTH,
    CREATE_TIME
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'document_search'
ORDER BY TABLE_NAME;