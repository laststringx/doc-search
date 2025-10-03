# Low-Level Architecture Documentation

## Enterprise Document Search - Technical Implementation Details

### Component Interaction Diagrams

#### 1. Detailed System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             CLIENT LAYER                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  Web Browser    │    Mobile App    │    Postman/API Client    │   CLI Tool  │
│  (React/Vue)    │   (React Native) │      (Testing)           │   (Scripts) │
└─────────────────┴──────────────────┴──────────────────────────┴─────────────┘
                                      │
                                  HTTPS/REST
                                      │
┌─────────────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                              Spring MVC                                    │
├─────────────────┬───────────────────┬───────────────────┬─────────────────────┤
│  AuthController │ DocumentController│FileUploadController│  HealthController  │
│                 │                   │                   │                     │
│ ┌─────────────┐ │ ┌───────────────┐ │ ┌───────────────┐ │ ┌─────────────────┐ │
│ │@PostMapping │ │ │@GetMapping    │ │ │@PostMapping   │ │ │@GetMapping      │ │
│ │/auth/login  │ │ │/documents     │ │ │/upload/single │ │ │/health          │ │
│ │@PostMapping │ │ │@PostMapping   │ │ │@PostMapping   │ │ │@GetMapping      │ │
│ │/auth/register│ │ │/documents     │ │ │/upload/batch  │ │ │/info            │ │
│ │@PostMapping │ │ │@PutMapping    │ │ │@GetMapping    │ │ └─────────────────┘ │
│ │/auth/refresh│ │ │/documents/{id}│ │ │/upload/types  │ │                     │
│ │@GetMapping  │ │ │@DeleteMapping │ │ └───────────────┘ │                     │
│ │/auth/profile│ │ │/documents/{id}│ │                   │                     │
│ │@PostMapping │ │ │@GetMapping    │ │                   │                     │
│ │/auth/logout │ │ │/search        │ │                   │                     │
│ └─────────────┘ │ │@GetMapping    │ │                   │                     │
│                 │ │/count         │ │                   │                     │
│                 │ └───────────────┘ │                   │                     │
└─────────────────┴───────────────────┴───────────────────┴─────────────────────┘
                                      │
                              Method Invocation
                                      │
┌─────────────────────────────────────────────────────────────────────────────┐
│                            SERVICE LAYER                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                          Business Logic Layer                              │
├─────────────────┬───────────────────┬───────────────────┬─────────────────────┤
│   UserService   │ DocumentService   │FileProcessingServ.│  SecurityService   │
│                 │                   │                   │                     │
│ ┌─────────────┐ │ ┌───────────────┐ │ ┌───────────────┐ │ ┌─────────────────┐ │
│ │authenticate │ │ │create         │ │ │processSingle  │ │ │generateToken    │ │
│ │register     │ │ │findAll        │ │ │processBatch   │ │ │validateToken    │ │
│ │findByEmail  │ │ │findById       │ │ │extractText    │ │ │refreshToken     │ │
│ │findByTenant │ │ │update         │ │ │validateFile   │ │ │getAuthorities   │ │
│ │updateProfile│ │ │delete         │ │ │checkFileType  │ │ │encodePassword   │ │
│ │validateUser │ │ │search         │ │ │generateMeta   │ │ │matchesPassword  │ │
│ │assignRole   │ │ │count          │ │ │storeFile      │ │ └─────────────────┘ │
│ └─────────────┘ │ │findByTenant   │ │ └───────────────┘ │                     │
│                 │ │findByStatus   │ │                   │                     │
│                 │ └───────────────┘ │                   │                     │
└─────────────────┴───────────────────┴───────────────────┴─────────────────────┘
                                      │
                              Repository Calls
                                      │
