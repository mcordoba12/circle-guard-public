# Taller CI/CD - CircleGuard

## 1. Descripción del Proyecto

### Propósito
CircleGuard es un sistema de trazabilidad de contactos implementado como una arquitectura de microservicios. Fue desarrollado para proporcionar una solución escalable y distribuida para el seguimiento y notificación de contactos en escenarios de salud pública.

### Stack Tecnológico
| Componente | Versión | Descripción |
|---|---|---|
| Java | 21 | Lenguaje de programación |
| Spring Boot | 3.2.4 | Framework para microservicios |
| Gradle | 8.x | Herramienta de construcción |
| PostgreSQL | 15 | Base de datos relacional principal |
| Kafka | 3.x | Message broker para eventos |
| Redis | 7.x | Cache y almacenamiento en memoria |
| Neo4j | 5.x | Base de datos de grafos |
| OpenLDAP | - | Servicio de directorio para autenticación |

### Microservicios

| Servicio | Puerto | Responsabilidad |
|---|---|---|
| circleguard-auth-service | 8081 | Autenticación JWT, validación LDAP |
| circleguard-identity-service | 8082 | Gestión de identidades y usuarios |
| circleguard-form-service | 8083 | Formularios de salud y encuestas |
| circleguard-gateway-service | 8080 | API Gateway, validación QR, caché Redis |
| circleguard-promotion-service | 8084 | Trazabilidad de contactos, grafos Neo4j, eventos Kafka |
| circleguard-notification-service | 8085 | Sistema de notificaciones |

---

## 2. Estrategia de Ramas Git

### Ramas Principales

| Rama | Propósito | Política |
|---|---|---|
| develop | Rama de integración continua | Cambios frecuentes, todos los features se integran aquí |
| master | Rama de producción | Solo versiones estables, requiere calidad garantizada |

### Flujo de Trabajo

```
feature/xyz → develop (PR)
         ↓
      Jenkins DEV Pipeline (6 jobs paralelos)
         ↓
    Si pasa: merge a develop
         ↓
      Jenkins STAGE Pipeline (auth-service, form-service)
         ↓
    develop → master (Pull Request)
         ↓
      Jenkins MASTER Pipeline (1 job, smoke tests)
         ↓
    Release a producción
```

### Pipelines por Rama

| Rama | Pipeline | Activación | Descripción |
|---|---|---|---|
| develop | DEV | Commit a develop | Construcción y deploy a namespace dev |
| develop | STAGE | Commit a develop | Construcción y deploy a namespace stage |
| master | MASTER | Commit a master | Construcción, deploy y release a namespace master |

---

## 3. Infraestructura

### Ambiente Local (Docker Compose)

El archivo `docker-compose.yml` en la raíz del proyecto levanta:

| Servicio | Imagen | Puerto | Descripción |
|---|---|---|---|
| PostgreSQL | postgres:15-alpine | 5432 | Base de datos relacional |
| Kafka | confluentinc/cp-kafka:7.x | 9092 | Message broker para eventos |
| Redis | redis:7-alpine | 6379 | Cache en memoria |
| Neo4j | neo4j:5 | 7687 (bolt) | Base de datos de grafos |
| OpenLDAP | osixia/openldap | 389 | Servicio LDAP para autenticación |
| Jenkins | jenkins/jenkins:latest | 8888 | Orquestador CI/CD |

**Iniciar infraestructura:**
```bash
docker-compose up -d
```

### Cluster Kubernetes (Azure AKS)

| Propiedad | Valor |
|---|---|
| Nombre del cluster | circleguard-aks |
| Proveedor | Microsoft Azure |
| Región | Canadá Central (canadacentral) |
| Nodos | 2 (escalable) |
| Runtime | Docker |

**Namespaces:**
- `dev` - Ambiente de desarrollo
- `stage` - Ambiente de staging/pruebas
- `master` - Ambiente de producción

