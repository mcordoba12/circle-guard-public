#!/bin/bash

# CircleGuard Docker Image Builder
# Construye y tagea las 6 imágenes de servicios de CircleGuard

set -e  # Exit on error

REGISTRY="circleguard"
SERVICES=(
    "circleguard-auth-service:8180"
    "circleguard-identity-service:8083"
    "circleguard-form-service:8086"
    "circleguard-promotion-service:8088"
    "circleguard-notification-service:8082"
    "circleguard-gateway-service:8087"
)

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "════════════════════════════════════════════════════════════════"
echo "CircleGuard Docker Image Builder"
echo "════════════════════════════════════════════════════════════════"
echo ""

# Verify Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running. Please start Docker and try again."
    exit 1
fi

echo "✅ Docker is running"
echo ""

# Build images
BUILD_COUNT=0
FAILED_SERVICES=()

for SERVICE_WITH_PORT in "${SERVICES[@]}"; do
    SERVICE_NAME="${SERVICE_WITH_PORT%:*}"
    SERVICE_PORT="${SERVICE_WITH_PORT#*:}"
    IMAGE_NAME="${REGISTRY}/${SERVICE_NAME}:latest"
    DOCKERFILE_PATH="${PROJECT_ROOT}/services/${SERVICE_NAME}/Dockerfile"

    echo "────────────────────────────────────────────────────────────────"
    echo "Building: ${IMAGE_NAME}"
    echo "Dockerfile: ${DOCKERFILE_PATH}"
    echo "Port: ${SERVICE_PORT}"
    echo ""

    # Check if Dockerfile exists
    if [ ! -f "${DOCKERFILE_PATH}" ]; then
        echo "❌ Dockerfile not found at ${DOCKERFILE_PATH}"
        FAILED_SERVICES+=("${SERVICE_NAME}")
        continue
    fi

    # Build image
    echo "🔨 Building Docker image..."
    if docker build \
        --file "${DOCKERFILE_PATH}" \
        --tag "${IMAGE_NAME}" \
        --build-arg SERVICE_NAME="${SERVICE_NAME}" \
        "${PROJECT_ROOT}"; then

        echo "✅ Successfully built: ${IMAGE_NAME}"
        BUILD_COUNT=$((BUILD_COUNT + 1))
    else
        echo "❌ Failed to build: ${IMAGE_NAME}"
        FAILED_SERVICES+=("${SERVICE_NAME}")
    fi

    echo ""
done

# Summary
echo "════════════════════════════════════════════════════════════════"
echo "Build Summary"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "✅ Successfully built: ${BUILD_COUNT}/${#SERVICES[@]} images"
echo ""

if [ ${#FAILED_SERVICES[@]} -gt 0 ]; then
    echo "❌ Failed services:"
    for FAILED_SERVICE in "${FAILED_SERVICES[@]}"; do
        echo "   - ${FAILED_SERVICE}"
    done
    exit 1
else
    echo "🎉 All images built successfully!"
    echo ""
    echo "Available images:"
    for SERVICE_WITH_PORT in "${SERVICES[@]}"; do
        SERVICE_NAME="${SERVICE_WITH_PORT%:*}"
        SERVICE_PORT="${SERVICE_WITH_PORT#*:}"
        echo "   - ${REGISTRY}/${SERVICE_NAME}:latest (port ${SERVICE_PORT})"
    done
    echo ""
    echo "To run containers:"
    echo "  docker run -p <host_port>:${SERVICE_PORT} ${REGISTRY}/<service-name>:latest"
    echo ""
fi
