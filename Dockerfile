# Multi-stage Dockerfile that does not depend on Maven Wrapper (.mvn)
# Build stage: use official Maven + Temurin JDK 17
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy POM and download dependencies first (leverages Docker layer cache)
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn -q -DskipTests package

# Run stage: slim JRE 17 image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy built JAR from build stage
COPY --from=build /app/target/*.jar /app/app.jar

# Create non-root user and set ownership
RUN useradd -r -u 10001 appuser && chown -R appuser:appuser /app
USER 10001

# Allow overriding JVM options and port at runtime (Render sets PORT)
ENV JAVA_OPTS=""
EXPOSE 8080

# Respect PORT env var if provided; default to 8080 locally
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} $JAVA_OPTS -jar /app/app.jar"]
