# Enterprise Document Search - Quick Start

## Application Authentication

This application uses **tenant-based authentication**. Each user belongs to a specific tenant, and authentication requires:

1. **Email/Username** - User's email or username
2. **Password** - User's password  
3. **Tenant ID** - The tenant identifier

## Test Users (Test Profile)

When running with the `test` profile, the following users are automatically created:

### Regular User
- **Email**: `test@example.com`
- **Password**: `password123`
- **Tenant ID**: `tenant_123`
- **Username**: `testuser`

### Admin User  
- **Email**: `admin@example.com`
- **Password**: `admin123`
- **Tenant ID**: `tenant_123`
- **Username**: `admin`

## Login Request Format

```json
{
  "usernameOrEmail": "test@example.com",
  "password": "password123", 
  "tenantId": "tenant_123"
}
```

## Quick Test with cURL

```bash
# Login as regular user
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "usernameOrEmail": "test@example.com",
    "password": "password123",
    "tenantId": "tenant_123"
  }'

# Login as admin user
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "usernameOrEmail": "admin@example.com", 
    "password": "admin123",
    "tenantId": "tenant_123"
  }'
```

## Successful Login Response

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com",
    "firstName": "Test",
    "lastName": "User",
    "tenantId": "tenant_123",
    "roles": ["USER"],
    "active": true,
    "emailVerified": true
  }
}
```

## Common Issues

### "Invalid credentials" error
- Verify email/username, password, and **tenantId** are correct
- Remember that authentication is tenant-specific
- Check that the user exists in the specified tenant

### Application won't start
- Ensure Java 17+ is installed and JAVA_HOME is set
- Run with: `mvn spring-boot:run -DskipTests`
- Check that port 8080 is available

## Starting the Application

```bash
# Set JAVA_HOME (Windows)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot"

# Start application (skip tests for faster startup)
mvn spring-boot:run -DskipTests

# Or with test profile explicitly
mvn spring-boot:run -DskipTests -Dspring-boot.run.profiles=test
```

The application will start on http://localhost:8080 with the test profile active by default.