**Conectar a AKS:**
```bash
az aks get-credentials --resource-group <resource-group> --name circleguard-aks --overwrite-existing
```

### Registro de Imágenes Docker

| Propiedad | Valor |
|---|---|
| Registry | Docker Hub |
| Usuario | angela912 |
| URL | docker.io/angela912 |

**Repositorios disponibles:**
- angela912/circleguard-auth-service
- angela912/circleguard-identity-service
- angela912/circleguard-form-service
- angela912/circleguard-gateway-service
- angela912/circleguard-promotion-service
- angela912/circleguard-notification-service

---

## 4. Pipelines CI/CD

### 4.1 Pipeline DEV (Rama develop)

**Estructura:** 6 jobs independientes, uno por microservicio

| Job | Descripción |
|---|---|
| DEV-auth-service | Construcción e deploy de auth-service |
| DEV-identity-service | Construcción e deploy de identity-service |
| DEV-form-service | Construcción e deploy de form-service |
| DEV-gateway-service | Construcción e deploy de gateway-service |
| DEV-promotion-service | Construcción e deploy de promotion-service |
| DEV-notification-service | Construcción e deploy de notification-service |

**Etapas de cada Job DEV:**
1. Checkout - Descarga código desde rama develop
2. Build - Compilación con Gradle
3. Unit Tests - Ejecución de pruebas unitarias (excluye E2E e integración)
4. Docker Build - Construcción de imagen Docker
5. Docker Push - Push a Docker Hub con tag `dev-N`
6. Deploy - Aplicación de manifests K8s al namespace dev
7. Health Check - Validación de pods en estado Running

**Trigger:** Commit a rama develop
**Resultado:** Imagen disponible en Docker Hub, servicio deployado en namespace dev

### 4.2 Pipeline STAGE (Rama develop)

**Estructura:** 1 job multi-etapa para validar antes de producción

| Job | Servicios |
|---|---|
| STAGE-Pipeline | auth-service, form-service |

**Etapas del Job STAGE:**
1. Checkout - Descarga código desde rama develop
2. Build - Compilación con Gradle
3. Unit Tests - Pruebas unitarias (excluye E2E e integración)
4. Docker Build & Push - Construcción y push con tag `stage-N` y `stage-latest`
5. Deploy Stage - Aplicación de manifests K8s al namespace stage
6. Health Check - Validación de probes (TCP socket, initialDelaySeconds: 180s)

**Trigger:** Commit a rama develop (después de pasos DEV)
**Resultado:** Imagen en Docker Hub con tag stage, servicios en namespace stage

### 4.3 Pipeline MASTER (Rama master)

**Estructura:** 1 job para release a producción

| Job |
|---|
| MASTER-Pipeline |

**Etapas del Job MASTER:**
1. Checkout - Descarga código desde rama master
2. Build - Compilación con Gradle
3. Unit Tests - Pruebas unitarias
4. Docker Build & Push - Construcción y push con tags `vN` y `latest`
5. Deploy Master - Aplicación de manifests K8s al namespace master
6. Release Notes - Generación de notas de release
7. Smoke Tests - Pruebas funcionales básicas (Locust)

**Trigger:** Commit a rama master
**Resultado:** Imagen de producción en Docker Hub, servicios en namespace master, release documentado

---

## 5. Pruebas

### Tipos de Pruebas Implementadas

| Tipo | Cantidad | Descripción |
|---|---|---|
| Unitarias | 27 métodos en 5 clases | Pruebas de lógica aislada |
| Integración | 5 clases | Pruebas con BD, servicios reales |
| E2E | 35 métodos en 5 clases | Pruebas de flujos completos |
| Rendimiento | 4 escenarios con Locust | Pruebas de carga y estrés |

### Ejecución de Pruebas

**Unitarias (DEV y STAGE):**
```bash
gradle unitTest
```

**Integración (ambiente local):**
```bash
gradle integrationTest
```

