# syntax=docker/dockerfile:1
# The backend: a Spring Boot application. Build:  docker build -t wtm-backend .

FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /build
# Dependencies first, so a change to the code does not download them again.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 10001 wtm
WORKDIR /app
COPY --from=build /build/target/wtm-*.jar /app/app.jar
USER wtm
EXPOSE 8080
# Everything else (database, storage, secrets, models) is given as environment variables; see docker-compose.app.yml.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
