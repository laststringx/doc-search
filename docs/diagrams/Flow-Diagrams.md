# Enterprise Document Search - Flow Diagrams

This document contains comprehensive Mermaid diagrams showing the various flows in the Enterprise Document Search application.

## 1. Authentication Flow

```mermaid
sequenceDiagram
    participant Client
    participant AuthController
    participant UserService
    participant SecurityConfig
    participant JwtTokenUtil
    participant Database

    Note over Client,Database: User Registration Flow
    Client->>+AuthController: POST /api/v1/auth/register
    AuthController->>+UserService: Check username/email exists
    UserService->>+Database: Query users table
    Database-->>-UserService: Return query result
    UserService-->>-AuthController: Validation result
    
    alt Username/Email exists
        AuthController-->>Client: 400 Bad Request (User exists)
    else User can be created
        AuthController->>+UserService: Create new user
        UserService->>+Database: Insert user record
        Database-->>-UserService: User created
        UserService-->>-AuthController: User object
        AuthController-->>Client: 200 OK (User registered)
    end

    Note over Client,Database: User Login Flow
    Client->>+AuthController: POST /api/v1/auth/login
    AuthController->>+SecurityConfig: Authenticate user
    SecurityConfig->>+UserService: Load user by username
    UserService->>+Database: Query user by username/email
    Database-->>-UserService: User record
    UserService-->>-SecurityConfig: UserDetails
    SecurityConfig-->>-AuthController: Authentication result
    
    alt Authentication successful
        AuthController->>+JwtTokenUtil: Generate access token
        JwtTokenUtil-->>-AuthController: JWT access token
        AuthController->>+JwtTokenUtil: Generate refresh token
        JwtTokenUtil-->>-AuthController: JWT refresh token
        AuthController->>+UserService: Update last login date
        UserService->>+Database: Update user record
        Database-->>-UserService: Update confirmed
        UserService-->>-AuthController: Success
        AuthController-->>Client: 200 OK (Tokens + User info)
    else Authentication failed
        AuthController-->>Client: 401 Unauthorized
    end

    Note over Client,Database: Token Refresh Flow
    Client->>+AuthController: POST /api/v1/auth/refresh (refresh token)
    AuthController->>+JwtTokenUtil: Validate refresh token
    JwtTokenUtil-->>-AuthController: Validation result
    
    alt Refresh token valid
        AuthController->>+JwtTokenUtil: Generate new access token
        JwtTokenUtil-->>-AuthController: New JWT token
        AuthController-->>Client: 200 OK (New access token)
    else Refresh token invalid/expired
        AuthController-->>Client: 401 Unauthorized
    end
```

## 2. Document Management Flow

