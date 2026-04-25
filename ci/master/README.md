# CircleGuard MASTER Pipeline

Pipeline de despliegue a producción con validaciones estrictas, release notes automáticas y rollback.

## 📋 Descripción

Este pipeline automatiza el despliegue a MASTER con:
1. Validación de rama (solo main/master)
2. Build y tests exhaustivos
3. Construcción y push de imágenes versionadas
4. Despliegue en STAGE para validación pre-producción
5. Tests de rendimiento con umbrales estrictos
6. Generación automática de release notes
7. Aprobación manual con vista de cambios
8. Despliegue a MASTER
9. Smoke tests con rollback automático

## 🔄 Flujo del Pipeline

```
Checkout + Validate Branch
    ↓
Build & Test
    ↓
Docker Build & Push (v${BUILD_NUMBER})
    ↓
Deploy STAGE & Validate
    ↓
Performance Tests (5 min, 50 usuarios, strict)
    ↓
Generate Release Notes
    ↓
⏸️  Manual Approval (mostrar release notes)
    ↓
Deploy MASTER
    ↓
Smoke Tests (con rollback automático)
    ↓
✅ Release v${BUILD_NUMBER} Completo
```

## 📊 Etapas Detalladas

### 1. Checkout + Validate Branch 🔄
- Clona repositorio desde rama `main`
- **Valida** que sea rama `main` o `master`
- Aborta si no cumple condición

### 2. Build & Test 🏗️
Compila y ejecuta tests de los 6 servicios **en paralelo**
```bash
./gradlew :services:circleguard-<nombre>:build
```
Incluye:
- Compilación completa
- Pruebas unitarias
- Validaciones de código

### 3. Docker Build & Push 🐳
Construye imágenes con **versionado semántico**:
```
circleguardacr.azurecr.io/circleguard-<service>:v${BUILD_NUMBER}
circleguardacr.azurecr.io/circleguard-<service>:latest
```

Push de ambos tags a ACR

### 4. Deploy STAGE & Validate 🚀
Despliegue y validación en namespace `stage` antes de MASTER:
- Deploy con imágenes versionadas
- Ejecución de tests de integración
- Ejecución de E2E tests
- Verificación de status de pods

### 5. Performance Tests ⚡
Ejecuta Locust por **5 minutos** con **50 usuarios**
```bash
locust -f locustfile.py \
  --headless \
  --users=50 \
  --spawn-rate=10 \
  --run-time=5m
```

**Umbrales ESTRICTOS**:
- Error rate: ≤ 2% (falla si > 2%)
- P95 response time: ≤ 1500ms (falla si > 1500ms)

### 6. Generate Release Notes 📝
Genera automáticamente `RELEASE_NOTES.md`:

**Estructura**:
```markdown
# Release v${BUILD_NUMBER} - ${FECHA}

## ✨ New Features
- [commit]: descripción

## 🐛 Bug Fixes
- [commit]: descripción

## 🔧 Maintenance
- [commit]: descripción

## 📦 Services Deployed
- circleguard-auth-service:v${BUILD_VERSION}
- ... (todos los servicios)

## 🔗 Links
- Build: ${BUILD_URL}
- Git Commit: ${COMMIT_SHA}
```

**También**:
- Crea git tag: `v${BUILD_NUMBER}`
- Push del tag al repositorio

### 7. Manual Approval 🚨
**Aprobación manual** mostrando release notes:
- Timeout: 2 horas
- Muestra contenido completo de `RELEASE_NOTES.md`
- Requiere confirmación de usuario autorizado
- Parámetro: `RELEASED_BY`

### 8. Deploy MASTER 🚀
Despliegue en namespace `master` con imágenes versionadas
- Aplica manifests en `k8s/master/`
- Actualiza imagen con tag `v${BUILD_NUMBER}`
- Verifica status de pods

### 9. Smoke Tests 🔥
Tests rápidos de salud de servicios:
```bash
curl http://localhost:${PORT}/actuator/health
```

**Comportamiento**:
- Si algún servicio falla: ❌ **Rollback automático**
- Rollback solo servicios fallidos
- Espera 30 segundos y verifica status

**Rollback automático**:
```bash
kubectl rollout undo deployment/<service> -n master
```

## 🔐 Credenciales Requeridas

| Credencial | Tipo | Uso |
|------------|------|-----|
| `ACR_CREDENTIALS` | Username + Password | Autenticación en Azure Container Registry |
| `KUBECONFIG` | Secret file | Acceso a cluster Kubernetes |

## 📦 Variables de Entorno

```groovy
ACR_REGISTRY = 'circleguardacr.azurecr.io'
STAGE_NAMESPACE = 'stage'
MASTER_NAMESPACE = 'master'
SPRING_PROFILES_ACTIVE = 'production'
BUILD_VERSION = "${BUILD_NUMBER}"
```

## ⏱️ Configuración

- **Timeout total**: 60 minutos
- **Log retention**: 10 últimas releases
- **Performance test duration**: 5 minutos
- **Performance users**: 50
- **Performance spawn rate**: 10 usuarios/segundo
- **Approval timeout**: 2 horas

