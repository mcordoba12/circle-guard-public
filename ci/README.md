# CircleGuard CI/CD Pipeline Architecture

Documentación completa de los pipelines de CI/CD para CircleGuard.

## 🏗️ Arquitectura General

```
GitHub (main/master)
    ↓
    ├─→ DEV Pipeline (ci/dev/)
    │   ├─ Checkout
    │   ├─ Build individual
    │   ├─ Unit Tests
    │   ├─ Docker Build & Push
    │   ├─ Deploy DEV
    │   └─ Health Check
    │   → Namespace: dev
    │
    ├─→ STAGE Pipeline (ci/stage/)
    │   ├─ Checkout
    │   ├─ Build All (paralelo)
    │   ├─ Unit Tests
    │   ├─ Docker Build & Push
    │   ├─ Deploy STAGE
    │   ├─ Integration Tests
    │   ├─ E2E Tests
    │   ├─ Performance Tests
    │   └─ 🚨 Approval Gate
    │   → Namespace: stage
    │
    └─→ MASTER Pipeline (ci/master/)
        ├─ Validate Branch
        ├─ Build & Test
        ├─ Docker Build & Push
        ├─ Deploy STAGE & Validate
        ├─ Performance Tests (strict)
        ├─ Generate Release Notes
        ├─ 🚨 Manual Approval
        ├─ Deploy MASTER
        ├─ Smoke Tests + Rollback
        → Namespace: master
        → Git Tag: v${BUILD_NUMBER}
```

## 📁 Estructura de Carpetas

```
ci/
├── README.md                          ← Este archivo (overview)
│
├── dev/
│   ├── Jenkinsfile                    ← Pipeline DEV individual
│   ├── README.md                      ← Documentación DEV
│   ├── SETUP-GUIDE.md                 ← Guía de configuración
│   ├── setup-jenkins-credentials.sh   ← Script para credenciales
│   └── k8s-manifest-template.yaml     ← Template de manifests K8s
│
├── stage/
│   ├── Jenkinsfile                    ← Pipeline STAGE integrado
│   └── README.md                      ← Documentación STAGE
│
└── master/
    ├── Jenkinsfile                    ← Pipeline MASTER con release notes
    └── README.md                       ← Documentación MASTER
```

## 🎯 Comparativa de Pipelines

| Aspecto | DEV | STAGE | MASTER |
|---------|-----|-------|--------|
| **Trigger** | Manual por servicio | Manual o automático | Manual (solo rama main) |
| **Servicios** | 1 individualmente | 6 en paralelo | 6 en paralelo |
| **Tests** | Unit | Unit + Integration + E2E | Unit + Integration + E2E |
| **Performance** | No | 2 min, 30 users, err < 5% | 5 min, 50 users, err < 2%, P95 < 1500ms |
| **Release Notes** | No | No | Sí, automáticas |
| **Aprobación** | No | 1 hora | 2 horas |
| **Despliegue** | namespace dev | namespace stage | namespace master |
| **Tag Docker** | stage-${BUILD_NUMBER} | stage-${BUILD_NUMBER} | v${BUILD_NUMBER} |
| **Rollback** | Manual | Manual | Automático (smoke tests) |
| **Prod Ready** | No | Validación | Sí |

## 🔄 Flujo Completo

### Escenario: Nueva feature desde main branch

```
1. Developer pushes a main
   ↓
2. GitHub webhook trigger
   ↓
3. STAGE Pipeline inicia
   ├─ Build todos los servicios ✓
   ├─ Unit tests ✓
   ├─ Docker build & push ✓
   ├─ Deploy a STAGE ✓
   ├─ Integration + E2E tests ✓
   ├─ Performance tests (2 min) ✓
   ├─ Await approval (1 hora)
   │
4. Approver revisa y aprueba
   ↓
5. MASTER Pipeline inicia
   ├─ Validate branch es main ✓
   ├─ Build & test ✓
   ├─ Docker build & push (v${BUILD_NUMBER}) ✓
   ├─ Deploy STAGE para validación ✓
   ├─ Performance tests (5 min, strict) ✓
   ├─ Generate release notes ✓
   ├─ Await approval (2 horas)
   │
6. Release manager revisa release notes
   ↓
7. Aprueba despliegue a MASTER
   ├─ Deploy MASTER ✓
   ├─ Smoke tests ✓
   │
8. Release v${BUILD_NUMBER} completo ✅
```

## 🎯 Casos de Uso

### Caso 1: Bugfix en Auth Service
```
1. Developer abre PR desde feature branch
2. Merge a main después de aprobación
3. Main branch triggers STAGE pipeline
4. Si pasa todos los tests → Ready para MASTER
5. Release manager aprobaría en siguiente ventana de deployment
```

### Caso 2: Hotfix urgente
```
1. Hotfix branch desde main
2. Merge a main directamente
3. STAGE pipeline valida
4. MASTER pipeline puede ejecutarse inmediatamente
5. Rollback automático si smoke tests fallan
```

### Caso 3: Desarrollo local en DEV
```
1. Developer trabaja localmente
2. Push a feature branch
3. Ejecuta manualmente pipeline DEV para un servicio
4. Prueba cambios en namespace dev
5. Una vez estable, crea PR para main
```

## 🔐 Seguridad

### Credenciales