```mermaid
sequenceDiagram
    participant Client
    participant JwtFilter
    participant DocumentController
    participant DocumentService
    participant Database
    participant User

    Note over Client,Database: JWT Authentication Filter
    Client->>+JwtFilter: Request with Authorization header
    JwtFilter->>+JwtTokenUtil: Validate JWT token
    JwtTokenUtil-->>-JwtFilter: Token validation result
    
    alt Token valid
        JwtFilter->>+UserService: Load user from token
        UserService->>+Database: Query user by username
        Database-->>-UserService: User details
        UserService-->>-JwtFilter: User object
        JwtFilter->>DocumentController: Forward request with user context
    else Token invalid/expired
        JwtFilter-->>Client: 401 Unauthorized
    end

    Note over Client,Database: Get Documents Flow
    DocumentController->>+DocumentService: Get documents for tenant
    DocumentService->>+Database: Query documents by tenant_id
    Database-->>-DocumentService: Document list
    DocumentService-->>-DocumentController: Paginated documents
    DocumentController-->>Client: 200 OK (Documents list)

    Note over Client,Database: Create Document Flow
    Client->>+DocumentController: POST /api/v1/documents
    DocumentController->>User: Extract tenant_id from authenticated user
    DocumentController->>+DocumentService: Create document with tenant_id
    DocumentService->>+Database: Insert document record
    Database-->>-DocumentService: Created document
    DocumentService-->>-DocumentController: Document object
    DocumentController-->>Client: 200 OK (Created document)

    Note over Client,Database: Search Documents Flow
    Client->>+DocumentController: GET /api/v1/documents/search?query=term
    DocumentController->>User: Extract tenant_id
    DocumentController->>+DocumentService: Search documents by tenant and query
    DocumentService->>+Database: Execute search query (tenant filtered)
    Database-->>-DocumentService: Matching documents
    DocumentService-->>-DocumentController: Search results
    DocumentController-->>Client: 200 OK (Search results)

    Note over Client,Database: Update Document Flow
    Client->>+DocumentController: PUT /api/v1/documents/{id}
    DocumentController->>User: Extract tenant_id
    DocumentController->>+DocumentService: Update document if belongs to tenant
    DocumentService->>+Database: Update document where id=? AND tenant_id=?
    Database-->>-DocumentService: Update result
    
    alt Document found and updated
        DocumentService-->>-DocumentController: Updated document
        DocumentController-->>Client: 200 OK (Updated document)
    else Document not found/unauthorized
        DocumentService-->>-DocumentController: Not found
        DocumentController-->>Client: 404 Not Found
    end

    Note over Client,Database: Delete Document Flow
    Client->>+DocumentController: DELETE /api/v1/documents/{id}
    DocumentController->>User: Extract tenant_id
    DocumentController->>+DocumentService: Delete document if belongs to tenant
    DocumentService->>+Database: DELETE WHERE id=? AND tenant_id=?
    Database-->>-DocumentService: Deletion result
    
    alt Document found and deleted
        DocumentService-->>-DocumentController: Success
        DocumentController-->>Client: 204 No Content
    else Document not found/unauthorized
        DocumentService-->>-DocumentController: Not found
        DocumentController-->>Client: 404 Not Found
    end
```

## 3. File Upload and Processing Flow

```mermaid
sequenceDiagram
    participant Client
    participant FileUploadController
    participant FileProcessingService
    participant ApacheTika
    participant DocumentService
    participant Database

    Note over Client,Database: Single File Upload Flow
    Client->>+FileUploadController: POST /api/v1/upload/document (multipart file)
    FileUploadController->>User: Extract tenant_id from authenticated user
    
    FileUploadController->>+FileProcessingService: Process uploaded file
    FileProcessingService->>FileProcessingService: Validate file type and size
    
    alt File validation passes
        FileProcessingService->>+ApacheTika: Extract content from file
        ApacheTika-->>-FileProcessingService: Extracted text content
        FileProcessingService->>FileProcessingService: Create Document object with metadata
        FileProcessingService-->>-FileUploadController: Document object
        
        FileUploadController->>+DocumentService: Save document
        DocumentService->>+Database: Insert document with extracted content
        Database-->>-DocumentService: Saved document
        DocumentService-->>-FileUploadController: Document with ID
        
        FileUploadController-->>Client: 200 OK (Upload success + document info)
    else File validation fails
        FileProcessingService-->>-FileUploadController: Validation error
        FileUploadController-->>Client: 400 Bad Request (Validation error)
    end

    Note over Client,Database: Batch File Upload Flow
    Client->>+FileUploadController: POST /api/v1/upload/documents/batch (multiple files)
    FileUploadController->>FileUploadController: Initialize batch processing results
    
    loop For each uploaded file
        FileUploadController->>+FileProcessingService: Process individual file
        FileProcessingService->>FileProcessingService: Validate file
        
        alt File valid
            FileProcessingService->>+ApacheTika: Extract content
            ApacheTika-->>-FileProcessingService: Content
            FileProcessingService-->>-FileUploadController: Document object
            
            FileUploadController->>+DocumentService: Save document
            DocumentService->>+Database: Insert document
            Database-->>-DocumentService: Saved document
            DocumentService-->>-FileUploadController: Success
            
            FileUploadController->>FileUploadController: Add to success list
        else File invalid
            FileProcessingService-->>-FileUploadController: Error
            FileUploadController->>FileUploadController: Add to failed list
        end
    end
    
    FileUploadController-->>Client: 200 OK (Batch results: success/failed counts)

    Note over Client,Database: Supported File Types Query
    Client->>+FileUploadController: GET /api/v1/upload/supported-types
    FileUploadController->>+FileProcessingService: Get supported types info
    FileProcessingService-->>-FileUploadController: Supported types and limits
    FileUploadController-->>Client: 200 OK (Supported types info)
```

