# Build the executable JAR with the pinned Gradle/JDK toolchain.
FROM gradle:9.8.2-jdk25-noble AS build
USER root
WORKDIR /workspace
COPY . .
RUN chown -R gradle:gradle /workspace
USER gradle
RUN gradle --no-daemon bootJar

# Keep the Gradle/JDK build toolchain out of the runtime image.
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends bash curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home appuser
COPY --from=build /workspace/build/libs/nutrition-api.jar /app/app.jar
RUN chown appuser:appuser /app/app.jar
USER appuser
EXPOSE 8181
HEALTHCHECK --interval=10s --timeout=3s --start-period=25s --retries=10 \
    CMD curl --fail --silent http://localhost:8181/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
