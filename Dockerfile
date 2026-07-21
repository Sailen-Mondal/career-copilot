# Multi-stage Dockerfile for Spring Boot Backend on Render / Cloud
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew :app:bootJar -x test --no-daemon -Dorg.gradle.jvmargs="-Xmx384m"

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
EXPOSE 8080
COPY --from=build /app/app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
