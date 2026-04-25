# CircleGuard Kubernetes Manifests

Complete Kubernetes manifests for deploying CircleGuard microservices across dev, stage, and master environments.

## 📁 Directory Structure

```
k8s/
├── README.md                          This file
├── namespaces.yml                     Namespace, ServiceAccount, NetworkPolicy definitions
│
├── infra/                             Infrastructure (shared across all environments)
│   ├── configmap-infra.yml            Shared infrastructure configuration
│   ├── postgres.yml                   PostgreSQL StatefulSet (1 per namespace)
│   ├── redis.yml                      Redis Deployment (1 per namespace)
│   ├── kafka.yml                      Kafka + Zookeeper (1 broker per namespace)
│   └── neo4j.yml                      Neo4j Graph Database (1 per namespace)
│
└── services/
    ├── dev/                           Development Environment (1 replica)
    │   ├── auth-service.yml           Auth Service (LoadBalancer, port 8180)
    │   ├── identity-service.yml       Identity Service (ClusterIP, port 8083)
    │   ├── form-service.yml           Form Service (ClusterIP, port 8086)
    │   ├── promotion-service.yml      Promotion Service (ClusterIP, port 8088)
    │   ├── notification-service.yml   Notification Service (ClusterIP, port 8082)
    │   └── gateway-service.yml        Gateway Service (LoadBalancer, port 8087)
    │
    ├── stage/                         Staging Environment (2 replicas + HPA)
    │   └── all-services.yml           All 6 services consolidated
    │
    └── master/                        Production Environment (2 replicas + HPA)
        └── all-services.yml           All 6 services consolidated with pod anti-affinity
```

## 🚀 Deployment Guide

### Prerequisites

1. **Kubernetes cluster** running (AKS, EKS, GKE, etc.)
2. **kubectl** configured to access the cluster
3. **Azure Container Registry** (ACR) configured with credentials
4. **Persistent volumes** available in the cluster

### 0. Setup Namespace and Infrastructure

```bash
# Create namespaces and network policies
kubectl apply -f k8s/namespaces.yml

# Create ImagePullSecret (if using private ACR)
kubectl create secret docker-registry acr-credentials \
  --docker-server=circleguardacr.azurecr.io \
  --docker-username=<CLIENT_ID> \
  --docker-password=<CLIENT_SECRET> \
  -n dev

# Repeat for stage and master namespaces
kubectl create secret docker-registry acr-credentials \
  --docker-server=circleguardacr.azurecr.io \
  --docker-username=<CLIENT_ID> \
  --docker-password=<CLIENT_SECRET> \
  -n stage

kubectl create secret docker-registry acr-credentials \
  --docker-server=circleguardacr.azurecr.io \
  --docker-username=<CLIENT_ID> \
  --docker-password=<CLIENT_SECRET> \
  -n master
```

### 1. Deploy Infrastructure

```bash
# Deploy to DEV
kubectl apply -f k8s/infra/configmap-infra.yml -n dev
kubectl apply -f k8s/infra/postgres.yml -n dev
kubectl apply -f k8s/infra/redis.yml -n dev
kubectl apply -f k8s/infra/kafka.yml -n dev
kubectl apply -f k8s/infra/neo4j.yml -n dev

# Deploy to STAGE
kubectl apply -f k8s/infra/configmap-infra.yml -n stage
kubectl apply -f k8s/infra/postgres.yml -n stage
kubectl apply -f k8s/infra/redis.yml -n stage
kubectl apply -f k8s/infra/kafka.yml -n stage
kubectl apply -f k8s/infra/neo4j.yml -n stage

# Deploy to MASTER
kubectl apply -f k8s/infra/configmap-infra.yml -n master
kubectl apply -f k8s/infra/postgres.yml -n master
kubectl apply -f k8s/infra/redis.yml -n master
kubectl apply -f k8s/infra/kafka.yml -n master
kubectl apply -f k8s/infra/neo4j.yml -n master

# Wait for infrastructure to be ready
kubectl wait --for=condition=Ready pod -l app=postgres -n dev --timeout=300s
kubectl wait --for=condition=Ready pod -l app=redis -n dev --timeout=300s
kubectl wait --for=condition=Ready pod -l app=kafka-broker -n dev --timeout=300s
kubectl wait --for=condition=Ready pod -l app=neo4j -n dev --timeout=300s
```

