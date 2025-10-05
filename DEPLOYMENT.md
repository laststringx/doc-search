# Enterprise Document Search - Production Deployment Guide

## Overview

This guide provides step-by-step instructions for deploying the Enterprise Document Search system to production. The system is designed to handle:

- **1000+ concurrent searches per second**
- **10+ million documents across multiple tenants**
- **Sub-500ms response times (95th percentile)**
- **Horizontal scalability and high availability**

## Architecture Components

### Core Services
- **Spring Boot Application** (3 instances) - Reactive WebFlux for high concurrency
- **Nginx Load Balancer** - Traffic distribution and rate limiting
- **MySQL Cluster** - Master-replica setup with partitioning
- **Elasticsearch Cluster** - 3-node cluster for document search
- **Redis Cluster** - 6-node cluster for distributed caching
- **Monitoring Stack** - Prometheus, Grafana, Alertmanager

### Infrastructure Requirements

#### Minimum Production Specifications
- **Application Servers**: 3 instances (4 vCPU, 8GB RAM each)
- **Database Servers**: 3 instances (8 vCPU, 16GB RAM each)
- **Elasticsearch Nodes**: 3 instances (8 vCPU, 16GB RAM, 500GB SSD each)
- **Redis Cluster**: 6 instances (2 vCPU, 4GB RAM each)
- **Load Balancer**: 1 instance (2 vCPU, 4GB RAM)
- **Monitoring**: 2 instances (4 vCPU, 8GB RAM each)

## Pre-Deployment Checklist

### Environment Setup
- [ ] Docker and Docker Compose installed on all servers
- [ ] Network security groups configured
- [ ] SSL certificates obtained and configured
- [ ] DNS records configured
- [ ] Backup and monitoring systems ready

### Configuration Files
- [ ] Environment variables set in `.env` file
- [ ] Database credentials secured
- [ ] Redis cluster authentication configured
- [ ] Elasticsearch security settings applied
- [ ] JWT secrets generated and secured

## Deployment Steps

### 1. Environment Configuration

Create a `.env` file with production settings:

```bash
# Application Configuration
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8080

# Database Configuration
DB_HOST=mysql-primary
DB_PORT=3306
DB_NAME=document_search
DB_USER=search_user
DB_PASSWORD=<SECURE_PASSWORD>

# Redis Configuration
REDIS_PASSWORD=<SECURE_REDIS_PASSWORD>

# Elasticsearch Configuration
ELASTIC_USER=elastic
ELASTIC_PASSWORD=<SECURE_ELASTIC_PASSWORD>

# Security Configuration
JWT_SECRET=<SECURE_JWT_SECRET_256_BITS>

# Resource Limits
JVM_MAX_HEAP=6g
JVM_MIN_HEAP=2g

# Monitoring
PROMETHEUS_ENABLED=true
GRAFANA_ADMIN_PASSWORD=<SECURE_GRAFANA_PASSWORD>
```

### 2. Deploy Infrastructure Services

Start the infrastructure services first:

```bash
# Start database cluster
docker-compose up -d mysql-primary mysql-replica-1 mysql-replica-2

# Wait for database initialization
sleep 60

# Start Elasticsearch cluster
docker-compose up -d elasticsearch-node-1 elasticsearch-node-2 elasticsearch-node-3

# Wait for Elasticsearch cluster formation
sleep 90

# Start Redis cluster
docker-compose up -d redis-node-1 redis-node-2 redis-node-3 redis-node-4 redis-node-5 redis-node-6

# Initialize Redis cluster
docker-compose exec redis-node-1 redis-cli --cluster create \\
  redis-node-1:7001 redis-node-2:7002 redis-node-3:7003 \\
  redis-node-4:7004 redis-node-5:7005 redis-node-6:7006 \\
  --cluster-replicas 1 --cluster-yes
```

### 3. Deploy Application Services

```bash
# Build application images
docker-compose build app-1 app-2 app-3

# Start application instances
docker-compose up -d app-1 app-2 app-3

# Start load balancer
docker-compose up -d nginx-lb

# Start monitoring stack
docker-compose up -d prometheus grafana alertmanager
```

