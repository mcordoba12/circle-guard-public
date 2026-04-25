# CircleGuard DEV Pipeline Jenkinsfiles

Esta carpeta contiene los Jenkinsfiles para el pipeline de CI/CD en el ambiente DEV de CircleGuard.

## 📁 Estructura de Archivos

```
ci/dev/
├── Jenkinsfile-auth-service              # Pipeline para Auth Service
├── Jenkinsfile-identity-service          # Pipeline para Identity Service
├── Jenkinsfile-form-service              # Pipeline para Form Service
├── Jenkinsfile-promotion-service         # Pipeline para Promotion Service
├── Jenkinsfile-notification-service      # Pipeline para Notification Service
├── Jenkinsfile-gateway-service           # Pipeline para Gateway Service
└── README.md                              # Este archivo
```

## 🚀 Servicios Incluidos

| Servicio | Puerto | Jenkinsfile |
|----------|--------|------------|
| Auth Service | 8180 | `Jenkinsfile-auth-service` |
| Identity Service | 8085 | `Jenkinsfile-identity-service` |
| Form Service | 8086 | `Jenkinsfile-form-service` |
| Gateway Service | 8087 | `Jenkinsfile-gateway-service` |
| Promotion Service | 8088 | `Jenkinsfile-promotion-service` |
| Notification Service | 8089 | `Jenkinsfile-notification-service` |

## 📋 Etapas del Pipeline

Cada Jenkinsfile contiene las siguientes etapas:

### 1. **Checkout** 🔄
- Clona el repositorio desde GitHub
- Branch: `main`
- URL configurable

### 2. **Build** 🏗️
```bash
./gradlew :services:circleguard-<nombre>-service:bootJar -x test
```
- Compila el servicio sin ejecutar tests
- Genera JAR ejecutable

### 3. **Unit Tests** 🧪
```bash
./gradlew :services:circleguard-<nombre>-service:test
```
- Ejecuta pruebas unitarias
- Publica resultados en formato JUnit XML
- Ruta: `**/build/test-results/test/*.xml`

### 4. **Docker Build** 🐳
```bash
docker build -f services/circleguard-<nombre>-service/Dockerfile -t <IMAGE_TAG> .
```
- Construye imagen Docker
- Tag: `circleguardacr.azurecr.io/circleguard-<nombre>-service:dev-${BUILD_NUMBER}`

### 5. **Docker Push** 📤
- Autentica con Azure Container Registry (ACR)
- Push de imagen a ACR
- Limpia imágenes antiguas de más de 24 horas
- Credencial: `ACR_CREDENTIALS`

### 6. **Deploy DEV** 🚀
- Aplica manifesto Kubernetes del servicio
- Namespace: `dev`
- Archivo: `k8s/dev/circleguard-<nombre>-service.yaml`
- Actualiza la imagen en el manifesto antes de aplicar
- Credencial: `KUBECONFIG`

### 7. **Health Check** 🏥
- Espera 30 segundos para que el pod se inicialice
- Verifica que el pod esté en estado `Running`
- Valida con: `kubectl get pods -l app=circleguard-<nombre>-service`
- Si falla, muestra detalles del pod con `kubectl describe`

## 🔧 Variables de Entorno

Cada pipeline define estas variables:

```groovy
ACR_REGISTRY = 'circleguardacr.azurecr.io'
SERVICE_NAME = 'circleguard-<nombre>-service'
NAMESPACE = 'dev'
SPRING_PROFILES_ACTIVE = 'dev'
IMAGE_TAG = "${ACR_REGISTRY}/${SERVICE_NAME}:dev-${BUILD_NUMBER}"
```

## 🔐 Credenciales Requeridas

Configura las siguientes credenciales en Jenkins:

### 1. **ACR_CREDENTIALS** (Tipo: Username with password)
- **Username:** Azure Service Principal (Client ID)
- **Password:** Azure Service Principal (Client Secret)
- **Uso:** Autenticación en Azure Container Registry

