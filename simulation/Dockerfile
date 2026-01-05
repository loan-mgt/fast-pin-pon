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

COPY --from=build /workspace/target/simulation-*-SNAPSHOT.jar /app/app.jar
ENV API_BASE_URL=http://api:8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
