# Multi-stage Dockerfile for Spring Boot Backend on Render / Cloud
FROM gradle:8.14.1-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle :app:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
EXPOSE 8080
COPY --from=build /app/app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
