# 🚀 CircleGuard DEV Pipeline - Guía de Configuración

Esta guía te ayudará a configurar completamente el ambiente para ejecutar los pipelines CI/CD de CircleGuard en DEV.

## 📋 Checklist Pre-Requisitos

- [ ] Jenkins instalado y funcionando
- [ ] Jenkins CLI disponible
- [ ] Acceso administrativo a Jenkins
- [ ] Docker instalado en Jenkins agent
- [ ] kubectl instalado en Jenkins agent
- [ ] Acceso a Azure Container Registry (ACR)
- [ ] Acceso a cluster Kubernetes (DEV)
- [ ] Repositorio GitHub clonado

## 🔧 Paso 1: Preparar Jenkins Agent

### Verificar que Docker está disponible
```bash
docker --version
# Esperado: Docker version 20.x.x o superior
```

### Verificar que kubectl está disponible
```bash
kubectl version --client
# Esperado: Client Version v1.x.x
```

### Verificar que Gradle está disponible
```bash
which gradle
# O en el repo:
./gradlew --version
```

## 🔐 Paso 2: Configurar Credenciales en Jenkins

### 2.1 Obtener token de Jenkins

1. Ve a: `http://jenkins-url/user/tu-usuario/configure`
2. Scroll hasta "API Token"
3. Click en "Add new Token"
4. Copia el token generado

### 2.2 Preparar credenciales de Azure ACR

Necesitarás:
- **Client ID** (Service Principal ID)
- **Client Secret** (Service Principal Password)

Puedes obtenerlos con:
```bash
az ad sp create-for-rbac \
  --name CircleGuardCI \
  --role AcrPush \
  --scopes /subscriptions/{subscription}/resourceGroups/{resource-group}/providers/Microsoft.ContainerRegistry/registries/circleguardacr
```

Output incluirá:
```json
{
  "appId": "your-client-id",
  "password": "your-client-secret",
  "tenant": "your-tenant-id"
}
```

### 2.3 Preparar KUBECONFIG

```bash
# Obtener el kubeconfig del cluster DEV
az aks get-credentials \
  --resource-group circleguard-rg \
  --name circleguard-dev-aks

# Verificar que funciona
kubectl cluster-info
```

### 2.4 Ejecutar script de configuración

```bash
# Dar permisos de ejecución
chmod +x ci/dev/setup-jenkins-credentials.sh

# Ejecutar el script
export JENKINS_URL="http://jenkins-url"
export JENKINS_USER="tu-usuario"
export JENKINS_TOKEN="token-generado-en-2.1"

./ci/dev/setup-jenkins-credentials.sh
```

## 📁 Paso 3: Crear Manifests de Kubernetes

Crea los archivos de manifests para cada servicio:

```bash
# Crear directorio
mkdir -p k8s/dev

# Crear manifests basado en template
cp ci/dev/k8s-manifest-template.yaml k8s/dev/circleguard-auth-service.yaml
cp ci/dev/k8s-manifest-template.yaml k8s/dev/circleguard-identity-service.yaml
cp ci/dev/k8s-manifest-template.yaml k8s/dev/circleguard-form-service.yaml
cp ci/dev/k8s-manifest-template.yaml k8s/dev/circleguard-gateway-service.yaml
cp ci/dev/k8s-manifest-template.yaml k8s/dev/circleguard-promotion-service.yaml
cp ci/dev/k8s-manifest-template.yaml k8s/dev/circleguard-notification-service.yaml
```

**IMPORTANTE**: Edita cada archivo para:
1. Cambiar `circleguard-auth-service` por el nombre correcto del servicio
2. Actualizar variables de entorno específicas del servicio
3. Validar recursos (memoria, CPU) apropiados

## 🏗️ Paso 4: Crear Jobs en Jenkins

### Opción A: Manual

1. Click en **New Item**
2. Ingresa nombre: `CircleGuard-Auth-Service-DEV`
3. Selecciona **Pipeline**
4. Click **OK**
5. En configuración:
   - **Definition**: Pipeline script from SCM
   - **SCM**: Git
   - **Repository URL**: `https://github.com/your-org/circle-guard-public.git`
   - **Script Path**: `ci/dev/Jenkinsfile-auth-service`
6. Repetir para cada servicio (cambiar nombre del job y script path)

### Opción B: Jenkinsfile compartido

Crea un archivo `Jenkinsfile` en la raíz que use parámetros:

```groovy
@Library('shared-library') _
pipeline {
    parameters {
        choice(name: 'SERVICE', choices: [
            'auth-service',
            'identity-service',
            'form-service',
            'gateway-service',
            'promotion-service',
            'notification-service'
        ], description: 'Service to deploy')
    }

    stages {
        stage('Load Pipeline') {
            steps {
                load "ci/dev/Jenkinsfile-${params.SERVICE}"
            }
        }
    }
}
```

## 🧪 Paso 5: Validar Configuración

### 5.1 Validar Credentials

```bash
# En Jenkins UI, ir a: Manage Jenkins → Credentials → System
# Verificar que existan:
# - ACR_CREDENTIALS (Username/Password)
# - KUBECONFIG (Secret file)
```

