FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY services/circleguard-gateway-service/build/libs/circleguard-gateway-service-1.0.0-SNAPSHOT.jar app.jar

ENV SPRING_PROFILES_ACTIVE=dev

EXPOSE 8087

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8087/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
