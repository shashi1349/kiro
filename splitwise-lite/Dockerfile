# syntax=docker/dockerfile:1.7
# ---------- Stage 1: build the fat jar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies in their own layer
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

# Build the application
COPY src ./src
RUN mvn -B -q -DskipTests package \
 && mv target/*.jar /workspace/app.jar

# ---------- Stage 2: minimal runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user
RUN useradd -r -u 1001 -g root spring
USER 1001

COPY --from=build /workspace/app.jar /app/app.jar

# Most PaaS platforms inject PORT; default to 8080 locally.
ENV PORT=8080
EXPOSE 8080

# MaxRAMPercentage keeps the JVM friendly to small (512 MB) free-tier containers.
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]