### 4. Deploy Monitoring and Observability

```bash
# Start all monitoring services
docker-compose up -d prometheus grafana alertmanager node-exporter

# Import Grafana dashboards (manual step)
# Access Grafana at http://your-domain:3000
# Default credentials: admin / <GRAFANA_ADMIN_PASSWORD>
```

## Post-Deployment Verification

### Health Checks

```bash
# Check application health
curl -f http://your-domain/actuator/health

# Check Elasticsearch cluster health
curl -u elastic:<password> http://your-domain:9200/_cluster/health

# Check Redis cluster status
docker-compose exec redis-node-1 redis-cli cluster info

# Check MySQL replication status
docker-compose exec mysql-primary mysql -u root -p<password> -e "SHOW MASTER STATUS\\G"
docker-compose exec mysql-replica-1 mysql -u root -p<password> -e "SHOW SLAVE STATUS\\G"
```

### Performance Verification

```bash
# Run load test (requires Apache Bench or similar)
ab -n 10000 -c 100 -H "Authorization: Bearer <JWT_TOKEN>" \\
   -H "Content-Type: application/json" \\
   http://your-domain/api/v1/search?query=test&tenantId=tenant1

# Monitor metrics
curl http://your-domain/actuator/prometheus
```

## Scaling Operations

### Horizontal Scaling

#### Add Application Instance
```bash
# Scale application to 5 instances
docker-compose up -d --scale app=5

# Update Nginx upstream configuration
# Reload Nginx configuration
docker-compose exec nginx-lb nginx -s reload
```

#### Add Elasticsearch Node
```bash
# Add new Elasticsearch node
docker-compose up -d elasticsearch-node-4

# The new node will automatically join the cluster
```

#### Scale Redis Cluster
```bash
# Add new Redis nodes
docker-compose up -d redis-node-7 redis-node-8

# Add nodes to cluster
docker-compose exec redis-node-1 redis-cli --cluster add-node \\
  redis-node-7:7007 redis-node-1:7001

# Rebalance cluster
docker-compose exec redis-node-1 redis-cli --cluster rebalance \\
  redis-node-1:7001 --cluster-use-empty-masters
```

### Vertical Scaling
```bash
# Update docker-compose.yml with increased resources
# Example: Change mem_limit from 8g to 16g
# Restart services
docker-compose up -d --force-recreate app-1 app-2 app-3
```

## Monitoring and Alerting

### Key Metrics to Monitor

#### Application Metrics
- Response time (95th percentile < 500ms)
- Throughput (requests per second)
- Error rate (< 1%)
- Active connections
- JVM memory usage
- GC pause times

#### Infrastructure Metrics
- CPU utilization (< 70%)
- Memory usage (< 80%)
- Disk I/O and space
- Network throughput
- Container health status

#### Database Metrics
- Query response time
- Connection pool usage
- Replication lag
- Lock contention
- Cache hit ratio

### Grafana Dashboards

Import the following dashboard templates:
1. **Application Performance Dashboard**
2. **Infrastructure Overview Dashboard**
3. **Database Performance Dashboard**
4. **Elasticsearch Cluster Dashboard**
5. **Redis Cluster Dashboard**

### Alerting Rules

Configure alerts for:
- High error rates (> 5%)
- Slow response times (> 1s average)
- High CPU/Memory usage (> 80%)
- Database replication lag (> 10s)
- Elasticsearch cluster yellow/red status
- Redis cluster node failures

## Backup and Recovery

### Database Backup
```bash
# Automated daily backup
docker-compose exec mysql-primary mysqldump \\
  -u root -p<password> --single-transaction \\
  --routines --triggers document_search > backup_$(date +%Y%m%d).sql

# Upload to secure storage
aws s3 cp backup_$(date +%Y%m%d).sql s3://your-backup-bucket/
```

