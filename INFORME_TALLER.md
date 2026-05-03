# Informe de Taller CI/CD - CircleGuard

**Proyecto:** CircleGuard - Sistema de Trazabilidad de Contactos
**Fecha de Reporte:** 2026-05-02
**Autor:** Angela
**Rama Base:** develop y master

---

## 1. Configuración de Pipelines

### 1.1 Jobs Configurados en Jenkins

| # | Nombre del Job | Rama | Script Path | Descripción |
|---|---|---|---|---|
| 1 | CircleGuard-DEV-auth-service | */develop | ci/dev/Jenkinsfile-auth-service | Pipeline DEV para auth-service |
| 2 | CircleGuard-DEV-form-service | */develop | ci/dev/Jenkinsfile-form-service | Pipeline DEV para form-service |
| 3 | CircleGuard-DEV-gateway-service | */develop | ci/dev/Jenkinsfile-gateway-service | Pipeline DEV para gateway-service |
| 4 | CircleGuard-DEV-identity-service | */develop | ci/dev/Jenkinsfile-identity-service | Pipeline DEV para identity-service |
| 5 | CircleGuard-DEV-notification-service | */develop | ci/dev/Jenkinsfile-notification-service | Pipeline DEV para notification-service |
| 6 | CircleGuard-DEV-promotion-service | */develop | ci/dev/Jenkinsfile-promotion-service | Pipeline DEV para promotion-service |
| 7 | CircleGuard-STAGE | */develop | ci/stage/Jenkinsfile | Pipeline STAGE (auth-service, form-service) |
| 8 | CircleGuard-MASTER | */master | ci/master/Jenkinsfile | Pipeline MASTER para producción |

### 1.2 Configuración General de Jobs

| Parámetro | Valor | Descripción |
|---|---|---|
| Definition | Pipeline script from SCM | Obtiene pipeline desde repositorio Git |
| SCM | Git | Control de versiones utilizado |
| Repository URL | https://github.com/mcordoba12/circle-guard-public.git | URL del repositorio |
| Credentials | None | Repositorio público, sin credenciales requeridas |
| Trigger | Polling SCM / Webhook | Detecta cambios en ramas configuradas |

### 1.3 Estructura de Etapas por Tipo de Pipeline

**Pipeline DEV (6 jobs paralelos):**
1. Checkout - Descarga código desde rama develop
2. Build - Compilación con Gradle
3. Unit Tests - Pruebas unitarias (excluye E2E e integración)
4. Docker Build - Construcción de imagen Docker
5. Docker Push - Push a Docker Hub con tag `dev-N`
6. Deploy DEV - Aplicación de manifests al namespace dev
7. Health Check - Validación de pods en estado Running
8. Post Actions - Limpieza y notificaciones

**Pipeline STAGE (1 job):**
1. Checkout SCM - Descarga código desde develop
2. Checkout - Validación adicional de rama
3. Build - Compilación de auth-service y form-service
4. Unit Tests - Pruebas unitarias de ambos servicios
5. Docker Build & Push - Construcción y push con tags stage-N y stage-latest
6. Deploy STAGE - Aplicación de manifests al namespace stage
7. Health Check - Validación de probes en namespace stage

**Pipeline MASTER (1 job):**
1. Checkout SCM - Descarga código desde master
2. Checkout + Validate Branch - Validación de rama master
3. Build - Compilación de servicios críticos
4. Unit Tests - Pruebas unitarias completas
5. Docker Build & Push - Construcción y push con tags vN y latest
6. Deploy MASTER - Aplicación de manifests al namespace master
7. Generate Release Notes - Generación de notas de release
8. Smoke Tests - Pruebas funcionales básicas con Locust
9. Post Actions - Notificaciones de release

---

## 2. Credenciales Configuradas

### 2.1 Credenciales en Jenkins

| Credencial ID | Tipo | Usuario/Nombre | Descripción | Usado En |
|---|---|---|---|---|
| DOCKER_HUB_CREDENTIALS | Username/Password | angela912 | Token de acceso Docker Hub para push de imágenes | Todos los 8 jobs (etapa Docker Push) |
| KUBECONFIG | File | kubeconfig-aks.yml | Archivo de configuración de Azure AKS | Todos los 8 jobs (etapa Deploy) |