┌─────────────────────────────────────────────────────────────────────────────┐
│                         REPOSITORY LAYER                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                         Data Access Layer                                  │
├─────────────────────────┬───────────────────────────────────────────────────┤
│    UserRepository       │           DocumentRepository                     │
│                         │                                                   │
│ ┌─────────────────────┐ │ ┌───────────────────────────────────────────────┐ │
│ │@Query               │ │ │@Query                                         │ │
│ │findByEmail          │ │ │findByTenantId                                 │ │
│ │findByUsername       │ │ │findByTenantIdAndStatus                        │ │
│ │findByTenantId       │ │ │findByTenantIdAndTitleContaining               │ │
│ │findByEmailAndTenant │ │ │findByTenantIdAndCreatedDateBetween            │ │
│ │findActiveUsers      │ │ │countByTenantId                                │ │
│ │countByTenantId      │ │ │countByTenantIdAndStatus                       │ │
│ │findByRole           │ │ │@Modifying                                     │ │
│ │updateLastLogin      │ │ │updateDocumentStatus                          │ │
│ │@Modifying           │ │ │deleteByTenantIdAndId                          │ │
│ │softDeleteUser       │ │ │findByAuthorAndTenantId                        │ │
│ └─────────────────────┘ │ └───────────────────────────────────────────────┘ │
└─────────────────────────┴───────────────────────────────────────────────────┘
                                      │
                              JPA/Hibernate
                                      │
