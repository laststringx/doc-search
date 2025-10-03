# Enterprise Document Search API - Test Cases

## Overview
This document provides comprehensive test cases for the Enterprise Document Search API, covering all endpoints with positive, negative, and edge case scenarios.

## Test Environment Setup

### Prerequisites
- Application running on `http://localhost:8080`
- Valid test user credentials
- Sample files for upload testing (PDF, DOCX, TXT)
- Postman or similar API testing tool

### Test Data
```json
{
  "testUser": {
    "username": "testuser",
    "email": "testuser@example.com",
    "password": "TestPassword123!",
    "firstName": "Test",
    "lastName": "User",
    "tenantId": "test-tenant-001"
  },
  "invalidUser": {
    "username": "invaliduser",
    "password": "wrongpassword"
  }
}
```

---

## 1. Authentication Endpoints

### 1.1 User Registration - POST /api/v1/auth/register

#### Test Case 1.1.1: Successful User Registration
**Objective**: Verify successful user registration with valid data
**Precondition**: Username and email not already registered
**Test Data**:
```json
{
  "username": "newuser001",
  "email": "newuser001@example.com",
  "password": "ValidPassword123!",
  "firstName": "New",
  "lastName": "User",
  "tenantId": "tenant-001"
}
```
**Expected Result**:
- Status Code: 200 OK
- Response contains user object with id, username, email
- Password should not be in response
- Created and updated timestamps should be present

#### Test Case 1.1.2: Registration with Existing Username
**Objective**: Verify proper error handling for duplicate username
**Test Data**: Use existing username with different email
**Expected Result**:
- Status Code: 400 Bad Request
- Error message: "Username already exists"

#### Test Case 1.1.3: Registration with Existing Email
**Objective**: Verify proper error handling for duplicate email
**Test Data**: Use existing email with different username
**Expected Result**:
- Status Code: 400 Bad Request
- Error message: "Email already exists"

#### Test Case 1.1.4: Registration with Invalid Data
**Objective**: Verify validation errors for invalid input
**Test Scenarios**:
- Empty username: 400 error
- Invalid email format: 400 error
- Weak password: 400 error
- Missing required fields: 400 error
- Username too short/long: 400 error

#### Test Case 1.1.5: Registration with SQL Injection Attempt
**Objective**: Verify security against SQL injection
**Test Data**: Include SQL injection patterns in username/email
**Expected Result**: 400 Bad Request or safe handling

### 1.2 User Login - POST /api/v1/auth/login

#### Test Case 1.2.1: Successful Login with Username
**Objective**: Verify successful authentication with username
**Test Data**:
```json
{
  "usernameOrEmail": "testuser",
  "password": "TestPassword123!"
}
```
**Expected Result**:
- Status Code: 200 OK
- Response contains accessToken and refreshToken
- Response contains user object
- Token should be valid JWT format

#### Test Case 1.2.2: Successful Login with Email
**Objective**: Verify successful authentication with email
**Test Data**:
```json
{
  "usernameOrEmail": "testuser@example.com",
  "password": "TestPassword123!"
}
```
**Expected Result**:
- Status Code: 200 OK
- Valid tokens returned

#### Test Case 1.2.3: Login with Invalid Credentials
**Objective**: Verify proper error handling for wrong credentials
**Test Data**: Wrong password
**Expected Result**:
- Status Code: 401 Unauthorized
- Error message: "Invalid credentials"

#### Test Case 1.2.4: Login with Non-existent User
**Objective**: Verify handling of non-existent user
**Expected Result**:
- Status Code: 401 Unauthorized
- Generic error message (no user enumeration)

#### Test Case 1.2.5: Login with Inactive User
**Objective**: Verify handling of inactive user account
**Precondition**: User account is deactivated
**Expected Result**:
- Status Code: 401 Unauthorized
- Appropriate error message

### 1.3 Token Refresh - POST /api/v1/auth/refresh

#### Test Case 1.3.1: Successful Token Refresh
**Objective**: Verify successful token refresh with valid refresh token
**Test Data**: Valid refresh token
**Expected Result**:
- Status Code: 200 OK
- New access token returned
- Token should be different from previous one

#### Test Case 1.3.2: Refresh with Invalid Token
**Objective**: Verify error handling for invalid refresh token
**Test Data**: Malformed or expired refresh token
**Expected Result**:
- Status Code: 401 Unauthorized
- Error message about invalid token

#### Test Case 1.3.3: Refresh with Expired Token
**Objective**: Verify handling of expired refresh token
**Expected Result**:
- Status Code: 401 Unauthorized
- User should re-authenticate

### 1.4 User Profile - GET /api/v1/auth/profile

#### Test Case 1.4.1: Get Profile with Valid Token
**Objective**: Verify profile retrieval with valid authentication
**Expected Result**:
- Status Code: 200 OK
- Complete user profile returned
- Sensitive data (password) excluded

#### Test Case 1.4.2: Get Profile without Authentication
**Objective**: Verify authentication requirement
**Expected Result**:
- Status Code: 401 Unauthorized