### 2.2 Configuración de Credenciales

**DOCKER_HUB_CREDENTIALS:**
- Tipo: Username/Password Token
- Usuario: angela912
- Alcance: Global
- Usado para: Push de imágenes a Docker Hub
- Registros afectados:
  - angela912/circleguard-auth-service
  - angela912/circleguard-form-service
  - angela912/circleguard-gateway-service
  - angela912/circleguard-identity-service
  - angela912/circleguard-notification-service
  - angela912/circleguard-promotion-service

**KUBECONFIG:**
- Tipo: File Upload
- Cluster: circleguard-aks (Azure, canadacentral)
- Alcance: Global
- Usado para: Despliegue en Kubernetes (namespace dev, stage, master)
- Nodos: 2 (escalables)

---

## 3. Resultados de Ejecución de Pipelines

### 3.1 Pipeline DEV - Resultados Individuales

| Servicio | Build # | Estado | Duración | Imagen Publicada | Fecha |
|---|---|---|---|---|---|
| auth-service | 6 | SUCCESS | 3min 37s | dev-6 | 2026-04-25 |
| form-service | 2 | SUCCESS | 6min 9s | dev-2 | 2026-04-25 |
| gateway-service | 2 | SUCCESS | 5min 55s | dev-2 | 2026-04-25 |
| identity-service | 2 | SUCCESS | 11min | dev-2 | 2026-04-25 |
| notification-service | 3 | SUCCESS | 5min 20s | dev-3 | 2026-04-25 |
| promotion-service | 4 | SUCCESS | 3min 47s | dev-4 | 2026-04-25 |

**Duración total DEV (paralelo):** 11 minutos (el job más lento fue identity-service)
**Tasa de éxito:** 100%

### 3.2 Análisis de Tiempos - Pipeline DEV

| Etapa | Tiempo Promedio | Min | Max |
|---|---|---|---|
| Checkout | 2s | 1s | 3s |
| Build (Gradle) | 2-3 min | 90s | 180s |
| Unit Tests | 1-2 min | 45s | 150s |
| Docker Build | 1-2 min | 30s | 120s |
| Docker Push | 30-45s | 20s | 60s |
| Deploy DEV | 20-30s | 15s | 45s |
| Health Check | 10-15s | 5s | 20s |
| **Total Promedio** | **5-7 min** | - | - |

### 3.3 Pipeline STAGE

| Métrica | Valor | Descripción |
|---|---|---|
| Build # | 2 | Build number en Jenkins |
| Estado | SUCCESS | Ejecución exitosa |
| Duración Total | 11 minutos | Tiempo total de ejecución |
| Checkout SCM | 1min 15s | Descarga inicial del código |
| Checkout | 6s | Validación de rama |
| Build auth-service | 1min 46s | Compilación de auth-service |
| Build form-service | 52s | Compilación de form-service |
| Unit Tests auth-service | 1min 58s | Pruebas auth-service |
| Unit Tests form-service | 3min 8s | Pruebas form-service |
| Docker Build & Push | 1min 30s | Construcción y push de imágenes |
| Deploy STAGE | 45s | Despliegue en namespace stage |
| Health Check | 30s | Validación de probes |
| Mensaje Final | STAGE Pipeline completed successfully! | Estado final |

**Imágenes publicadas:**
- angela912/circleguard-auth-service:stage-2
- angela912/circleguard-auth-service:stage-latest
- angela912/circleguard-form-service:stage-2
- angela912/circleguard-form-service:stage-latest

### 3.4 Pipeline MASTER

| Métrica | Valor | Descripción |
|---|---|---|
| Build # | 4 | Build number en Jenkins |
| Estado | SUCCESS | Ejecución exitosa |
| Duración Total | 12 minutos | Tiempo total de ejecución |
| Checkout SCM | 1min 25s | Descarga inicial del código |
| Checkout + Validate Branch | 8s | Validación de rama master |
| Build auth-service | 1min 53s | Compilación de auth-service |
| Build form-service | 1min 11s | Compilación de form-service |
| Unit Tests auth-service | 2min 7s | Pruebas auth-service |
| Unit Tests form-service | 3min 26s | Pruebas form-service |
| Docker Build & Push | 1min 8s | Construcción y push de imágenes |
| Deploy MASTER | 21s | Despliegue en namespace master |
| Generate Release Notes | 15s | Generación de notas de release |
| Smoke Tests | 1min 30s | Ejecución de pruebas funcionales |
| Post Actions | 10s | Notificaciones y limpieza |
| Mensaje Final | Release v4 deployed to MASTER successfully! | Estado final |