┌─────────────────────────────────────────────────────────────────────────────┐
│                          DATABASE LAYER                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                     MySQL 8.0 / H2 (Test)                                 │
├─────────────────────────┬───────────────────────────────────────────────────┤
│      USERS Table        │              DOCUMENTS Table                     │
│                         │                                                   │
│ ┌─────────────────────┐ │ ┌───────────────────────────────────────────────┐ │
│ │id (BIGINT, PK)      │ │ │id (BIGINT, PK)                                │ │
│ │username (VARCHAR)   │ │ │title (VARCHAR, NOT NULL)                      │ │
│ │email (VARCHAR)      │ │ │content (TEXT)                                 │ │
│ │password (VARCHAR)   │ │ │author (VARCHAR)                               │ │
│ │first_name (VARCHAR) │ │ │file_name (VARCHAR)                            │ │
│ │last_name (VARCHAR)  │ │ │file_type (VARCHAR)                            │ │
│ │tenant_id (VARCHAR)  │ │ │file_size (BIGINT)                             │ │
│ │active (BOOLEAN)     │ │ │status (ENUM)                                  │ │
│ │email_verified (BOOL)│ │ │tags (VARCHAR)                                 │ │
│ │created_date (TIMESTAMP)│ │created_date (TIMESTAMP)                       │ │
│ │last_modified (TIMESTAMP)│ │last_modified_date (TIMESTAMP)               │ │
│ │last_login (TIMESTAMP)│ │ │tenant_id (VARCHAR, NOT NULL)                 │ │
│ │version (BIGINT)     │ │ │version (BIGINT)                               │ │
│ └─────────────────────┘ │ └───────────────────────────────────────────────┘ │
└─────────────────────────┴───────────────────────────────────────────────────┘
```

#### 2. Security Flow Implementation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      SECURITY ARCHITECTURE                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐    ┌──────────────────┐    ┌─────────────────────────────┐ │
│  │   Client    │───▶│ SecurityFilter   │───▶│    JwtAuthenticationFilter │ │
│  │  Request    │    │     Chain        │    │                             │ │
│  └─────────────┘    └──────────────────┘    └─────────────────────────────┘ │
│                              │                              │               │
│                              ▼                              ▼               │
│  ┌─────────────────────────────────────────┐    ┌─────────────────────────┐ │
│  │           CorsFilter                    │    │      JwtTokenUtil       │ │
│  │  ┌─────────────────────────────────────┐│    │ ┌─────────────────────┐ │ │
│  │  │• Allow Origins: *                   ││    │ │• generateToken()    │ │ │
│  │  │• Allow Methods: GET,POST,PUT,DELETE ││    │ │• validateToken()    │ │ │
│  │  │• Allow Headers: *                   ││    │ │• extractUsername()  │ │ │
│  │  │• Allow Credentials: true            ││    │ │• extractClaims()    │ │ │
│  │  │• Max Age: 3600                      ││    │ │• isTokenExpired()   │ │ │
│  │  └─────────────────────────────────────┘│    │ │• refreshToken()     │ │ │
│  └─────────────────────────────────────────┘    │ └─────────────────────┘ │ │
│                              │                  └─────────────────────────┘ │
│                              ▼                              │               │
│  ┌─────────────────────────────────────────┐                ▼               │
│  │       Authorization Filter              │    ┌─────────────────────────┐ │
│  │ ┌─────────────────────────────────────┐ │    │   UserDetailsService   │ │
│  │ │• Role-based access control          │ │    │ ┌─────────────────────┐ │ │
│  │ │• Tenant isolation                   │ │    │ │• loadUserByUsername │ │ │
│  │ │• Method-level security              │ │    │ │• buildUserPrincipal │ │ │
│  │ │• Resource-level permissions         │ │    │ │• getAuthorities     │ │ │
│  │ │• Admin/Manager/User hierarchy       │ │    │ │• checkAccountStatus │ │ │
│  │ └─────────────────────────────────────┘ │    │ └─────────────────────┘ │ │
│  └─────────────────────────────────────────┘    └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 3. Data Flow Sequence

```
REQUEST PROCESSING FLOW:
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│ 1. HTTP Request                                                             │
│    ┌─────────┐                                                              │
│    │ Client  │──────────────────────────────────────────────────────────┐   │
│    └─────────┘                                                          │   │
│                                                                         │   │
│ 2. Security Filter Chain                                                │   │
│    ┌─────────────────────────────────────────────────────────────────┐  │   │
│    │ ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐│  │   │
│    │ │ CORS Filter │──│ JWT Filter  │──│ Authorization Filter        ││  │   │
│    │ └─────────────┘  └─────────────┘  └─────────────────────────────┘│  │   │
│    └─────────────────────────────────────────────────────────────────┘  │   │
│                                          │                              │   │
│ 3. Controller Layer                      ▼                              │   │
│    ┌─────────────────────────────────────────────────────────────────┐  │   │
│    │ ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐│  │   │
│    │ │@RestController │ │@RequestMapping │ │@Valid @RequestBody       ││  │   │
│    │ │@CrossOrigin   │ │@PreAuthorize   │ │@PathVariable @RequestParam││  │   │
│    │ └─────────────┘  └─────────────┘  └─────────────────────────────┘│  │   │
│    └─────────────────────────────────────────────────────────────────┘  │   │
│                                          │                              │   │
│ 4. Service Layer                         ▼                              │   │
│    ┌─────────────────────────────────────────────────────────────────┐  │   │
│    │ ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐│  │   │
│    │ │@Service     │  │@Transactional │ │Business Logic              ││  │   │
│    │ │@Autowired   │  │@Cacheable     │ │Validation & Processing     ││  │   │
│    │ └─────────────┘  └─────────────┘  └─────────────────────────────┘│  │   │
│    └─────────────────────────────────────────────────────────────────┘  │   │
│                                          │                              │   │
│ 5. Repository Layer                      ▼                              │   │
│    ┌─────────────────────────────────────────────────────────────────┐  │   │
│    │ ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐│  │   │
│    │ │@Repository  │  │@Query        │ │JPA/Hibernate               ││  │   │
│    │ │@Transactional │ │@Modifying   │ │Entity Mapping              ││  │   │
│    │ └─────────────┘  └─────────────┘  └─────────────────────────────┘│  │   │
│    └─────────────────────────────────────────────────────────────────┘  │   │
│                                          │                              │   │
│ 6. Database Layer                        ▼                              │   │
│    ┌─────────────────────────────────────────────────────────────────┐  │   │
│    │ ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐│  │   │
│    │ │Connection   │  │Transaction  │  │SQL Execution               ││  │   │
│    │ │Pool         │  │Management   │  │Result Mapping              ││  │   │
│    │ └─────────────┘  └─────────────┘  └─────────────────────────────┘│  │   │
│    └─────────────────────────────────────────────────────────────────┘  │   │
│                                          │                              │   │
│ 7. Response Processing                   ▼                              │   │
│    ┌─────────────────────────────────────────────────────────────────┐  │   │
│    │ ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐│  │   │
│    │ │Entity to DTO│  │JSON         │ │HTTP Response               ││  │   │
│    │ │Mapping      │  │Serialization│ │Status & Headers            ││  │   │
│    │ └─────────────┘  └─────────────┘  └─────────────────────────────┘│  │   │
│    └─────────────────────────────────────────────────────────────────┘  │   │
│                                          │                              │   │
│ 8. Client Response                       ▼                              ▼   │
│    ┌─────────────────────────────────────────────────────────────────────┐  │
│    │                     JSON Response                                   │  │
│    └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 4. Exception Handling Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       EXCEPTION HANDLING ARCHITECTURE                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │                        @ControllerAdvice                               │ │
│ │                   GlobalExceptionHandler                               │ │
│ ├─────────────────────────────────────────────────────────────────────────┤ │
│ │                                                                         │ │
│ │ @ExceptionHandler(ResourceNotFoundException.class)                     │ │
│ │ ┌─────────────────────────────────────────────────────────────────────┐ │ │
│ │ │ • HTTP Status: 404 NOT FOUND                                        │ │ │
│ │ │ • Error Code: RESOURCE_NOT_FOUND                                    │ │ │
│ │ │ • Message: Requested resource not found                             │ │ │
│ │ │ • Timestamp: Current timestamp                                      │ │ │
│ │ │ • Path: Request URI                                                 │ │ │
│ │ └─────────────────────────────────────────────────────────────────────┘ │ │
│ │                                                                         │ │
│ │ @ExceptionHandler(BusinessException.class)                             │ │
│ │ ┌─────────────────────────────────────────────────────────────────────┐ │ │
│ │ │ • HTTP Status: 400 BAD REQUEST                                      │ │ │
│ │ │ • Error Code: BUSINESS_RULE_VIOLATION                               │ │ │
│ │ │ • Message: Business rule validation failed                          │ │ │
│ │ │ • Details: Specific business rule information                       │ │ │
│ │ └─────────────────────────────────────────────────────────────────────┘ │ │
│ │                                                                         │ │
│ │ @ExceptionHandler(MethodArgumentNotValidException.class)               │ │
│ │ ┌─────────────────────────────────────────────────────────────────────┐ │ │
│ │ │ • HTTP Status: 400 BAD REQUEST                                      │ │ │
│ │ │ • Error Code: VALIDATION_ERROR                                      │ │ │
│ │ │ • Message: Request validation failed                                │ │ │
│ │ │ • Field Errors: List of validation failures                        │ │ │
│ │ └─────────────────────────────────────────────────────────────────────┘ │ │
│ │                                                                         │ │
│ │ @ExceptionHandler(AccessDeniedException.class)                         │ │
│ │ ┌─────────────────────────────────────────────────────────────────────┐ │ │
│ │ │ • HTTP Status: 403 FORBIDDEN                                        │ │ │
│ │ │ • Error Code: ACCESS_DENIED                                         │ │ │
│ │ │ • Message: Access denied to requested resource                      │ │ │
│ │ │ • User: Current authenticated user                                  │ │ │
│ │ └─────────────────────────────────────────────────────────────────────┘ │ │
│ │                                                                         │ │
│ │ @ExceptionHandler(Exception.class)                                     │ │
│ │ ┌─────────────────────────────────────────────────────────────────────┐ │ │
│ │ │ • HTTP Status: 500 INTERNAL SERVER ERROR                           │ │ │
│ │ │ • Error Code: INTERNAL_ERROR                                        │ │ │
│ │ │ • Message: An unexpected error occurred                             │ │ │
│ │ │ • Reference ID: UUID for error tracking                            │ │ │
│ │ └─────────────────────────────────────────────────────────────────────┘ │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 5. JWT Token Processing

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          JWT TOKEN LIFECYCLE                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│ 1. Token Generation                                                         │
│    ┌─────────────────────────────────────────────────────────────────────┐   │
│    │ Input: User credentials, Tenant ID, Roles                          │   │
│    │                            │                                        │   │
│    │                            ▼                                        │   │
│    │ JwtTokenUtil.generateToken()                                        │   │
│    │ ┌─────────────────────────────────────────────────────────────────┐ │   │
│    │ │ • Header: {"alg": "HS512", "typ": "JWT"}                        │ │   │
│    │ │ • Payload: {                                                    │ │   │
│    │ │     "sub": "username",                                          │ │   │
│    │ │     "tenantId": "tenant_123",                                   │ │   │
│    │ │     "roles": ["USER", "MANAGER"],                               │ │   │
│    │ │     "iat": 1633024800,                                          │ │   │
│    │ │     "exp": 1633111200                                           │ │   │
│    │ │   }                                                             │ │   │
│    │ │ • Signature: HMACSHA512(base64(header) + "." + base64(payload)) │ │   │
│    │ └─────────────────────────────────────────────────────────────────┘ │   │
│    └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                       │
│ 2. Token Validation                  ▼                                       │
│    ┌─────────────────────────────────────────────────────────────────────┐   │
│    │ JwtAuthenticationFilter.doFilterInternal()                         │   │
│    │ ┌─────────────────────────────────────────────────────────────────┐ │   │
│    │ │ 1. Extract token from Authorization header                      │ │   │
│    │ │ 2. Validate token signature                                     │ │   │
│    │ │ 3. Check token expiration                                       │ │   │
│    │ │ 4. Extract username and tenant ID                               │ │   │
│    │ │ 5. Load user details from database                              │ │   │
│    │ │ 6. Create Authentication object                                 │ │   │
│    │ │ 7. Set SecurityContext                                          │ │   │
│    │ └─────────────────────────────────────────────────────────────────┘ │   │
│    └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                       │
│ 3. Token Refresh                     ▼                                       │
│    ┌─────────────────────────────────────────────────────────────────────┐   │
│    │ AuthController.refreshToken()                                      │   │
│    │ ┌─────────────────────────────────────────────────────────────────┐ │   │
│    │ │ 1. Validate existing token (allow expired)                     │ │   │
│    │ │ 2. Extract user information                                     │ │   │
│    │ │ 3. Verify user is still active                                  │ │   │
│    │ │ 4. Generate new token with extended expiry                      │ │   │
│    │ │ 5. Return new token to client                                   │ │   │
│    │ └─────────────────────────────────────────────────────────────────┘ │   │
│    └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 6. File Upload Processing

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       FILE UPLOAD ARCHITECTURE                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │                    FileUploadController                                 │ │
│ │ ┌─────────────────────────────────────────────────────────────────────┐ │ │
│ │ │ @PostMapping("/upload/single")                                      │ │ │
│ │ │ ┌─────────────────────────────────────────────────────────────────┐ │ │ │
│ │ │ │ 1. Receive MultipartFile                                        │ │ │ │
│ │ │ │ 2. Validate file size (< 10MB)                                  │ │ │ │
│ │ │ │ 3. Validate file type (PDF, DOC, TXT)                           │ │ │ │
│ │ │ │ 4. Generate unique filename                                     │ │ │ │
│ │ │ │ 5. Extract tenant information                                   │ │ │ │
│ │ │ │ 6. Call FileProcessingService                                   │ │ │ │
│ │ │ └─────────────────────────────────────────────────────────────────┘ │ │ │
│ │ └─────────────────────────────────────────────────────────────────────┘ │ │
│ │                                      │                                   │ │
│ │ @PostMapping("/upload/batch")        ▼                                   │ │
│ │ ┌─────────────────────────────────────────────────────────────────────┐ │ │
│ │ │ 1. Receive MultipartFile[]                                          │ │ │
│ │ │ 2. Validate batch size (< 5 files)                                 │ │ │
│ │ │ 3. Process each file individually                                   │ │ │
│ │ │ 4. Collect results and errors                                       │ │ │
│ │ │ 5. Return batch processing summary                                  │ │ │
│ │ └─────────────────────────────────────────────────────────────────────┘ │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                      │                                       │
│                                      ▼                                       │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │                 FileProcessingService                                   │ │
│ │ ┌─────────────────────────────────────────────────────────────────────┐ │ │
│ │ │ processSingleFile(MultipartFile, String tenantId)                   │ │ │
│ │ │ ┌─────────────────────────────────────────────────────────────────┐ │ │ │
│ │ │ │ 1. File Validation:                                             │ │ │ │
│ │ │ │    • Check file is not empty                                    │ │ │ │
│ │ │ │    • Check file size <= MAX_FILE_SIZE                           │ │ │ │
│ │ │ │    • Check file type in ALLOWED_TYPES                           │ │ │ │
│ │ │ │    • Check filename length                                      │ │ │ │
│ │ │ │                                                                 │ │ │ │
│ │ │ │ 2. Text Extraction:                                             │ │ │ │
│ │ │ │    • PDF: Apache PDFBox                                         │ │ │ │
│ │ │ │    • DOC/DOCX: Apache POI                                       │ │ │ │
│ │ │ │    • TXT: Direct reading                                        │ │ │ │
│ │ │ │                                                                 │ │ │ │
│ │ │ │ 3. Metadata Generation:                                         │ │ │ │
│ │ │ │    • File size, type, original name                             │ │ │ │
│ │ │ │    • Content hash for deduplication                             │ │ │ │
│ │ │ │    • Creation timestamp                                         │ │ │ │
│ │ │ │                                                                 │ │ │ │
│ │ │ │ 4. Document Creation:                                           │ │ │ │
│ │ │ │    • Create Document entity                                     │ │ │ │
│ │ │ │    • Set tenant isolation                                       │ │ │ │
│ │ │ │    • Set status to PROCESSING                                   │ │ │ │
│ │ │ │    • Save to database                                           │ │ │ │
│ │ │ │                                                                 │ │ │ │
│ │ │ │ 5. File Storage:                                                │ │ │ │
│ │ │ │    • Generate unique storage path                               │ │ │ │
│ │ │ │    • Save file to filesystem/cloud                              │ │ │ │
│ │ │ │    • Update document with file path                             │ │ │ │
│ │ │ │    • Set status to ACTIVE                                       │ │ │ │
│ │ │ └─────────────────────────────────────────────────────────────────┘ │ │ │
│ │ └─────────────────────────────────────────────────────────────────────┘ │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Performance and Scalability Considerations