#### Test Case 1.4.3: Get Profile with Expired Token
**Objective**: Verify token expiration handling
**Expected Result**:
- Status Code: 401 Unauthorized

### 1.5 User Logout - POST /api/v1/auth/logout

#### Test Case 1.5.1: Successful Logout
**Objective**: Verify successful logout operation
**Expected Result**:
- Status Code: 200 OK
- Success message returned

---

## 2. Document Management Endpoints

### 2.1 Get Documents - GET /api/v1/documents

#### Test Case 2.1.1: Get Documents with Valid Authentication
**Objective**: Verify document retrieval for authenticated user
**Expected Result**:
- Status Code: 200 OK
- Paginated response with documents
- Only tenant's documents returned

#### Test Case 2.1.2: Get Documents with Pagination
**Objective**: Verify pagination functionality
**Test Data**: `?page=0&size=5&sortBy=title&sortDir=asc`
**Expected Result**:
- Correct page size and content
- Proper sorting applied
- Pagination metadata included

#### Test Case 2.1.3: Get Documents without Authentication
**Objective**: Verify authentication requirement
**Expected Result**:
- Status Code: 401 Unauthorized

### 2.2 Get Document by ID - GET /api/v1/documents/{id}

#### Test Case 2.2.1: Get Existing Document
**Objective**: Verify retrieval of existing document
**Expected Result**:
- Status Code: 200 OK
- Complete document data returned

#### Test Case 2.2.2: Get Non-existent Document
**Objective**: Verify handling of invalid document ID
**Expected Result**:
- Status Code: 404 Not Found

#### Test Case 2.2.3: Get Document from Different Tenant
**Objective**: Verify tenant isolation
**Expected Result**:
- Status Code: 404 Not Found (security through obscurity)

### 2.3 Create Document - POST /api/v1/documents

#### Test Case 2.3.1: Create Valid Document
**Objective**: Verify successful document creation
**Test Data**:
```json
{
  "title": "Test Document",
  "content": "Test content",
  "author": "Test Author",
  "tags": "test,document"
}
```
**Expected Result**:
- Status Code: 200 OK
- Document created with generated ID
- Tenant ID automatically assigned

#### Test Case 2.3.2: Create Document with Invalid Data
**Objective**: Verify validation for required fields
**Test Data**: Missing title or content
**Expected Result**:
- Status Code: 400 Bad Request
- Validation error messages

### 2.4 Update Document - PUT /api/v1/documents/{id}

#### Test Case 2.4.1: Update Existing Document
**Objective**: Verify successful document update
**Expected Result**:
- Status Code: 200 OK
- Updated document returned
- lastModifiedDate updated

#### Test Case 2.4.2: Update Non-existent Document
**Objective**: Verify handling of invalid document ID
**Expected Result**:
- Status Code: 404 Not Found

### 2.5 Delete Document - DELETE /api/v1/documents/{id}

#### Test Case 2.5.1: Delete Existing Document
**Objective**: Verify successful document deletion
**Expected Result**:
- Status Code: 204 No Content

#### Test Case 2.5.2: Delete Non-existent Document
**Objective**: Verify handling of invalid document ID
**Expected Result**:
- Status Code: 404 Not Found

### 2.6 Search Documents - GET /api/v1/documents/search

#### Test Case 2.6.1: Search with Valid Query
**Objective**: Verify document search functionality
**Test Data**: `?query=test`
**Expected Result**:
- Status Code: 200 OK
- Relevant documents returned
- Search results from current tenant only

#### Test Case 2.6.2: Search with No Results
**Objective**: Verify handling of empty search results
**Test Data**: `?query=nonexistentterm`
**Expected Result**:
- Status Code: 200 OK
- Empty array returned

### 2.7 Get Document Count - GET /api/v1/documents/count

#### Test Case 2.7.1: Get Document Count
**Objective**: Verify document count retrieval
**Expected Result**:
- Status Code: 200 OK
- Correct count for tenant

---

## 3. File Upload Endpoints

### 3.1 Upload Single Document - POST /api/v1/upload/document

#### Test Case 3.1.1: Upload Valid PDF File
**Objective**: Verify successful PDF file upload and processing
**Test Data**: Valid PDF file with metadata
**Expected Result**:
- Status Code: 200 OK
- Document created with extracted content
- File metadata stored

#### Test Case 3.1.2: Upload Valid DOCX File
**Objective**: Verify DOCX file processing
**Test Data**: Valid Word document
**Expected Result**:
- Status Code: 200 OK
- Text content extracted properly

#### Test Case 3.1.3: Upload Unsupported File Type
**Objective**: Verify handling of unsupported file types
**Test Data**: .exe or other unsupported file
**Expected Result**:
- Status Code: 400 Bad Request
- Error message about unsupported file type

#### Test Case 3.1.4: Upload Oversized File
**Objective**: Verify file size limit enforcement
**Test Data**: File exceeding size limit
**Expected Result**:
- Status Code: 400 Bad Request
- File size error message

#### Test Case 3.1.5: Upload Corrupted File
**Objective**: Verify handling of corrupted files
**Test Data**: Corrupted PDF/DOCX file
**Expected Result**:
- Status Code: 400 Bad Request
- Processing error message

