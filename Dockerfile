# 측정용 이미지: 빌드(JDK 21 + Gradle) -> 런타임(JRE 21)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home --uid 10001 app && mkdir -p /app/logs && chown -R app /app
COPY --from=build --chown=app /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