#### Caching Strategy
```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CACHING LAYERS                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│ Application Cache (Spring Cache)                                            │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ • @Cacheable("users")                                                   │ │
│ │ • @Cacheable("documents")                                               │ │
│ │ • @CacheEvict for updates                                               │ │
│ │ • TTL: 15 minutes                                                       │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│ Database Connection Pool (HikariCP)                                         │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ • Maximum pool size: 20                                                 │ │
│ │ • Minimum idle: 5                                                       │ │
│ │ • Connection timeout: 30 seconds                                        │ │
│ │ • Idle timeout: 10 minutes                                              │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│ HTTP Response Caching                                                       │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ • ETags for document responses                                          │ │
│ │ • Last-Modified headers                                                 │ │
│ │ • Cache-Control headers                                                 │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Database Optimization
```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         DATABASE OPTIMIZATION                              │
├─────────────────────────────────────────────────────────────────────────────┤
│ Indexes:                                                                    │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ Users Table:                                                            │ │
│ │ • PRIMARY KEY (id)                                                      │ │
│ │ • UNIQUE INDEX (email)                                                  │ │
│ │ • UNIQUE INDEX (username)                                               │ │
│ │ • INDEX (tenant_id)                                                     │ │
│ │ • INDEX (active)                                                        │ │
│ │                                                                         │ │
│ │ Documents Table:                                                        │ │
│ │ • PRIMARY KEY (id)                                                      │ │
│ │ • INDEX (tenant_id)                                                     │ │
│ │ • INDEX (title)                                                         │ │
│ │ • INDEX (created_date)                                                  │ │
│ │ • INDEX (status)                                                        │ │
│ │ • COMPOSITE INDEX (tenant_id, status)                                  │ │
│ │ • COMPOSITE INDEX (tenant_id, created_date)                            │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│ Query Optimization:                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ • Lazy loading for relationships                                        │ │
│ │ • Pagination for large result sets                                      │ │
│ │ • Batch operations for bulk updates                                     │ │
│ │ • Query hints for performance                                           │ │
│ │ • Connection pooling                                                    │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Testing Strategy Implementation

#### Unit Testing Architecture
```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            TESTING LAYERS                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│ Controller Tests (@WebMvcTest)                                              │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ • Mock MVC for HTTP testing                                             │ │
│ │ • @MockBean for service layer                                           │ │
│ │ • JSON request/response validation                                      │ │
│ │ • Security context testing                                              │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│ Service Tests (@ExtendWith(MockitoExtension.class))                        │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ • @Mock for repositories                                                │ │
│ │ • Business logic validation                                             │ │
│ │ • Exception handling testing                                            │ │
│ │ • Transaction testing                                                   │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│ Repository Tests (@DataJpaTest)                                             │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ • In-memory H2 database                                                 │ │
│ │ • @Sql for test data setup                                              │ │
│ │ • Custom query testing                                                  │ │
│ │ • Entity relationship validation                                        │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│ Integration Tests (@SpringBootTest)                                         │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ • Full application context                                              │ │
│ │ • TestRestTemplate for API calls                                        │ │
│ │ • Database transactions                                                 │ │
│ │ • Security integration                                                  │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

This low-level architecture provides detailed technical specifications for implementing, maintaining, and scaling the Enterprise Document Search application with comprehensive understanding of component interactions, data flow, and system internals.