### 3.2 Batch Upload - POST /api/v1/upload/documents/batch

#### Test Case 3.2.1: Upload Multiple Valid Files
**Objective**: Verify batch upload functionality
**Test Data**: Array of valid files
**Expected Result**:
- Status Code: 200 OK
- Batch processing results
- Success/failure count

#### Test Case 3.2.2: Batch Upload with Mixed Results
**Objective**: Verify partial success handling
**Test Data**: Mix of valid and invalid files
**Expected Result**:
- Status Code: 200 OK
- Detailed results for each file

### 3.3 Get Supported File Types - GET /api/v1/upload/supported-types

#### Test Case 3.3.1: Get Supported Types
**Objective**: Verify supported file types information
**Expected Result**:
- Status Code: 200 OK
- List of supported file types
- Maximum file size information

---

## 4. Health & Monitoring Endpoints

### 4.1 Health Check - GET /api/v1/health

#### Test Case 4.1.1: Application Health Check
**Objective**: Verify application health status
**Expected Result**:
- Status Code: 200 OK
- Status: "UP"
- Service information included

### 4.2 Application Info - GET /api/v1/info

#### Test Case 4.2.1: Get Application Information
**Objective**: Verify application metadata retrieval
**Expected Result**:
- Status Code: 200 OK
- Application name, version, description

### 4.3 Welcome Message - GET /

#### Test Case 4.3.1: Get Welcome Message
**Objective**: Verify root endpoint accessibility
**Expected Result**:
- Status Code: 200 OK
- Welcome message and navigation links

---

## 5. Security & Performance Tests

### 5.1 Authentication Security Tests

#### Test Case 5.1.1: JWT Token Validation
**Objective**: Verify JWT token security
**Test Scenarios**:
- Malformed JWT tokens
- Expired tokens
- Tokens with invalid signatures
- Tokens with modified claims

#### Test Case 5.1.2: CORS Headers Validation
**Objective**: Verify CORS configuration
**Expected Result**: Proper CORS headers present

#### Test Case 5.1.3: SQL Injection Prevention
**Objective**: Verify protection against SQL injection
**Test Data**: Various SQL injection payloads
**Expected Result**: Safe handling of malicious input

### 5.2 Performance Tests

#### Test Case 5.2.1: Response Time Validation
**Objective**: Verify acceptable response times
**Expected Result**: All endpoints respond within 2 seconds

#### Test Case 5.2.2: Concurrent User Load
**Objective**: Verify system behavior under load
**Test Scenario**: Multiple concurrent users
**Expected Result**: System remains stable

#### Test Case 5.2.3: Large File Upload Performance
**Objective**: Verify performance with large files
**Test Data**: Maximum allowed file size
**Expected Result**: Acceptable processing time

---

## 6. Edge Cases & Error Handling

### 6.1 Boundary Value Tests

#### Test Case 6.1.1: Maximum Field Lengths
**Objective**: Verify handling of maximum field lengths
**Test Data**: Fields at maximum allowed length
**Expected Result**: Successful processing

#### Test Case 6.1.2: Empty/Null Values
**Objective**: Verify handling of empty values
**Test Data**: Empty strings, null values
**Expected Result**: Appropriate validation errors

### 6.2 Network & System Tests

#### Test Case 6.2.1: Network Timeout Simulation
**Objective**: Verify timeout handling
**Expected Result**: Graceful timeout handling

#### Test Case 6.2.2: Database Connection Issues
**Objective**: Verify database error handling
**Expected Result**: Appropriate error responses

---

## 7. Integration Test Scenarios

### 7.1 Complete User Journey
**Objective**: Test complete user workflow
**Steps**:
1. Register new user
2. Login and obtain tokens
3. Upload documents
4. Search and retrieve documents
5. Update document
6. Delete document
7. Logout

### 7.2 Multi-Tenant Isolation
**Objective**: Verify tenant data isolation
**Steps**:
1. Create users in different tenants
2. Upload documents for each tenant
3. Verify each tenant sees only their data

---

## Test Execution Guidelines

### Automated Testing
- Run tests in order (authentication first)
- Use dynamic variables for IDs and tokens
- Validate response schemas
- Check response times
- Verify security headers

### Manual Testing
- Test with various file types and sizes
- Verify UI responses match API responses
- Test error scenarios thoroughly
- Validate accessibility and usability

### Test Reporting
- Document all test results
- Include screenshots for failures
- Report performance metrics
- Track test coverage metrics

---

## Test Environment Configurations

### Local Development
```
Base URL: http://localhost:8080
Database: H2 in-memory
Authentication: JWT with 1-hour expiration
File Upload Limit: 10MB
```

### Staging Environment
```
Base URL: https://staging-api.enterprise-docs.com
Database: MySQL with connection pooling
Authentication: JWT with 15-minute expiration
File Upload Limit: 25MB
```

### Production Environment
```
Base URL: https://api.enterprise-docs.com
Database: MySQL with replication
Authentication: JWT with 5-minute expiration
File Upload Limit: 50MB
```