## 4. Security and Authorization Flow

```mermaid
flowchart TD
    A[Client Request] --> B{Has Authorization Header?}
    B -->|No| C[Return 401 Unauthorized]
    B -->|Yes| D[Extract JWT Token]
    
    D --> E{Token Format Valid?}
    E -->|No| C
    E -->|Yes| F[Validate Token Signature]
    
    F --> G{Signature Valid?}
    G -->|No| C
    G -->|Yes| H[Check Token Expiration]
    
    H --> I{Token Expired?}
    I -->|Yes| C
    I -->|No| J[Extract User Claims]
    
    J --> K[Load User from Database]
    K --> L{User Exists and Active?}
    L -->|No| C
    L -->|Yes| M[Set Authentication Context]
    
    M --> N[Check Endpoint Authorization]
    N --> O{User Has Required Role?}
    O -->|No| P[Return 403 Forbidden]
    O -->|Yes| Q[Process Request]
    
    Q --> R[Apply Tenant Filtering]
    R --> S[Execute Business Logic]
    S --> T[Return Response]

    style A fill:#e1f5fe
    style C fill:#ffebee
    style P fill:#fff3e0
    style T fill:#e8f5e8
```

## 5. Error Handling Flow

```mermaid
flowchart TD
    A[Exception Thrown] --> B{Exception Type?}
    
    B -->|ValidationException| C[400 Bad Request]
    B -->|AuthenticationException| D[401 Unauthorized]
    B -->|AccessDeniedException| E[403 Forbidden]
    B -->|ResourceNotFoundException| F[404 Not Found]
    B -->|BusinessException| G[422 Unprocessable Entity]
    B -->|FileUploadException| H[413 Payload Too Large]
    B -->|MethodArgumentNotValidException| I[400 Bad Request with Field Errors]
    B -->|DataIntegrityViolationException| J[409 Conflict]
    B -->|Other RuntimeException| K[500 Internal Server Error]
    
    C --> L[Create Error Response with Validation Details]
    D --> M[Create Error Response with Auth Message]
    E --> N[Create Error Response with Permission Message]
    F --> O[Create Error Response with Not Found Message]
    G --> P[Create Error Response with Business Rule Message]
    H --> Q[Create Error Response with File Size Message]
    I --> R[Create Error Response with Field Validation Details]
    J --> S[Create Error Response with Conflict Message]
    K --> T[Create Generic Error Response]
    
    L --> U[Log Error Details]
    M --> U
    N --> U
    O --> U
    P --> U
    Q --> U
    R --> U
    S --> U
    T --> U
    
    U --> V[Return Structured Error Response]
    
    style A fill:#ffebee
    style V fill:#e8f5e8
    style U fill:#fff3e0
```

## 6. Database Transaction Flow

```mermaid
sequenceDiagram
    participant Service
    participant TransactionManager
    participant Database
    participant ErrorHandler

    Note over Service,ErrorHandler: Transactional Operation Flow
    Service->>+TransactionManager: Begin Transaction
    TransactionManager->>+Database: START TRANSACTION
    Database-->>-TransactionManager: Transaction started
    TransactionManager-->>-Service: Transaction context
    
    Service->>+Database: Execute SQL Operation 1
    Database-->>-Service: Result 1
    
    Service->>+Database: Execute SQL Operation 2
    Database-->>-Service: Result 2
    
    alt All operations successful
        Service->>+TransactionManager: Commit Transaction
        TransactionManager->>+Database: COMMIT
        Database-->>-TransactionManager: Transaction committed
        TransactionManager-->>-Service: Success
    else Any operation fails
        Service->>+TransactionManager: Rollback Transaction
        TransactionManager->>+Database: ROLLBACK
        Database-->>-TransactionManager: Transaction rolled back
        TransactionManager-->>-Service: Rollback complete
        Service->>+ErrorHandler: Handle exception
        ErrorHandler-->>-Service: Error response
    end
```

