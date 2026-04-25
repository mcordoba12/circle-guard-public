# Runtime-only Dockerfile (assumes JAR is pre-built)
# Build with: ./gradlew bootJar -x test
# Then: docker build -f services/circleguard-auth-service/Dockerfile -t circleguard/circleguard-auth-service:latest .

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy pre-built JAR
COPY services/circleguard-auth-service/build/libs/circleguard-auth-service-1.0.0-SNAPSHOT.jar app.jar

# Set environment
ENV SPRING_PROFILES_ACTIVE=dev

# Expose port
EXPOSE 8180

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8180/actuator/health || exit 1

# Entrypoint
ENTRYPOINT ["java", "-jar", "app.jar"]