**Imágenes publicadas (producción):**
- angela912/circleguard-auth-service:v4
- angela912/circleguard-auth-service:latest
- angela912/circleguard-form-service:v4
- angela912/circleguard-form-service:latest

**Release generado:** v4

---

## 4. Análisis de Pruebas de Rendimiento

### 4.1 Configuración de Pruebas Locust

| Parámetro | Valor | Descripción |
|---|---|---|
| Herramienta | Locust | Framework de pruebas de carga |
| Usuarios Virtuales | 100 | Número de usuarios concurrentes simulados |
| Spawn Rate | 10 usuarios/segundo | Velocidad de creación de usuarios |
| Duración | 5 minutos | Tiempo total de la prueba |
| Host | http://localhost:8180 | URL del API Gateway |
| Escenarios | 4 tipos de usuarios | CampusEntry, HealthReport, HealthStatus, Admin |

### 4.2 Distribución de Usuarios por Escenario

| Escenario | Porcentaje | Usuarios | Descripción |
|---|---|---|---|
| CampusEntryUser | 40% | 40 | Entrada a campus (QR, validación) |
| HealthStatusUser | 30% | 30 | Consultas de estado de salud |
| HealthReportUser | 20% | 20 | Reportes diarios de síntomas |
| AdminUser | 10% | 10 | Auditorías administrativas |

### 4.3 Métricas Generales de Rendimiento

| Métrica | Valor | Análisis |
|---|---|---|
| Total de Requests | 5,876 | Volumen de solicitudes completado |
| Requests Exitosos | 4,684 | 79.7% de solicitudes sin error |
| Requests Fallidos | 1,192 | 20.3% de solicitudes con error |
| Tiempo Mediano (p50) | 6 ms | Respuesta típica muy rápida |
| Tiempo Promedio | 33 ms | Promedio incluyendo solicitudes lentas |
| Tiempo Mínimo | 1.93 ms | Respuesta más rápida |
| Tiempo Máximo | 4,061 ms | Respuesta más lenta (login) |
| Throughput (req/s) | 32.7 | Rendimiento general del sistema |

### 4.4 Análisis por Percentil de Respuesta

| Percentil | Tiempo (ms) | Interpretación |
|---|---|---|
| p50 (Mediana) | 6 | 50% de requests responden en ≤6ms |
| p66 | 7 | 66% de requests responden en ≤7ms |
| p75 | 11 | 75% de requests responden en ≤11ms |
| p80 | 14 | 80% de requests responden en ≤14ms |
| p90 | 18 | 90% de requests responden en ≤18ms |
| p95 | 23 | 95% de requests responden en ≤23ms |
| p98 | 52 | 98% de requests responden en ≤52ms |
| p99 | 200 | 99% de requests responden en ≤200ms |

### 4.5 Resultados por Endpoint

#### Endpoints Exitosos (0% de fallos)

| Endpoint | Método | Requests | Fallos | Mediana (ms) | Promedio (ms) | p95 (ms) |
|---|---|---|---|---|---|---|
| /api/v1/auth/qr/generate | GET | 1,725 | 0 | 5 | 5.8 | 9 |
| /api/v1/gate/validate | POST | 1,725 | 0 | 4 | 5.96 | 9 |
| /api/v1/surveys | POST | 498 | 0 | 16 | 22.18 | 47 |
| /api/v1/circles/user/{id} | GET | 606 | 0 | 16 | 19.93 | 29 |
| /api/v1/health-status/stats | GET | 80 | 0 | 14 | 17.14 | 30 |
| /api/v1/auth/login | POST | 50 | 0 | 2,600 | 2,772.8 | 3,700 |

#### Endpoints con Fallos