### 2. Deploy Services

#### DEV Environment

```bash
# Deploy all 6 services
kubectl apply -f k8s/services/dev/auth-service.yml
kubectl apply -f k8s/services/dev/identity-service.yml
kubectl apply -f k8s/services/dev/form-service.yml
kubectl apply -f k8s/services/dev/promotion-service.yml
kubectl apply -f k8s/services/dev/notification-service.yml
kubectl apply -f k8s/services/dev/gateway-service.yml

# Verify deployment
kubectl get deployments -n dev
kubectl get pods -n dev
```

#### STAGE Environment

```bash
# Deploy all services (consolidated in one file)
kubectl apply -f k8s/services/stage/all-services.yml

# Verify deployment
kubectl get deployments -n stage
kubectl get pods -n stage
kubectl get hpa -n stage  # View autoscalers
```

#### MASTER Environment

```bash
# Deploy all services with prod configuration
kubectl apply -f k8s/services/master/all-services.yml

# Verify deployment
kubectl get deployments -n master
kubectl get pods -n master
kubectl get hpa -n master  # View autoscalers
```

### 3. Verify Deployments

```bash
# Check all pods are running
kubectl get pods -n dev
kubectl get pods -n stage
kubectl get pods -n master

# Check services and LoadBalancers
kubectl get svc -n dev      # Note external IPs for LoadBalancer services
kubectl get svc -n stage
kubectl get svc -n master

# View logs from a pod
kubectl logs -f <pod-name> -n dev

# Describe a pod for debugging
kubectl describe pod <pod-name> -n dev
```

## 📊 Environment Comparison

| Aspect | DEV | STAGE | MASTER |
|--------|-----|-------|--------|
| **Replicas** | 1 | 2 | 2 |
| **Image Tag** | `dev-latest` | `stage-latest` | `latest` |
| **HPA** | No | Yes (2-4) | Yes (2-6) |
| **Pod Anti-Affinity** | No | No | Yes |
| **Probes** | Readiness + Liveness | Readiness + Liveness | Readiness + Liveness |
| **Resources** | 256Mi/512Mi | 256Mi/512Mi | 512Mi/1Gi |
| **Logging Level** | DEBUG | INFO | WARN |

## 🔧 Configuration Details

### Ports by Service

| Service | Port | Type |
|---------|------|------|
| auth-service | 8180 | LoadBalancer |
| gateway-service | 8087 | LoadBalancer |
| identity-service | 8083 | ClusterIP |
| form-service | 8086 | ClusterIP |
| promotion-service | 8088 | ClusterIP |
| notification-service | 8082 | ClusterIP |

### Database Endpoints

- **PostgreSQL**: `postgres.{namespace}.svc.cluster.local:5432`
- **Redis**: `redis.{namespace}.svc.cluster.local:6379`
- **Kafka**: `kafka-broker.{namespace}.svc.cluster.local:9092`
- **Neo4j**: `neo4j.{namespace}.svc.cluster.local:7687`

### Environment Variables

All services receive:
- `SPRING_PROFILES_ACTIVE` - Environment profile (dev/stage/production)
- `SPRING_DATASOURCE_URL` - PostgreSQL connection
- `SPRING_DATASOURCE_USERNAME` - From Secret
- `SPRING_DATASOURCE_PASSWORD` - From Secret
- `JWT_SECRET` - From Secret
- `KAFKA_BOOTSTRAP_SERVERS` - Kafka broker address
- `REDIS_HOST` - Redis host
- `NEO4J_HOST` - Neo4j host

## 🏥 Health Checks

All services implement health checks:

- **Readiness Probe**: `GET /actuator/health` every 10s (initialDelay: 30s)
- **Liveness Probe**: `GET /actuator/health` every 30s (initialDelay: 60s)

Services are considered ready only after passing readiness probe.

## 📈 Autoscaling

### STAGE Environment

```yaml
HorizontalPodAutoscaler:
  min: 2 replicas
  max: 4 replicas
  CPU target: 70%
  Memory target: 80%
```

### MASTER Environment

```yaml
HorizontalPodAutoscaler:
  min: 2 replicas
  max: 6 replicas
  CPU target: 60%
  Memory target: 75%
  Pod Anti-Affinity: Preferred (spread across nodes)
```

