FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version

COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