| Endpoint | Método | Requests | Fallos | % Fallos | Status HTTP | Error |
|---|---|---|---|---|---|---|
| /api/v1/health/report | POST | 1,103 | 1,103 | 100% | 403 | Forbidden - Credencial o permiso insuficiente |
| /api/v1/admin/settings | GET | 89 | 89 | 100% | 500 | Internal Server Error - Error en servidor |

### 4.6 Análisis de Tiempos de Respuesta por Etapa

**Flujo de Campus Entry (más frecuente):**
1. Login (inicial): ~2,772 ms
2. QR Generate: ~5.8 ms
3. Gate Validate: ~5.96 ms
**Total promedio:** ~2,783 ms (primero), luego ~11 ms por iteración

**Flujo de Health Report:**
1. Survey Submit: ~22.18 ms
**Throughput:** Rápido y consistente

**Flujo de Health Status:**
1. Health Report: ERROR 403 (no medible)
2. Contact Circles Query: ~19.93 ms (cuando no hay error)

---

## 5. Análisis de Fallos de Rendimiento

### 5.1 Fallos Detectados

#### Error 1: POST /api/v1/health/report - HTTP 403 Forbidden

**Estadísticas:**
- Endpoint: POST /api/v1/health/report
- Errores: 1,103 (100% de solicitudes a este endpoint)
- Status HTTP: 403 Forbidden
- Escenario afectado: HealthStatusUser (30% de carga)

**Causa Probable:**
1. **Autorización insuficiente:** El usuario autenticado (staff_guard) no tiene permisos para reportar estado de salud
2. **Headers faltantes:** Posible ausencia de headers requeridos (ej: aplicación/versión)
3. **Validación de rol:** El servicio de promotion-service requiere rol específico no presente en el token JWT
4. **Configuración de base de datos:** Datos de seed incompletos en test users

**Recomendaciones:**
1. Verificar permisos de usuario staff_guard en promotion-service
2. Revisar roles y permisos en circleguard-identity-service
3. Actualizar script de seed SQL (V2__seed_test_users.sql)
4. Validar archivo de configuración application.yml de promotion-service
5. Agregar logs de autorización en promotion-service
6. Considerar usar usuario con permisos administrativos para pruebas

#### Error 2: GET /api/v1/admin/settings - HTTP 500 Internal Server Error

**Estadísticas:**
- Endpoint: GET /api/v1/admin/settings
- Errores: 89 (100% de solicitudes a este endpoint)
- Status HTTP: 500 Internal Server Error
- Escenario afectado: AdminUser (10% de carga)

**Causa Probable:**
1. **Excepción no manejada:** Error no capturado en el controlador AdminController
2. **Conexión a base de datos:** Fallo de conexión a PostgreSQL o Neo4j
3. **Serialización JSON:** Error al serializar objeto de configuración
4. **Recurso no inicializado:** Servicio de promotion-service no completamente iniciado
5. **Race condition:** Acceso a recurso no disponible durante startup

**Recomendaciones:**
1. Revisar logs de promotion-service en servidor (kubectl logs)
2. Aumentar initialDelaySeconds en liveness probe (actualmente 180s)
3. Validar conectividad a bases de datos (PostgreSQL, Neo4j)
4. Implementar manejo de excepciones en endpoint /admin/settings
5. Agregar validación de estado de servicio antes de retornar datos
6. Implementar health check más riguroso
7. Considerar endpoint /actuator/health para diagnóstico

### 5.2 Impacto en Puntaje General

**Cálculo de confiabilidad:**
- Requests exitosas: 4,684 / 5,876 = 79.7%
- SLA típico (99.9%) requiere: < 2.9 fallos por 5,876 requests
- Estado actual: CRÍTICO (1,192 fallos vs. objetivo 2-3)

**Severidad de fallos:**
| Endpoint | Severidad | Impacto |
|---|---|---|
| /api/v1/health/report | CRÍTICA | 30% de usuarios afectados |
| /api/v1/admin/settings | ALTA | Auditoría no funcional |

---

## 6. Conclusiones

### 6.1 Estado General del CI/CD

El sistema de Integración Continua y Despliegue de CircleGuard demuestra una arquitectura sólida con pipelines bien definidos:

**Fortalezas:**
1. **Automatización completa:** Los 8 jobs en Jenkins se ejecutan sin intervención manual
2. **Paralización efectiva:** Pipeline DEV ejecuta 6 servicios en paralelo reduciendo tiempo total
3. **Artefactos consistentes:** Todas las imágenes Docker se publican exitosamente en Docker Hub
4. **Despliegue seguro:** Credenciales bien organizadas y configuración de AKS integrada
5. **Rastreabilidad:** Sistema de versioning (dev-N, stage-N, vN) claro y profesional

**Debilidades identificadas:**
1. **Tiempo de startup prolongado:** Login toma ~2.8 segundos, indica Spring Boot lento en primer acceso
2. **Fallos críticos en pruebas de carga:** 20.3% de error rate inaceptable para producción
3. **Health checks insuficientes:** Pod se reporta como Ready pero endpoint falla

### 6.2 Estado de Pruebas de Rendimiento

Las pruebas de carga con Locust revelan problemas críticos que requieren atención inmediata:

**Problemas detectados:**
1. **Autenticación fallida:** POST /api/v1/health/report retorna 403 (1,103 errores)
2. **Error del servidor:** GET /api/v1/admin/settings retorna 500 (89 errores)
3. **Impacto de usuarios:** 30% + 10% = 40% de carga totalmente afectada

**Endpoints confiables:**
- Gate Entry Flow (QR generate + validate): Funciona perfectamente, ~5-6ms
- Survey Submission: 100% exitoso, ~22ms
- Contact Circle Queries: 100% exitoso, ~20ms
- Login: Lento pero funciona, ~2.8s

### 6.3 Recomendaciones Inmediatas

**Prioridad 1 (Crítica - Resolver en 24 horas):**
1. Investigar y resolver error 403 en /api/v1/health/report
   - Verificar permisos de usuario de prueba
   - Revisar configuración de autorización en promotion-service
   - Ejecutar tests en namespace stage antes de master

2. Investigar y resolver error 500 en /api/v1/admin/settings
   - Revisar logs del pod de promotion-service
   - Validar conectividad a bases de datos
   - Aumentar initialDelaySeconds a 240s si es necesario

**Prioridad 2 (Alta - Resolver en 1 semana):**
1. Optimizar tiempo de login (2.8s → <500ms)
   - Revisar configuración de LDAP/JWT
   - Considerar caching de tokens
   - Analizar query a base de datos de identidad

2. Implementar moniteo de salud más granular
   - Agregar health check por componente (DB, Cache, Message Queue)
   - Implementar readiness probe adicional

**Prioridad 3 (Media - Resolver en 2 semanas):**
1. Aumentar cobertura de pruebas E2E
   - Casos de error 403 y 500
   - Validación de flujos de admin

2. Implementar SLA monitoring
   - Dashboard Grafana para p95, p99
   - Alertas en Prometheus

### 6.4 Métricas de Éxito

Para considerar las pruebas y el CI/CD como exitosos:

| Métrica | Objetivo | Actual | Estado |
|---|---|---|---|
| Tasa de error (Locust) | < 1% | 20.3% | CRÍTICO |
| Tiempo promedio respuesta | < 50ms | 33ms | ACEPTABLE |
| p95 latencia | < 100ms | 23ms | EXCELENTE |
| Disponibilidad de endpoints | 100% | 66.7% | CRÍTICO |
| Duración pipeline DEV | < 15 min | 11 min | EXCELENTE |
| Duración pipeline MASTER | < 20 min | 12 min | EXCELENTE |

### 6.5 Conclusión Final

El sistema de CI/CD de CircleGuard está **correctamente configurado y funcional** en términos de automatización e infraestructura. Sin embargo, la **calidad del código en algunos servicios requiere atención inmediata**, particularmente en:
- promotion-service (/health/report, /admin/settings)
- Autenticación/Autorización

**Recomendación:** No desplegar a producción hasta resolver los errores 403 y 500. El pipeline técnicamente funciona, pero la funcionalidad del negocio está comprometida.

---

**Generado:** 2026-05-02
**Próxima revisión sugerida:** 2026-05-09
**Responsable de seguimiento:** Angela
