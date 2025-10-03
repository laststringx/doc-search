-- Initialize database with some basic settings
CREATE DATABASE IF NOT EXISTS enterprise_document_search CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE enterprise_document_search;

-- Create application user
CREATE USER IF NOT EXISTS 'appuser'@'%' IDENTIFIED BY 'apppassword';
GRANT ALL PRIVILEGES ON enterprise_document_search.* TO 'appuser'@'%';
FLUSH PRIVILEGES;

-- Set some basic configurations
SET GLOBAL max_allowed_packet = 16777216;
SET GLOBAL innodb_buffer_pool_size = 134217728;