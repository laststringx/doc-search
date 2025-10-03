# Enterprise Document Search Service

A distributed document search service built with Spring Boot, designed to handle millions of documents with sub-second response times. This service supports multi-tenancy, fault tolerance, and horizontal scalability.

## Technology Stack

- **Java 17** - Runtime environment
- **Spring Boot 3.2.0** - Application framework
- **MySQL 8.0** - Primary database
- **Elasticsearch 8.11** - Search engine
- **Redis 7** - Caching layer
- **Apache Kafka** - Message broker
- **Docker & Docker Compose** - Containerization
- **Maven** - Build tool
- **Lombok** - Code generation

## Features

- Multi-tenant document management
- Full-text search with relevance ranking
- RESTful API with pagination
- Distributed architecture
- Containerized deployment
- Health monitoring and metrics
- Fault tolerance and scalability

## Quick Start

### Prerequisites

- Java 17+
- Docker & Docker Compose
- Maven 3.6+

### Running with Docker Compose

1. Clone the repository
2. Navigate to the project directory
3. Start all services:

```bash
docker-compose up --build
```

This will start:
- MySQL database on port 3306
- Redis cache on port 6379
- Elasticsearch on port 9200
- Kafka on port 9092
- Application on port 8080
- Kafka UI on port 8081

### API Endpoints

#### Health Check
- `GET /` - Welcome message
- `GET /api/v1/health` - Application health
- `GET /api/v1/info` - Application information

#### Document Management (require X-Tenant-ID header)
- `GET /api/v1/documents` - List documents
- `GET /api/v1/documents/{id}` - Get document by ID
- `POST /api/v1/documents` - Create document
- `PUT /api/v1/documents/{id}` - Update document
- `DELETE /api/v1/documents/{id}` - Delete document
- `GET /api/v1/documents/search?query={text}` - Search documents
- `GET /api/v1/documents/recent` - Get recent documents
- `GET /api/v1/documents/count` - Get document count

### Example Usage

1. Check application health:
```bash
curl http://localhost:8080/api/v1/health
```

2. Create a document:
```bash
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant1" \
  -d '{
    "title": "Sample Document",
    "content": "This is a sample document for testing",
    "author": "John Doe",
    "fileType": "text",
    "tags": "sample,test,demo"
  }'
```

3. Search documents:
```bash
curl "http://localhost:8080/api/v1/documents/search?query=sample" \
  -H "X-Tenant-ID: tenant1"
```

### Development

#### Running Locally

1. Start only the infrastructure services:
```bash
docker-compose up mysql redis elasticsearch kafka -d
```

2. Run the application:
```bash
mvn spring-boot:run
```

#### Running Tests

```bash
mvn test
```

#### Building the Application

```bash
mvn clean package
```

### Architecture

The application follows a microservices architecture with:

- **Controller Layer**: REST API endpoints
- **Service Layer**: Business logic
- **Repository Layer**: Data access
- **Entity Layer**: Data models

### Configuration Profiles

- `development`: Local development with external services
- `docker`: Containerized deployment
- `production`: Production environment (configured via environment variables)

### Monitoring

- Health checks: `/actuator/health`
- Metrics: `/actuator/metrics`
- Application info: `/actuator/info`
- Kafka UI: `http://localhost:8081`

## Project Structure

```
src/
├── main/
│   ├── java/com/enterprise/documentsearch/
│   │   ├── controller/     # REST controllers
│   │   ├── service/        # Business logic
│   │   ├── repository/     # Data access
│   │   ├── model/          # Entity models
│   │   ├── config/         # Configuration classes
│   │   └── DocumentSearchApplication.java
│   └── resources/
│       └── application.yml # Configuration
├── test/                   # Unit tests
└── docker/                 # Docker-related files
```

## Contributing

1. Follow Java coding standards
2. Add unit tests for new features
3. Update documentation as needed
4. Use Lombok annotations to reduce boilerplate code

## License

This project is for educational and assessment purposes.