## 🔐 Secrets Management

### Passwords (in Secrets)

```yaml
PostgreSQL Password:     postgres_{env}_password
Redis Password:          redis_{env}_password
Neo4j Password:          neo4j_{env}_password
JWT Secret:              {env}-jwt-secret-key-{service}
```

**IMPORTANT**: Replace with strong passwords in production!

### ConfigMaps

- Infrastructure config shared across all namespaces
- Per-service config for application-specific settings

## 🐛 Troubleshooting

### Pod Not Starting

```bash
# Check pod status
kubectl describe pod <pod-name> -n dev

# View logs
kubectl logs <pod-name> -n dev

# Check events
kubectl get events -n dev --sort-by='.lastTimestamp'
```

### Database Connection Issues

```bash
# Test PostgreSQL connectivity
kubectl run -it --rm debug --image=postgres:15 -- \
  psql -h postgres.dev.svc.cluster.local -U circleguard -d circleguard_dev

# Test Redis connectivity
kubectl run -it --rm debug --image=redis:7 -- \
  redis-cli -h redis.dev.svc.cluster.local ping

# Test Kafka connectivity
kubectl run -it --rm debug --image=confluentinc/cp-kafka -- \
  kafka-console-producer --bootstrap-server kafka-broker.dev.svc.cluster.local:9092 --topic test
```

### Service Not Accessible

```bash
# Check service endpoints
kubectl get endpoints -n dev

# Check service LoadBalancer IP
kubectl get svc -n dev

# Port forward for testing
kubectl port-forward svc/circleguard-auth-service 8180:8180 -n dev
# Now access: http://localhost:8180
```

## 📊 Monitoring

### View Resource Usage

```bash
# Check current resource usage
kubectl top nodes
kubectl top pods -n dev

# Check HPA status
kubectl get hpa -n stage
kubectl describe hpa circleguard-auth-service-hpa -n stage
```

### View Metrics

```bash
# Pod CPU and memory
kubectl get pods -n dev -o custom-columns=NAME:.metadata.name,CPU:.spec.containers[0].resources.requests.cpu,MEMORY:.spec.containers[0].resources.requests.memory

# Deployment replicas
kubectl get deployment -n dev -o wide
```

## 🔄 Updates and Rollbacks

### Update Image

```bash
# Update deployment with new image
kubectl set image deployment/circleguard-auth-service \
  auth-service=circleguardacr.azurecr.io/circleguard-auth-service:dev-v2 \
  -n dev

# Check rollout status
kubectl rollout status deployment/circleguard-auth-service -n dev

# Rollback if needed
kubectl rollout undo deployment/circleguard-auth-service -n dev
```

### Scale Deployment

```bash
# Manually scale (only works if HPA is not active)
kubectl scale deployment circleguard-auth-service --replicas=3 -n stage

# Disable HPA if needed
kubectl delete hpa circleguard-auth-service-hpa -n stage
```

## 📚 Related Documentation

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Spring Boot on Kubernetes](https://spring.io/guides/gs/spring-boot-docker/)
- [PostgreSQL in Kubernetes](https://www.postgresql.org/about/)
- [Kafka on Kubernetes](https://kafka.apache.org/)
- [Neo4j on Kubernetes](https://neo4j.com/docs/operations-manual/current/kubernetes/)

## 📝 Notes

- StatefulSets are used for databases (PostgreSQL) to maintain persistent state
- Deployments are used for stateless services
- PersistentVolumes are created automatically via StatefulSet volumeClaimTemplates
- Network Policies restrict traffic within namespaces
- All services use health checks for readiness and liveness verification
- HPA is not included in DEV to avoid unnecessary scaling
- Pod anti-affinity in MASTER helps distribute load across nodes

## 🚨 Important

1. **Backup Data**: Always backup PostgreSQL databases before major updates
2. **Secrets**: Replace default passwords with strong, secure values
3. **Resource Limits**: Adjust based on actual requirements
4. **Monitoring**: Implement observability (Prometheus, Grafana, ELK)
5. **Networking**: Implement Ingress for external access (optional)
6. **PVs**: Ensure your cluster has sufficient persistent volumes

---

**Last Updated**: 2026-04-25
**Version**: 1.0
**Status**: Production Ready