### Elasticsearch Backup
```bash
# Create snapshot repository
curl -X PUT "elasticsearch-node-1:9200/_snapshot/backup_repo" \\
  -H 'Content-Type: application/json' -d '{
    "type": "fs",
    "settings": {
      "location": "/usr/share/elasticsearch/backup"
    }
  }'

# Create snapshot
curl -X PUT "elasticsearch-node-1:9200/_snapshot/backup_repo/<snapshot_name>"
```

### Application Configuration Backup
```bash
# Backup configuration files
tar -czf config_backup_$(date +%Y%m%d).tar.gz \\
  docker-compose.yml .env nginx/ mysql/ elasticsearch/ redis/

# Store securely
aws s3 cp config_backup_$(date +%Y%m%d).tar.gz s3://your-backup-bucket/config/
```

## Security Hardening

### Network Security
- Configure firewall rules to restrict access
- Use VPC/private networks for internal communication
- Enable TLS for all external connections
- Implement network segmentation

### Application Security
- Enable HTTPS with strong SSL certificates
- Configure JWT with short expiration times
- Implement rate limiting and DDoS protection
- Enable audit logging for security events
- Use secure headers (HSTS, CSP, etc.)

### Database Security
- Use strong passwords and rotate regularly
- Enable SSL for database connections
- Restrict database user permissions
- Enable audit logging
- Regular security updates

## Troubleshooting Guide

### Common Issues

#### High Response Times
1. Check application metrics for bottlenecks
2. Verify database query performance
3. Check Elasticsearch cluster health
4. Monitor cache hit rates
5. Review JVM garbage collection logs

#### Application Startup Failures
1. Check database connectivity
2. Verify Elasticsearch cluster is ready
3. Ensure Redis cluster is operational
4. Review application logs for errors
5. Check resource constraints

#### Database Connection Issues
1. Verify connection pool settings
2. Check database server status
3. Review network connectivity
4. Monitor connection limits
5. Check authentication credentials

### Log Analysis
```bash
# Application logs
docker-compose logs -f app-1 app-2 app-3

# Database logs
docker-compose logs -f mysql-primary mysql-replica-1

# Elasticsearch logs
docker-compose logs -f elasticsearch-node-1

# Nginx access logs
docker-compose logs -f nginx-lb
```

## Performance Tuning

### JVM Tuning
- Use G1GC for better latency
- Set appropriate heap sizes
- Enable string deduplication
- Configure GC logging

### Database Tuning
- Optimize query indexes
- Configure connection pool sizes
- Enable query cache
- Optimize InnoDB settings

### Elasticsearch Tuning
- Adjust heap size (50% of RAM)
- Configure index refresh intervals
- Optimize shard and replica counts
- Enable index-level caching

### Caching Strategy
- Implement multi-layer caching (L1 + L2)
- Set appropriate TTL values
- Monitor cache hit rates
- Implement cache warming strategies

## Maintenance Procedures

### Regular Maintenance Tasks
- Weekly: Review system metrics and alerts
- Monthly: Update security patches
- Quarterly: Performance testing and optimization
- Annually: Disaster recovery testing

### Update Procedures
1. Test updates in staging environment
2. Schedule maintenance window
3. Create system backup
4. Deploy updates using rolling deployment
5. Verify system functionality
6. Monitor for issues post-deployment

## Support and Escalation

### Support Contacts
- **Level 1**: Operations team (monitoring, basic troubleshooting)
- **Level 2**: Engineering team (application issues, performance)
- **Level 3**: Architecture team (design changes, major incidents)

### Escalation Procedures
1. **P1 (Critical)**: System down, data loss - Immediate escalation
2. **P2 (High)**: Performance degradation - 1 hour response
3. **P3 (Medium)**: Minor issues - 4 hour response
4. **P4 (Low)**: Enhancement requests - Next business day

---

For additional support, refer to:
- [Application Architecture Documentation](./ARCHITECTURE.md)
- [API Documentation](./API.md)
- [Security Guidelines](./SECURITY.md)
- [Development Guide](./DEVELOPMENT.md)