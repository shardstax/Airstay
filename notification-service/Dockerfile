# syntax=docker/dockerfile:1
# Build context is this folder (notification-service/). Same layout as the API's Dockerfile.

# ---- Build stage ------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependencies first, so they are cached until pom.xml changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- Runtime stage: small JRE image, non-root user ----------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S airstay && adduser -S airstay -G airstay
WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER airstay
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
