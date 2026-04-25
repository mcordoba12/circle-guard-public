# CircleGuard STAGE Pipeline

Pipeline de CI/CD para validación integral antes de despliegue a MASTER.

## 📋 Descripción

Este pipeline automatiza:
1. Build de 6 servicios en paralelo
2. Pruebas unitarias
3. Construcción y push de imágenes Docker
4. Despliegue en namespace STAGE
5. Tests de integración y E2E
6. Tests de rendimiento
7. Aprobación manual para promoción a MASTER

## 🔄 Flujo del Pipeline

```
Checkout
    ↓
Build All Services (paralelo)
    ↓
Unit Tests
    ↓
Docker Build & Push
    ↓
Deploy STAGE
    ↓
Integration Tests
    ↓
E2E Tests
    ↓
Performance Tests
    ↓
⏸️  Approval Gate (espera aprobación)
    ↓
✅ Ready for MASTER
```

## 📊 Etapas Detalladas

### 1. Checkout 🔄
- Clona repositorio desde rama `main`
- URL: `https://github.com/your-org/circle-guard-public.git`

### 2. Build All Services 🏗️
Compila los 6 servicios **en paralelo**:
- circleguard-auth-service
- circleguard-identity-service
- circleguard-form-service
- circleguard-gateway-service
- circleguard-promotion-service
- circleguard-notification-service

Comando: `./gradlew :services:circleguard-<nombre>:bootJar -x test`

### 3. Unit Tests 🧪
Ejecuta pruebas unitarias de todos los servicios **en paralelo**
Publica resultados en formato JUnit XML

### 4. Docker Build & Push 🐳
Construye y envía imágenes a Azure Container Registry con tag:
```
circleguardacr.azurecr.io/circleguard-<service>:stage-${BUILD_NUMBER}
```

### 5. Deploy STAGE 🚀
Aplica manifests Kubernetes en namespace `stage`
- Actualiza imagen en cada manifest
- Verifica status de pods

### 6. Integration Tests 🔗
Pruebas de integración para servicios que las tienen:
- circleguard-auth-service
- circleguard-form-service
- circleguard-gateway-service
- circleguard-promotion-service

### 7. E2E Tests 🧬
Ejecuta todas las pruebas E2E del proyecto
Busca tests con clase `*E2E*`

### 8. Performance Tests ⚡
Ejecuta Locust por **2 minutos** con **30 usuarios**
```bash
locust -f locustfile.py \
  --headless \
  --users=30 \
  --spawn-rate=5 \
  --run-time=2m
```

**Umbral**: Error rate ≤ 5% (falla si > 5%)

### 9. Approval Gate 🚨
**Input manual** solicitando aprobación para promoción a MASTER
- Timeout: 1 hora
- Parámetro: `APPROVED_BY`

## 🔐 Credenciales Requeridas

| Credencial | Tipo | Uso |
|------------|------|-----|
| `ACR_CREDENTIALS` | Username + Password | Autenticación en Azure Container Registry |
| `KUBECONFIG` | Secret file | Acceso a cluster Kubernetes |

## 📦 Variables de Entorno

```groovy
ACR_REGISTRY = 'circleguardacr.azurecr.io'
NAMESPACE = 'stage'
SPRING_PROFILES_ACTIVE = 'stage'
SERVICES = 'auth-service,identity-service,form-service,gateway-service,promotion-service,notification-service'
```

## ⏱️ Configuración

- **Timeout total**: 60 minutos
- **Log retention**: 20 últimas builds
- **Health check wait**: 30 segundos
- **Performance test duration**: 2 minutos
- **Performance users**: 30
- **Performance spawn rate**: 5 usuarios/segundo

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
│   └── stage/
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
└── ci/
    └── stage/
        └── Jenkinsfile
```

## 📊 Reportes

### Performance Report
Se guarda en: `tests/performance/results/stage-perf-report*.csv`
- `stage-perf-report_stats.csv` - Estadísticas generales
- `stage-perf-report_failures.csv` - Errores

### Test Results
Se publican en Jenkins: **JUnit Report**

## 🔍 Monitoreo durante ejecución

```bash
# Ver pods en STAGE
kubectl get pods -n stage -w

# Ver logs de un servicio
kubectl logs -f <pod-name> -n stage

# Ver eventos
kubectl describe pod <pod-name> -n stage
```

## ❌ Troubleshooting

### "Manifest not found"
- Verifica que existan archivos en `k8s/stage/`
- Compara nombres exactos con los servicios

### "Performance test failed - Error rate > 5%"
- Revisa logs del servicio en STAGE
- Verifica que todos los pods estén Running
- Intenta ejecutar tests nuevamente

### "No pod found"
- Espera a que el deployment se complete
- Revisa: `kubectl describe deployment <name> -n stage`

### "Docker push failed"
- Verifica credenciales de ACR
- Comprueba que el image name sea correcto
- Valida permisos en ACR

## ✅ Post Actions

### Success ✅
- Notificación de éxito
- Aguarda aprobación para MASTER

### Failure ❌
- Rollback automático de STAGE
- Archiva reportes de error

### Always 🧹
- Limpia imágenes Docker > 24 horas
- Archiva reports de performance

## 🚀 Próximos Pasos Post-Aprobación

Una vez aprobado en STAGE:
1. Jenkins activará pipeline MASTER
2. Se aplicarán tests más estrictos
3. Se generarán release notes
4. Se requerirá aprobación nuevamente
5. Se desplegará a MASTER

## 📝 Notas

- No modifiques manifests de Kubernetes manualmente
- Los tests se ejecutan con timeout de 60 minutos
- El error rate se calcula desde estadísticas de Locust
- Aprobación requiere confirmación manual por un usuario autorizado

---

**Última actualización**: 2026-04-25
**Versión**: 1.0
