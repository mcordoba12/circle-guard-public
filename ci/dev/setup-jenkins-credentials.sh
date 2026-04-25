#!/bin/bash
#
# Script para configurar credenciales en Jenkins para CircleGuard DEV Pipeline
# Uso: ./setup-jenkins-credentials.sh
#
# Este script requiere:
# 1. Jenkins CLI instalado
# 2. Variables de entorno configuradas con credenciales de Azure
# 3. Acceso a Jenkins con permisos administrativos

set -e

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuración
JENKINS_URL="${JENKINS_URL:-http://localhost:8080}"
JENKINS_USER="${JENKINS_USER:-admin}"
JENKINS_TOKEN="${JENKINS_TOKEN:-}" # Debe ser proporcionado

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}CircleGuard Jenkins Credentials Setup${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# Validar JENKINS_TOKEN
if [ -z "$JENKINS_TOKEN" ]; then
    echo -e "${RED}❌ Error: JENKINS_TOKEN no está configurado${NC}"
    echo ""
    echo "Para obtener un token:"
    echo "1. Ve a: $JENKINS_URL/user/$JENKINS_USER/configure"
    echo "2. Click en 'Add new Token'"
    echo "3. Copia el token y establécelo en: export JENKINS_TOKEN='your-token'"
    exit 1
fi

echo -e "${YELLOW}ℹ️  Jenkins URL: $JENKINS_URL${NC}"
echo -e "${YELLOW}ℹ️  Jenkins User: $JENKINS_USER${NC}"
echo ""

# Función para crear credencial
create_credential() {
    local cred_id=$1
    local cred_type=$2
    local cred_description=$3

    echo -e "${BLUE}📝 Creando credencial: $cred_id${NC}"
    echo -e "${GRAY}   Tipo: $cred_type${NC}"
    echo ""
}

# ============================================================
# CREDENCIAL 1: ACR_CREDENTIALS
# ============================================================

echo -e "${BLUE}[1/2] Configurando ACR_CREDENTIALS${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

read -p "Ingresa Azure Service Principal Client ID: " ACR_USERNAME
read -sp "Ingresa Azure Service Principal Client Secret: " ACR_PASSWORD
echo ""

# Validar que no estén vacíos
if [ -z "$ACR_USERNAME" ] || [ -z "$ACR_PASSWORD" ]; then
    echo -e "${RED}❌ Error: Client ID o Client Secret vacío${NC}"
    exit 1
fi

# Crear XML para credencial de usuario/contraseña
ACR_CREDENTIAL_XML=$(cat <<EOF
<?xml version='1.1' encoding='UTF-8'?>
<com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl plugin="credentials@1283.v7a_5c0f6afc78">
  <id>ACR_CREDENTIALS</id>
  <description>Azure Container Registry Credentials for CircleGuard DEV</description>
  <scope>GLOBAL</scope>
  <username>${ACR_USERNAME}</username>
  <password>${ACR_PASSWORD}</password>
</com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>
EOF
)

echo -e "${YELLOW}⚠️  Asegúrate de tener Jenkins CLI instalado${NC}"
echo "    Descargar desde: $JENKINS_URL/cli"
echo ""
echo -e "${YELLOW}Comando para crear ACR_CREDENTIALS:${NC}"
echo ""
echo "java -jar jenkins-cli.jar -s $JENKINS_URL -auth $JENKINS_USER:$JENKINS_TOKEN \\"
echo "  create-credentials-by-xml system::system::jenkins '(global)' < acr-credentials.xml"
echo ""

# Guardar el XML en archivo
echo "$ACR_CREDENTIAL_XML" > acr-credentials.xml
echo -e "${GREEN}✅ Archivo 'acr-credentials.xml' creado${NC}"
echo ""

# ============================================================
# CREDENCIAL 2: KUBECONFIG
# ============================================================

echo -e "${BLUE}[2/2] Configurando KUBECONFIG${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

read -p "Ingresa ruta del archivo KUBECONFIG: " KUBECONFIG_PATH

# Validar que el archivo existe
if [ ! -f "$KUBECONFIG_PATH" ]; then
    echo -e "${RED}❌ Error: Archivo no encontrado: $KUBECONFIG_PATH${NC}"
    exit 1
fi

# Crear XML para credencial de archivo secreto
KUBECONFIG_CONTENT=$(cat "$KUBECONFIG_PATH" | base64)
KUBECONFIG_FILENAME=$(basename "$KUBECONFIG_PATH")

KUBECONFIG_CREDENTIAL_XML=$(cat <<EOF
<?xml version='1.1' encoding='UTF-8'?>
<org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl plugin="plain-credentials@143.v1b_df8ff669e7">
  <id>KUBECONFIG</id>
  <description>Kubernetes Config for CircleGuard DEV Cluster</description>
  <scope>GLOBAL</scope>
  <fileName>${KUBECONFIG_FILENAME}</fileName>
  <secretBytes>${KUBECONFIG_CONTENT}</secretBytes>
</org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl>
EOF
)

echo "$KUBECONFIG_CREDENTIAL_XML" > kubeconfig-credentials.xml
echo -e "${GREEN}✅ Archivo 'kubeconfig-credentials.xml' creado${NC}"
echo ""

echo -e "${YELLOW}Comando para crear KUBECONFIG:${NC}"
echo ""
echo "java -jar jenkins-cli.jar -s $JENKINS_URL -auth $JENKINS_USER:$JENKINS_TOKEN \\"
echo "  create-credentials-by-xml system::system::jenkins '(global)' < kubeconfig-credentials.xml"
echo ""

# ============================================================
# Instrucciones finales
# ============================================================

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}Próximos pasos:${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""
echo "1️⃣  Descargar Jenkins CLI:"
echo "    wget $JENKINS_URL/jnlpJars/jenkins-cli.jar"
echo ""
echo "2️⃣  Crear credencial ACR_CREDENTIALS:"
echo "    java -jar jenkins-cli.jar -s $JENKINS_URL -auth $JENKINS_USER:$JENKINS_TOKEN \\"
echo "      create-credentials-by-xml system::system::jenkins '(global)' < acr-credentials.xml"
echo ""
echo "3️⃣  Crear credencial KUBECONFIG:"
echo "    java -jar jenkins-cli.jar -s $JENKINS_URL -auth $JENKINS_USER:$JENKINS_TOKEN \\"
echo "      create-credentials-by-xml system::system::jenkins '(global)' < kubeconfig-credentials.xml"
echo ""
echo "4️⃣  Verificar credenciales en:"
echo "    $JENKINS_URL/credentials/"
echo ""
echo "5️⃣  Crear nuevos Pipeline jobs:"
echo "    - Job Name: CircleGuard-Auth-Service-DEV"
echo "    - Pipeline Script from SCM"
echo "    - Repository: https://github.com/your-org/circle-guard-public.git"
echo "    - Script Path: ci/dev/Jenkinsfile-auth-service"
echo ""

echo -e "${GREEN}✅ Configuración completada!${NC}"
echo ""
echo "Archivos creados:"
echo "  - acr-credentials.xml"
echo "  - kubeconfig-credentials.xml"
echo ""
echo -e "${YELLOW}Nota: Mantén estos archivos seguros o elimínalos después de crear las credenciales${NC}"
echo ""