### 2. **KUBECONFIG** (Tipo: Secret file)
- **Contenido:** Archivo KUBECONFIG para acceso a Kubernetes
- **Uso:** Despliegue en cluster Kubernetes

## 📍 Configuración en Jenkins

### Crear un Pipeline Job

1. **New Item** → **Pipeline**
2. **Pipeline** → **Definition**: `Pipeline script from SCM`
3. **SCM**: Git
4. **Repository URL**: `https://github.com/your-org/circle-guard-public.git`
5. **Script Path**: `ci/dev/Jenkinsfile-<nombre>-service`

### Webhook Trigger (Opcional)

```groovy
triggers {
    githubPush()
}
```

## 📊 Notificaciones

Los pipelines incluyen post-actions para:

- ✅ **Success**: Notificación en consola
- ❌ **Failure**: Notificación de error
- 🧹 **Always**: Limpieza de imágenes Docker

**Nota:** Las notificaciones a Slack están comentadas. Descomenta y configura tu webhook:

```groovy
curl -X POST https://hooks.slack.com/services/YOUR/WEBHOOK/URL \
  -d '{"text": "Message here"}'
```

## 🔍 Troubleshooting

### "Manifest not found at k8s/dev/..."

- Verifica que exista el archivo en `k8s/dev/circleguard-<nombre>-service.yaml`
- Asegúrate de que la etiqueta `app` en el manifesto coincida con `SERVICE_NAME`

### "No pod found for circleguard-<nombre>-service"

- Verifica que los labels en el manifesto sean correctos
- Comprueba permisos de KUBECONFIG
- Revisa logs del deployment con:
  ```bash
  kubectl describe deployment circleguard-<nombre>-service -n dev
  ```

### "Docker login failed"

- Verifica credenciales en Jenkins
- Comprueba que el username sea el Client ID (con formato UUID)
- Valida que ACR exista: `circleguardacr.azurecr.io`

## 📈 Métricas y Logs

### Jenkins Console Output
- Cada etapa emite logs con emojis para fácil identificación
- Los logs de test se archivan automáticamente

### Kubernetes Logs
```bash
# Ver logs del pod
kubectl logs <pod-name> -n dev

# Ver logs del deployment
kubectl describe deployment circleguard-<nombre>-service -n dev
```

### Docker Registry
```bash
# Listar imágenes en ACR
az acr repository list --name circleguardacr

# Listar tags de una imagen
az acr repository show-tags --name circleguardacr \
  --repository circleguard-<nombre>-service
```

## ⏱️ Timeouts

- **Pipeline total**: 30 minutos (`timeout(time: 30, unit: 'MINUTES')`)
- **Health Check wait**: 30 segundos
- **Request timeout**: Configurado en los scripts

## 📝 Personalización

### Cambiar rama
En la etapa Checkout, modifica:
```groovy
branches: [[name: '*/main']]  // Cambiar 'main' por tu rama
```

### Cambiar URL del repositorio
```groovy
userRemoteConfigs: [[url: 'https://github.com/your-org/your-repo.git']]
```

### Agregar más pasos
Edita el Jenkinsfile correspondiente y agrega nuevos stages según necesites.

## 🔗 Referencias

- [Jenkins Pipeline Syntax](https://jenkins.io/doc/book/pipeline/syntax/)
- [Gradle Build Tool](https://gradle.org/)
- [Docker Build Documentation](https://docs.docker.com/engine/reference/commandline/build/)
- [Kubernetes kubectl](https://kubernetes.io/docs/reference/kubectl/)
- [Azure Container Registry](https://learn.microsoft.com/en-us/azure/container-registry/)

## 📞 Soporte

Para problemas específicos:
1. Revisa los logs del pipeline en Jenkins
2. Valida configuración de credenciales
3. Comprueba que los manifests de K8s existan
4. Verifica conectividad a ACR y Kubernetes

---

**Última actualización:** 2026-04-25
**Versión:** 1.0
**Estado:** Producción Ready
