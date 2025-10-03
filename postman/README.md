# Enterprise Document Search API - Postman Collection

This directory contains Postman collection and environment files for testing the Enterprise Document Search API.

## Files

- `Enterprise-Document-Search-API.postman_collection.json` - Main API collection with all test cases
- `Enterprise-Document-Search.postman_environment.json` - Environment variables for the collection

## Import Instructions

1. Open Postman
2. Click "Import" in the top left
3. Drag and drop both JSON files or click "Upload Files" and select them
4. Select the "Enterprise Document Search Environment" environment from the dropdown in the top right

## Collection Structure

### 1. Health & Info
- **Welcome Message** - Tests the root endpoint
- **Health Check** - Validates application health status
- **Application Info** - Retrieves application metadata

### 2. Document Management
- **Create Document** - Creates a new document with full metadata
- **Create Second Document** - Creates additional document for testing
- **Get Document by ID** - Retrieves a specific document
- **Get All Documents** - Lists documents with pagination
- **Update Document** - Updates an existing document
- **Search Documents** - Full-text search functionality
- **Get Recent Documents** - Retrieves recently modified documents
- **Get Document Count** - Gets total document count for tenant

### 3. Multi-Tenant Tests
- **Create Document for Tenant2** - Tests tenant isolation
- **Verify Tenant Isolation** - Ensures tenant1 cannot access tenant2 data
- **Get Documents Count for Tenant2** - Validates tenant-specific counts

### 4. Error Handling Tests
- **Get Non-existent Document** - Tests 404 error handling
- **Update Non-existent Document** - Tests update error handling
- **Delete Non-existent Document** - Tests delete error handling

### 5. Cleanup
- **Delete Document** - Cleans up test data

## Environment Variables

- `baseUrl` - Application base URL (default: http://localhost:8080)
- `tenantId` - Primary tenant ID for testing (default: tenant1)
- `documentId` - Auto-populated from document creation
- `documentId2` - Auto-populated from second document creation
- `tenant2DocumentId` - Auto-populated from tenant2 document creation

## Test Features

### Automated Test Scripts
Each request includes automated test scripts that verify:
- HTTP status codes
- Response structure and content
- Data integrity
- Multi-tenant isolation
- Error handling

### Global Tests
- Response time validation (< 5000ms)
- Content-Type validation
- Pre-request scripts for environment setup

## Running the Tests

### Prerequisites
1. Ensure the Enterprise Document Search application is running
2. Database should be accessible (MySQL)
3. Application should be healthy at http://localhost:8080

### Execution Order
The tests are designed to run in sequence:

1. **Health & Info** - Validates application is running
2. **Document Management** - Tests core CRUD operations
3. **Multi-Tenant Tests** - Validates tenant isolation
4. **Error Handling Tests** - Tests edge cases
5. **Cleanup** - Removes test data

### Run Collection
1. Select the collection
2. Click "Run collection" 
3. Choose the environment
4. Click "Run Enterprise Document Search API"

## Expected Results

- All health checks should pass
- Document CRUD operations should work correctly
- Multi-tenant isolation should be enforced
- Error handling should return appropriate HTTP status codes
- Search functionality should return relevant results
- Pagination should work correctly

## Troubleshooting

### Common Issues

1. **Connection refused**
   - Ensure application is running on port 8080
   - Check if database is accessible

2. **404 errors on valid requests**
   - Verify the application started successfully
   - Check database connection and table creation

3. **Tenant isolation not working**
   - Verify X-Tenant-ID header is being sent
   - Check database data for tenant separation

### Environment Configuration

Make sure the environment variables are set correctly:
- `baseUrl` should match your application URL
- `tenantId` should be a valid tenant identifier

## API Documentation Reference

For detailed API documentation, refer to the main README.md file in the project root directory.

## Test Coverage

This collection provides comprehensive test coverage for:
- ✅ Health monitoring endpoints
- ✅ Document CRUD operations
- ✅ Search functionality
- ✅ Pagination and sorting
- ✅ Multi-tenant isolation
- ✅ Error handling and edge cases
- ✅ Data validation and integrity