**E2E (ambiente local o STAGE):**
```bash
gradle e2eTest
```

**Rendimiento (Locust):**
```bash
locust -f load_tests/locustfile.py --host=http://localhost:8080
```

### Escenarios de Rendimiento (Locust)

| Escenario | Objetivo | Usuarios |
|---|---|---|
| 1. Login | Validar autenticación bajo carga | 50 |
| 2. QR Validation | Validar lectura de QR en gateway | 100 |
| 3. Contact Tracing | Prueba de trazabilidad completa | 75 |
| 4. Notification Burst | Ráfaga de notificaciones | 200 |

---

## 6. Imágenes Docker

### Nomenclatura de Tags

| Ambiente | Tag Pattern | Ejemplo |
|---|---|---|
| DEV | `dev-N` | `dev-1`, `dev-42` |
| STAGE | `stage-N`, `stage-latest` | `stage-15`, `stage-latest` |
| MASTER | `vN`, `latest` | `v1.0.0`, `latest` |

### Construcción Manual

```bash
# DEV
gradle build -x test
docker build -t angela912/circleguard-auth-service:dev-1 .
docker push angela912/circleguard-auth-service:dev-1

# STAGE
docker build -t angela912/circleguard-auth-service:stage-15 .
docker build -t angela912/circleguard-auth-service:stage-latest .
docker push angela912/circleguard-auth-service:stage-15
docker push angela912/circleguard-auth-service:stage-latest

# MASTER
docker build -t angela912/circleguard-auth-service:v1.0.0 .
docker build -t angela912/circleguard-auth-service:latest .
docker push angela912/circleguard-auth-service:v1.0.0
docker push angela912/circleguard-auth-service:latest
```

---

## 7. Kubernetes

### Estructura de Manifests

```
k8s/
├── services/
│   ├── dev/
│   │   ├── auth-service-deployment.yaml
│   │   ├── identity-service-deployment.yaml
│   │   ├── form-service-deployment.yaml
│   │   ├── gateway-service-deployment.yaml
│   │   ├── promotion-service-deployment.yaml
│   │   └── notification-service-deployment.yaml
│   ├── stage/
│   │   ├── auth-service-deployment.yaml
│   │   └── form-service-deployment.yaml
│   └── master/
│       └── all-services-deployment.yaml
└── infrastructure/
    ├── namespaces.yaml
    ├── configmaps.yaml
    └── secrets.yaml
```

### Configuración de Probes

**Health Checks implementados:**

| Tipo | Protocolo | Puerto | Delay Inicial | Intervalo |
|---|---|---|---|---|
| Liveness | TCP Socket | Service Port | 180s | 30s |
| Readiness | TCP Socket | Service Port | 120s | 10s |

**Rationale:** Spring Boot 3.2.4 requiere mayor tiempo de startup. TCP socket es más tolerante que httpGet ante servicios lentamente disponibles.

### Namespaces

| Namespace | Propósito | Réplicas |
|---|---|---|
| dev | Desarrollo e integración continua | 1 |
| stage | Staging y pruebas pre-producción | 1-2 |
| master | Producción | 2-3 |

---

## 8. Instrucciones de Ejecución

### 8.1 Ambiente Local

**Prerrequisitos:**
- Docker y Docker Compose instalados
- Git configurado
- Gradle 8.x o superior
- Java 21

**Paso 1: Clonar repositorio**
```bash
git clone https://github.com/mcordoba12/circle-guard-public.git
cd circle-guard-public
git checkout develop
```

**Paso 2: Levantar infraestructura**
```bash
docker-compose up -d
```

**Paso 3: Esperar a servicios iniciales (aprox. 60 segundos)**
```bash
docker-compose logs -f
# Presionar Ctrl+C cuando PostgreSQL, Kafka y Redis estén ready
```

**Paso 4: Construir servicios**
```bash
gradle clean build -x test
```