### 5.2 Validar Dockerfiles

```bash
# Verificar que existan todos los Dockerfiles
ls -la services/circleguard-*/Dockerfile

# Output esperado:
# services/circleguard-auth-service/Dockerfile
# services/circleguard-identity-service/Dockerfile
# services/circleguard-form-service/Dockerfile
# services/circleguard-gateway-service/Dockerfile
# services/circleguard-promotion-service/Dockerfile
# services/circleguard-notification-service/Dockerfile
```

### 5.3 Validar Manifests de K8s

```bash
# Validar sintaxis YAML
for f in k8s/dev/*.yaml; do
  echo "Validando $f..."
  kubectl apply -f "$f" --dry-run=client -n dev
done
```

### 5.4 Validar Gradle

```bash
# Verificar que todos los servicios compilar
./gradlew :services:circleguard-auth-service:bootJar -x test
./gradlew :services:circleguard-identity-service:bootJar -x test
./gradlew :services:circleguard-form-service:bootJar -x test
./gradlew :services:circleguard-gateway-service:bootJar -x test
./gradlew :services:circleguard-promotion-service:bootJar -x test
./gradlew :services:circleguard-notification-service:bootJar -x test
```

## 🚀 Paso 6: Ejecutar Pipeline de Prueba

1. Ve al job: `CircleGuard-Auth-Service-DEV`
2. Click en **Build Now**
3. Monitorea los logs en **Console Output**
4. Esperado:
   - ✅ Checkout - 1-2 minutos
   - ✅ Build - 2-3 minutos
   - ✅ Unit Tests - 1-2 minutos
   - ✅ Docker Build - 1-2 minutos
   - ✅ Docker Push - 30-60 segundos
   - ✅ Deploy DEV - 30-60 segundos
   - ✅ Health Check - 30-60 segundos

## 📊 Monitoreo

### Ver logs del pipeline
```bash
# En Jenkins UI
http://jenkins-url/job/CircleGuard-Auth-Service-DEV/lastBuild/console
```

### Ver status del deployment
```bash
kubectl get deployment circleguard-auth-service -n dev
kubectl get pods -n dev -l app=circleguard-auth-service
kubectl logs -f <pod-name> -n dev
```

### Ver imágenes en ACR
```bash
az acr repository list --name circleguardacr
az acr repository show-tags --name circleguardacr --repository circleguard-auth-service
```

## 🔧 Troubleshooting

### Error: "docker: command not found"
**Solución**:
- Instalar Docker en Jenkins agent
- Agregar usuario jenkins al grupo docker: `usermod -aG docker jenkins`

### Error: "kubectl: command not found"
**Solución**:
- Instalar kubectl en Jenkins agent
- Verificar PATH en Jenkins agent

### Error: "Manifest not found"
**Solución**:
- Crear archivos en `k8s/dev/` con nombre exacto del servicio
- Validar YAML con: `kubectl apply -f <archivo> --dry-run=client`

### Error: "No pod found"
**Solución**:
- Verificar que el deployment se creó: `kubectl get deployment -n dev`
- Ver logs del deployment: `kubectl describe deployment <name> -n dev`
- Revisar eventos: `kubectl describe pod <pod-name> -n dev`

### Error: "Authentication failed (ACR)"
**Solución**:
- Verificar Client ID está correcto (no es UUID si usas username)
- Validar credenciales en Jenkins: `Manage Jenkins → Credentials`
- Probar manualmente: `docker login circleguardacr.azurecr.io`

### Error: "KUBECONFIG permission denied"
**Solución**:
- Verificar permisos del archivo KUBECONFIG
- En Jenkins, el archivo debe tener permisos de lectura

## 📋 Checklist de Validación Final

- [ ] Jenkins Agent tiene Docker instalado
- [ ] Jenkins Agent tiene kubectl instalado
- [ ] ACR_CREDENTIALS configurado en Jenkins
- [ ] KUBECONFIG configurado en Jenkins
- [ ] Manifests de K8s existen en `k8s/dev/`
- [ ] Dockerfiles existen para todos los servicios
- [ ] Gradle puede compilar todos los servicios
- [ ] Al menos un pipeline ejecutó exitosamente
- [ ] Pod está en estado `Running` después del deploy
- [ ] Logs muestran servicio iniciado correctamente

## 📞 Soporte

Si encuentras problemas:

1. **Revisa los logs** del pipeline en Jenkins
2. **Valida las credenciales** en Manage Jenkins → Credentials
3. **Prueba manualmente** los comandos que falla el pipeline
4. **Verifica conectividad** a ACR y Kubernetes

## 📚 Referencias Útiles

- [Jenkins Documentation](https://www.jenkins.io/doc/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Azure Container Registry](https://learn.microsoft.com/en-us/azure/container-registry/)
- [Azure Kubernetes Service](https://learn.microsoft.com/en-us/azure/aks/)
- [Gradle Build Tool](https://gradle.org/documentation/)

---

**Última actualización**: 2026-04-25
**Versión**: 1.0
**Status**: Production Ready