| Credencial | Ambiente | Permisos |
|-----------|----------|----------|
| ACR_CREDENTIALS | DEV + STAGE + MASTER | Push a ACR |
| KUBECONFIG | DEV + STAGE + MASTER | Read/Write a todos los namespaces |

**Nota**: Idealmente, usar credenciales diferentes por ambiente:
- ACR_CREDENTIALS_DEV (push solo a dev images)
- ACR_CREDENTIALS_STAGE (push a stage images)
- ACR_CREDENTIALS_MASTER (push a latest + version tags)

### Aprobaciones

| Pipeline | Aprobación | Timeout |
|----------|-----------|---------|
| DEV | Ninguna (manual trigger) | N/A |
| STAGE | Approval Gate | 1 hora |
| MASTER | Manual Approval (muestra release notes) | 2 horas |

## 📊 Métricas y Umbrales

### Performance Tests

#### STAGE
- **Duración**: 2 minutos
- **Usuarios**: 30
- **Error rate threshold**: 5% (falla si > 5%)
- **Propósito**: Validación rápida

#### MASTER
- **Duración**: 5 minutos
- **Usuarios**: 50
- **Error rate threshold**: 2% (falla si > 2%)
- **P95 threshold**: 1500ms (falla si > 1500ms)
- **Propósito**: Validación exhaustiva pre-producción

## 🔧 Configuración Inicial

### 1. DEV Pipeline
```bash
# Leer guía
cat ci/dev/SETUP-GUIDE.md

# Configurar credenciales
./ci/dev/setup-jenkins-credentials.sh

# Crear manifests
cp ci/dev/k8s-manifest-template.yaml k8s/dev/circleguard-auth-service.yaml
# ... repetir para otros servicios
```

### 2. STAGE Pipeline
- Usar mismas credenciales que DEV
- Crear manifests en `k8s/stage/`
- Configurar webhook en GitHub

### 3. MASTER Pipeline
- Usar mismas credenciales
- Crear manifests en `k8s/master/`
- Configurar rama protection en GitHub (require checks)

## 🚀 Deployment Windows

**Recomendado**:
- DEV: 24/7 (testing local)
- STAGE: Business hours + nightly validation
- MASTER: Scheduled windows (e.g., Martes y Jueves 2pm UTC)

## 📈 Monitoreo

### Jenkins Dashboard
```
http://jenkins-url/
├── CircleGuard-Dev-* (6 jobs, uno por servicio)
├── CircleGuard-Stage (1 job)
└── CircleGuard-Master (1 job)
```

### Health Checks
```bash
# Ver pods por ambiente
kubectl get pods -n dev -o wide
kubectl get pods -n stage -o wide
kubectl get pods -n master -o wide

# Ver history de deployments
kubectl rollout history deployment/<name> -n <namespace>
```

### Logs
```bash
# Jenkins console output
http://jenkins-url/job/CircleGuard-Stage/[BUILD_NUMBER]/console

# Pod logs
kubectl logs -f <pod-name> -n <namespace>

# Deployment events
kubectl describe deployment <name> -n <namespace>
```

## 🔄 Gitflow Integration

Recomendamos seguir **Git Flow**:

```
main ← Production-ready (MASTER pipeline)
 ↑
staging ← Testing (STAGE pipeline)
 ↑
develop ← Integration (future)
 ↑
feature/* ← Development
```

**Actual simplificado**:
```
main ← Triggers STAGE → (approval) → MASTER
 ↑
feature/* ← Development (local or DEV pipeline)
```

## 📋 Checklist Deployment

### Antes de STAGE
- [ ] PR aprobado por al menos 2 reviewers
- [ ] Todos los tests locales pasan
- [ ] Commit message sigue Conventional Commits

### Antes de MASTER
- [ ] STAGE pipeline completado exitosamente
- [ ] Performance tests dentro de umbrales
- [ ] Release notes están correctas
- [ ] No hay issues críticos abiertos

## 🆘 Troubleshooting Rápido

| Error | Causa | Solución |
|-------|-------|----------|
| "Branch validation failed" | No es main/master | Merge a main primero |
| "Docker push failed" | Credenciales incorrectas | Validar ACR_CREDENTIALS |
| "Smoke test failed - rollback" | Servicio unhealthy | Ver logs del pod |
| "Performance test failed" | Carga mayor a umbral | Investigar latencia |
| "No pod found" | Deployment incompleto | Esperar 60 segundos |

## 📞 Contacto y Soporte

Para problemas específicos:
1. **DEV Pipeline**: Ver `ci/dev/SETUP-GUIDE.md`
2. **STAGE Pipeline**: Ver `ci/stage/README.md`
3. **MASTER Pipeline**: Ver `ci/master/README.md`

## 🔗 Referencias Útiles

- [Jenkins Documentation](https://www.jenkins.io/doc/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Azure Container Registry](https://docs.microsoft.com/en-us/azure/container-registry/)
- [Locust Documentation](https://docs.locust.io/)

## 📝 Notas Finales

- Todos los Jenkinsfiles usan **declarative syntax**
- Los pipelines están **idempotentes** (safe to rerun)
- Rollback es **automático** en MASTER si smoke tests fallan
- Release notes son **generadas automáticamente** desde commits
- Cada pipeline tiene **timeout** para evitar ejecuciones infinitas

---

**Última actualización**: 2026-04-25
**Versión**: 2.0 (DEV + STAGE + MASTER)
**Status**: Production Ready
