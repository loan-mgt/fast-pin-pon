# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Create non-root user for runtime safety
RUN useradd -m appuser && chown -R appuser /app

COPY --from=build /workspace/target/simulation-*-SNAPSHOT.jar /app/app.jar
ENV API_BASE_URL=http://api:8080

# Ensure application files are owned by non-root user
RUN chown appuser:appuser /app/app.jar
USER appuser

ENTRYPOINT ["java","-jar","/app/app.jar"]