## 📁 Estructura de Archivos Esperada

```
circle-guard-public/
├── services/
│   ├── circleguard-auth-service/Dockerfile
│   ├── circleguard-identity-service/Dockerfile
│   ├── circleguard-form-service/Dockerfile
│   ├── circleguard-gateway-service/Dockerfile
│   ├── circleguard-promotion-service/Dockerfile
│   └── circleguard-notification-service/Dockerfile
├── k8s/
│   ├── stage/
│   │   ├── circleguard-auth-service.yaml
│   │   ├── circleguard-identity-service.yaml
│   │   ├── circleguard-form-service.yaml
│   │   ├── circleguard-gateway-service.yaml
│   │   ├── circleguard-promotion-service.yaml
│   │   └── circleguard-notification-service.yaml
│   └── master/
│       ├── circleguard-auth-service.yaml
│       ├── circleguard-identity-service.yaml
│       ├── circleguard-form-service.yaml
│       ├── circleguard-gateway-service.yaml
│       ├── circleguard-promotion-service.yaml
│       └── circleguard-notification-service.yaml
├── tests/
│   └── performance/
│       ├── locustfile.py
│       └── results/
├── .git/
│   └── tags/ (v${BUILD_NUMBER})
└── ci/
    └── master/
        └── Jenkinsfile
```

## 📊 Reportes y Artifacts

### Release Notes
Ubicación: `RELEASE_NOTES.md`
Formato: Markdown
Contenido: Commits agrupados por tipo (feat, fix, chore)

### Performance Report
Se guarda en: `tests/performance/results/master-perf-report*.csv`

### Artifacts Archivados
- `RELEASE_NOTES.md`
- `master-perf-report_stats.csv`
- `master-perf-report_failures.csv`

## 📋 Validaciones

| Validación | Punto | Acción si Falla |
|------------|-------|-----------------|
| Rama es main/master | Inicio | Aborta |
| Build completo | Build stage | Aborta |
| Todos los tests pasan | Test stage | Aborta |
| Error rate < 2% | Performance | Aborta |
| P95 < 1500ms | Performance | Aborta |
| Aprobación manual | Approval | Aborta |
| Smoke tests pasan | Smoke Tests | Rollback automático |

## 🔄 Rollback

### Rollback Manual
```bash
kubectl rollout undo deployment/<service> -n master
```

### Rollback Automático (Smoke Tests)
Se ejecuta si algún servicio falla en `/actuator/health`
- Solo rollback servicios fallidos
- Sigue verificando status

## 🔍 Monitoreo

### Durante el pipeline
```bash
# Ver pods en STAGE
kubectl get pods -n stage -w

# Ver pods en MASTER
kubectl get pods -n master -w

# Ver logs
kubectl logs -f <pod-name> -n master
```

### Ver release notes generadas
```bash
# En Jenkins UI
http://jenkins-url/job/CircleGuard-Master/[BUILD_NUMBER]/artifact/RELEASE_NOTES.md

# O en workspace
cat RELEASE_NOTES.md
```

### Ver git tags
```bash
git tag -l "v*"
git show v${BUILD_NUMBER}
```

## ❌ Troubleshooting

### "Pipeline must run from main or master"
- Asegúrate de hacer push a rama `main` o `master`
- Verifica que el repositorio tenga esa rama

### "Performance test failed - Error rate > 2%"
- Esto es crítico para MASTER
- Revisa logs de servicios en STAGE
- Mejora estabilidad antes de relanzar

### "Smoke test failed - Automatic rollback initiated"
- El pipeline hizo rollback automáticamente
- Revisa qué servicio falló
- Investiga en logs de ese servicio

### "Release notes empty"
- Verifica que haya commits desde último tag
- Comprueba formato de commits (feat:, fix:, chore:)

## ✅ Post Actions

### Success ✅
- Release v${BUILD_NUMBER} deploying
- Notificación de éxito
- Artifacts archivados

### Failure ❌
- Rollback automático de servicios fallidos
- Notificación de error
- Artifacts archivados para debugging

### Always 🧹
- Limpia imágenes Docker
- Archiva todos los artifacts
- Registra versión deployed

## 🚀 Mejores Prácticas

1. **Siempre** hacer merge a través de PR
2. **Validar** release notes antes de aprobar
3. **Monitorear** después de desplegar
4. **Documentar** cambios importantes en commits
5. **Usar** Conventional Commits (feat:, fix:, chore:)

## 📝 Conventional Commits

Para que release notes incluyan tu cambio:

```bash
git commit -m "feat: nueva funcionalidad en auth service"
git commit -m "fix: resolver bug en promotion service"
git commit -m "chore: actualizar dependencias"
```

## 📞 Soporte

Si encuentras problemas:
1. Revisa logs del pipeline en Jenkins
2. Verifica credenciales en Jenkins
3. Valida que manifests existan en `k8s/master/`
4. Comprueba conectividad a Kubernetes

---

**Última actualización**: 2026-04-25
**Versión**: 1.0
**Status**: Production Release
