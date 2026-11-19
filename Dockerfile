# syntax=docker/dockerfile:1

# ---- Build stage: compile and package with Maven (no JDK needed on the host) ----------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependencies first, so they are cached until pom.xml changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- Runtime stage: small JRE image, non-root user ------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S airstay && adduser -S airstay -G airstay
WORKDIR /app

# Copied from least to most frequently changing, so code-only changes rebuild just the last layer.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
COPY scripts/seed-demo-catalog.sql scripts/attach-demo-photos.sql scripts/demo-photos.csv /app/demo/
COPY scripts/demo-assets /app/demo/demo-assets/

USER airstay
EXPOSE 8080
# Size the heap from the container memory limit and restart cleanly instead of limping after an OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
