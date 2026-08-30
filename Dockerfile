FROM node:22-alpine AS frontend-build
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend ./
RUN npm run build

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /src
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies
COPY src ./src
COPY --from=frontend-build /src/frontend/dist ./src/main/resources/static
RUN ./gradlew --no-daemon -x test bootJar

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /src/build/libs/finance-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