## 7. Caching Strategy Flow (Future Enhancement)

```mermaid
flowchart TD
    A[Request for Data] --> B{Data in Cache?}
    
    B -->|Yes| C[Check Cache Expiry]
    C --> D{Cache Valid?}
    D -->|Yes| E[Return Cached Data]
    D -->|No| F[Remove from Cache]
    
    B -->|No| G[Query Database]
    F --> G
    
    G --> H[Get Data from Database]
    H --> I[Store in Cache with TTL]
    I --> J[Return Data to Client]
    
    E --> K[Update Cache Access Time]
    K --> L[Return Data to Client]
    
    style A fill:#e1f5fe
    style E fill:#e8f5e8
    style L fill:#e8f5e8
    style J fill:#e8f5e8
```

## 8. Monitoring and Health Check Flow

```mermaid
sequenceDiagram
    participant Client
    participant HealthController
    participant Database
    participant FileSystem
    participant ExternalServices

    Note over Client,ExternalServices: Health Check Flow
    Client->>+HealthController: GET /api/v1/health
    
    HealthController->>+Database: Check database connectivity
    Database-->>-HealthController: Connection status
    
    HealthController->>+FileSystem: Check disk space
    FileSystem-->>-HealthController: Disk status
    
    HealthController->>+ExternalServices: Check external dependencies
    ExternalServices-->>-HealthController: External service status
    
    HealthController->>HealthController: Aggregate health status
    
    alt All systems healthy
        HealthController-->>Client: 200 OK (Status: UP)
    else Any system unhealthy
        HealthController-->>Client: 503 Service Unavailable (Status: DOWN)
    end
    
    Note over Client,ExternalServices: Application Info Flow
    Client->>+HealthController: GET /api/v1/info
    HealthController->>HealthController: Gather application metadata
    HealthController-->>Client: 200 OK (App info)
```

## 9. API Gateway Integration Flow (Future Enhancement)

```mermaid
sequenceDiagram
    participant Client
    participant APIGateway
    participant LoadBalancer
    participant AppInstance1
    participant AppInstance2
    participant Database

    Note over Client,Database: API Gateway Request Flow
    Client->>+APIGateway: API Request
    APIGateway->>APIGateway: Rate limiting check
    APIGateway->>APIGateway: Authentication validation
    APIGateway->>APIGateway: Request logging
    
    APIGateway->>+LoadBalancer: Forward request
    LoadBalancer->>LoadBalancer: Health check instances
    
    alt Instance 1 healthy
        LoadBalancer->>+AppInstance1: Route request
        AppInstance1->>+Database: Process request
        Database-->>-AppInstance1: Data response
        AppInstance1-->>-LoadBalancer: API response
    else Instance 1 unhealthy
        LoadBalancer->>+AppInstance2: Route to backup
        AppInstance2->>+Database: Process request
        Database-->>-AppInstance2: Data response
        AppInstance2-->>-LoadBalancer: API response
    end
    
    LoadBalancer-->>-APIGateway: Response
    APIGateway->>APIGateway: Response logging
    APIGateway-->>Client: Final response
```

## 10. Multi-Tenant Data Isolation Flow

```mermaid
flowchart TD
    A[Authenticated Request] --> B[Extract User from JWT]
    B --> C[Get Tenant ID from User]
    
    C --> D{Tenant ID Present?}
    D -->|No| E[Return 403 Forbidden]
    D -->|Yes| F[Add Tenant Filter to Query]
    
    F --> G[Execute Database Query]
    G --> H[Apply Row-Level Security]
    
    H --> I[Query: SELECT * FROM table WHERE tenant_id = ?]
    I --> J[Return Only Tenant Data]
    
    J --> K[Response to Client]
    
    style A fill:#e1f5fe
    style E fill:#ffebee
    style K fill:#e8f5e8
    style H fill:#fff3e0
```