FROM --platform=linux/amd64 eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM --platform=linux/amd64 eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