**Paso 5: Ejecutar pruebas**
```bash
gradle test
```

**Paso 6: Ejecutar servicios localmente**
```bash
# En terminal separada, por cada servicio:
gradle -p circleguard-auth-service bootRun
gradle -p circleguard-identity-service bootRun
# ... etc
```

**Paso 7: Validar deployments**
```bash
# Servicios disponibles en:
curl http://localhost:8080/health  # Gateway
curl http://localhost:8081/health  # Auth Service
curl http://localhost:8082/health  # Identity Service
```

### 8.2 Ejecución de Pipelines en Jenkins

**Acceder a Jenkins:**
```
http://localhost:8888
```

**Ejecutar Pipeline DEV:**
1. Hacer commit a rama develop
2. Jenkins detecta cambio automáticamente
3. 6 jobs se disparan en paralelo
4. Monitorear en "Build Queue"

**Ejecutar Pipeline STAGE:**
1. Pipeline DEV debe pasar completamente
2. STAGE Pipeline se dispara automáticamente
3. Construye y valida auth-service y form-service

**Ejecutar Pipeline MASTER:**
1. Hacer merge de develop a master
2. Jenkins detecta cambio en master
3. Pipeline ejecuta: Build → Tests → Docker → Deploy → Release → Smoke Tests

**Ver logs de ejecución:**
```
Jenkins UI → Job → Build #N → Console Output
```

### 8.3 Gestión de Cluster AKS

**Conectar a AKS:**
```bash
az login
az account set --subscription <subscription-id>
az aks get-credentials --resource-group <resource-group> --name circleguard-aks --overwrite-existing
```

**Verificar cluster:**
```bash
kubectl cluster-info
kubectl get nodes
```

**Ver deployments:**
```bash
# Ver namespace dev
kubectl get deployments -n dev

# Ver todos los namespaces
kubectl get deployments --all-namespaces
```

**Ver logs de pods:**
```bash
kubectl logs <pod-name> -n <namespace>
kubectl logs -f <pod-name> -n dev  # Live logs
```

**Escalar réplicas:**
```bash
kubectl scale deployment <service-name> --replicas=3 -n <namespace>
```

**Eliminar namespace completo (cuidado):**
```bash
kubectl delete namespace dev
```

---

## Resumen de Flujo Completo

```
Desarrollador               Jenkins              Docker Hub           Kubernetes
     |                         |                     |                    |
     |--commit develop---------→|                     |                    |
     |                         |                     |                    |
     |                    [DEV Pipeline]             |                    |
     |                    6 jobs paralelos           |                    |
     |                         |                     |                    |
     |                   [Build + Tests]             |                    |
     |                         |                     |                    |
     |                  [Docker Build/Push]----------→|                    |
     |                  tag: dev-N                   |                    |
     |                         |                     |                    |
     |                  [Deploy to dev]---------------------------→|      |
     |                         |                     |          k8s/dev   |
     |                         |                     |                    |
     |                   [STAGE Pipeline]            |                    |
     |                   (auth, form)                |                    |
     |                         |                     |                    |
     |                  [Docker Build/Push]----------→|                    |
     |                  tag: stage-N                 |                    |
     |                         |                     |                    |
     |                  [Deploy to stage]----------------------------→|   |
     |                         |                     |          k8s/stage |
     |                         |                     |                    |
     |--create PR master-------→|                     |                    |
     |                         |                     |                    |
     |                   [MASTER Pipeline]           |                    |
     |                         |                     |                    |
     |                  [Docker Build/Push]----------→|                    |
     |                  tag: vN, latest              |                    |
     |                         |                     |                    |
     |                  [Deploy to master]---------------------------→|   |
     |                  [Smoke Tests]                |          k8s/master|
     |                         |                     |                    |
     |←----success message-----←|                     |                    |
```

---

**Última actualización:** 2026-05-01
**Versión de documentación:** 1.0
**Autora:** Angela
