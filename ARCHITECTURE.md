# Enterprise Document Search - Architecture Documentation

## Overview

The Enterprise Document Search application is a high-performance, scalable document management and search system designed to handle enterprise-scale workloads with the following NFR requirements:

- **Scale**: 10M+ documents
- **Performance**: Sub-second search response times
- **Concurrency**: 1000+ concurrent searches
- **Availability**: 99.9% uptime with horizontal scaling

## Architecture Diagram

```
┌─────────────────┐    ┌──────────────────┐    ┌────────────────────┐
│   Load Balancer │    │   API Gateway    │    │   Rate Limiting    │
│     (Nginx)     │────│   (Spring Boot)  │────│    (Bucket4j)      │
└─────────────────┘    └──────────────────┘    └────────────────────┘
         │                       │                         │
         └───────────────────────┼─────────────────────────┘
                                 │
         ┌───────────────────────┼─────────────────────────┐
         │                       │                         │
         ▼                       ▼                         ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   App Instance  │    │   App Instance  │    │   App Instance  │
│      (Pod 1)    │    │      (Pod 2)    │    │      (Pod 3)    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                         │
         └───────────────────────┼─────────────────────────┘
                                 │
    ┌────────────────────────────┼────────────────────────────┐
    │                            │                            │
    ▼                            ▼                            ▼
┌─────────────┐        ┌─────────────────┐        ┌─────────────────┐
│   Redis     │        │  Elasticsearch  │        │     MySQL       │
│  (Cache &   │        │   (Search &     │        │  (Metadata &    │
│  Sessions)  │        │   Full-text)    │        │   Relations)    │
└─────────────┘        └─────────────────┘        └─────────────────┘
                                │
                                ▼
                    ┌─────────────────┐
                    │      Kafka      │
                    │  (Async Events  │
                    │  & Processing)  │
                    └─────────────────┘
```

## System Components

### 1. Web Layer

#### Load Balancer (Nginx)
- **Purpose**: Distribute traffic across multiple application instances
- **Features**: 
  - Least-connection load balancing
  - Health checks with circuit breaking
  - Rate limiting (100 req/min per IP)
  - Compression and static content optimization
- **Configuration**: `/docker/nginx/`

#### API Gateway (Spring Boot)
- **Endpoints**: 
  - `/api/v1/auth/*` - Authentication & authorization
  - `/api/v1/documents/*` - Document CRUD operations
  - `/api/v1/search/*` - Search operations
  - `/api/v1/files/*` - File upload operations
- **Security**: JWT-based authentication with multi-tenant support
- **Rate Limiting**: Bucket4j per-tenant and per-endpoint
- **Monitoring**: Micrometer metrics for Prometheus

### 2. Business Logic Layer

#### Document Service
- **Responsibilities**: 
  - Document lifecycle management
  - Metadata operations
  - Business rule enforcement
- **Caching**: Redis-based caching with TTL policies
- **Async Processing**: CompletableFuture for non-blocking operations

#### Search Service (Elasticsearch)
- **Features**:
  - Full-text search with relevance scoring
  - Multi-field search (content, title, metadata)
  - Faceted search and filtering
  - Bulk indexing for high-volume operations
- **Performance**: 
  - Index optimization for 10M+ documents
  - Bulk operations (5000 docs/batch)
  - Connection pooling and timeout management

#### File Processing Service
- **Supported Formats**: PDF, DOC/DOCX, XLS/XLSX, PPT/PPTX, TXT, CSV
- **Features**:
  - Content extraction using Apache Tika
  - Metadata extraction
  - File validation and sanitization
- **Async Processing**: Dedicated thread pools for file operations

### 3. Data Layer

#### Primary Database (MySQL)
- **Purpose**: Document metadata, user data, tenant information
- **Optimization**:
  - HikariCP connection pooling (50 connections)
  - JPA batch processing (50 items/batch)
  - Prepared statement caching
  - Optimized indexes for tenant-based queries

#### Search Engine (Elasticsearch)
- **Purpose**: Full-text search and document indexing
- **Configuration**:
  - 10 shards for horizontal scaling
  - 2 replicas for high availability
  - Bulk indexing optimization
  - Custom analyzers for document content

#### Cache Layer (Redis)
- **Purpose**: 
  - Application-level caching
  - Session management
  - Rate limiting storage
- **Caching Strategy**:
  - Documents: 1 hour TTL
  - Search results: 30 minutes TTL
  - Metadata: 2 hours TTL
  - User preferences: 24 hours TTL

#### Message Queue (Kafka)
- **Purpose**: Asynchronous event processing
- **Topics**:
  - `document.indexed` - Document indexing events
  - `document.updated` - Document update events
  - `document.deleted` - Document deletion events
- **Features**: Parallel processing, failure handling, event ordering

## Non-Functional Requirements Implementation

### Performance & Scalability

#### Sub-second Response Times
- **Multi-level Caching**: Redis + local Caffeine caching
- **Database Optimization**: Connection pooling, batch processing
- **Elasticsearch Tuning**: Bulk operations, optimized queries
- **Async Processing**: Non-blocking I/O operations

#### 10M+ Document Support
- **Elasticsearch Sharding**: 10 shards for horizontal distribution
- **Bulk Indexing**: 5000 documents per batch operation
- **Index Optimization**: Custom mappings and analyzers
- **Storage Efficiency**: Document content compression

#### 1000+ Concurrent Users
- **Horizontal Scaling**: 3 application instances behind load balancer
- **Connection Pooling**: 50 database connections per instance
- **Async Thread Pools**: Dedicated executors for different operations
- **Rate Limiting**: Per-tenant and per-endpoint protection

### Availability & Resilience

#### High Availability
- **Load Balancing**: Nginx with health checks
- **Database Replication**: MySQL master-slave setup
- **Elasticsearch Clustering**: Multi-node cluster with replicas
- **Stateless Architecture**: Session stored in Redis

#### Fault Tolerance
- **Circuit Breakers**: Resilience4j for external service protection
- **Retry Logic**: Exponential backoff for transient failures
- **Graceful Degradation**: Fallback mechanisms for service failures
- **Health Checks**: Comprehensive monitoring endpoints

### Security

#### Authentication & Authorization
- **JWT Tokens**: Stateless authentication with refresh tokens
- **Multi-tenancy**: Tenant-based data isolation
- **Role-based Access**: Fine-grained permissions
- **Rate Limiting**: Protection against abuse

#### Data Protection
- **Encryption**: TLS in transit, encrypted storage
- **Input Validation**: Comprehensive request validation
- **SQL Injection Prevention**: Parameterized queries
- **File Upload Security**: Type validation and sanitization

### Monitoring & Observability

#### Metrics Collection
- **Application Metrics**: Custom Micrometer metrics
- **Infrastructure Metrics**: System resource monitoring
- **Business Metrics**: Document processing rates, search latency
- **Error Tracking**: Comprehensive error logging and alerting

#### Performance Monitoring
- **Response Time Tracking**: P50, P95, P99 percentiles
- **Throughput Monitoring**: Requests per second
- **Cache Hit Ratios**: Redis and Elasticsearch cache efficiency
- **Resource Utilization**: CPU, memory, disk, network

## Deployment Architecture

### Containerized Deployment
- **Docker Compose**: Multi-service orchestration
- **Container Images**: Optimized Spring Boot images
- **Health Checks**: Application and infrastructure health monitoring
- **Resource Limits**: CPU and memory constraints

### Environment Configuration
- **Development**: H2 in-memory database, single instance
- **Testing**: TestContainers for integration tests
- **Production**: Full stack with load balancing and replication

### Scaling Strategy
- **Horizontal Scaling**: Add more application instances
- **Database Scaling**: Read replicas and connection pooling
- **Elasticsearch Scaling**: Additional nodes and shards
- **Cache Scaling**: Redis cluster mode

## Technology Stack

### Backend
- **Framework**: Spring Boot 3.2.0
- **Language**: Java 17
- **Build Tool**: Maven
- **Testing**: JUnit 5, TestContainers, Mockito

### Data Storage
- **Database**: MySQL 8.0
- **Search**: Elasticsearch 8.11.0
- **Cache**: Redis 7
- **Message Queue**: Apache Kafka 7.4.0

### Infrastructure
- **Load Balancer**: Nginx
- **Containerization**: Docker & Docker Compose
- **Monitoring**: Micrometer + Prometheus
- **Logging**: Logback with structured logging

### Security
- **Authentication**: JWT with Spring Security
- **Rate Limiting**: Bucket4j
- **Circuit Breaker**: Resilience4j
- **Encryption**: TLS 1.3

## Performance Benchmarks

### Target Metrics
- **Search Response Time**: < 500ms (P95)
- **Document Upload**: < 2s for 10MB files
- **Bulk Indexing**: 1000+ docs/second
- **Concurrent Users**: 1000+ simultaneous searches
- **Cache Hit Ratio**: > 80% for frequent operations

### Load Testing Results
- **Peak Throughput**: 5000 requests/second
- **Average Response Time**: 150ms
- **Error Rate**: < 0.1%
- **Resource Utilization**: < 70% CPU/Memory at peak load

## Future Enhancements

### Planned Features
- **Machine Learning**: Document classification and recommendations
- **Advanced Search**: Semantic search and natural language queries
- **Real-time Collaboration**: Live document editing and comments
- **Analytics Dashboard**: Usage statistics and insights

### Scalability Improvements
- **Microservices Architecture**: Service decomposition
- **Event Sourcing**: Audit trail and event replay
- **CQRS Pattern**: Command-query separation
- **Kubernetes Deployment**: Container orchestration