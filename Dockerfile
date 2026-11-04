# One image per runnable module, built from the repository with Docker alone (docs/adr/0012):
#   docker build --build-arg MODULE=csms-app .
# The JDK and JRE images must stay on the same Java version.

FROM eclipse-temurin:25.0.4.1_1-jdk-noble@sha256:a9c5ddd386f629518bd500ecfabf7d1e5d32d7ffed735d5c3b1604ae92c830f4 AS build
ARG MODULE
WORKDIR /workspace
COPY . .
# The cache mount keeps downloaded dependencies between builds without putting them in a layer.
# Locked, because Compose builds several modules at once: Gradle processes in separate build
# containers cannot ask each other to release the cache, and time out waiting for its lock.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon --quiet ":${MODULE}:bootJar" \
    && java -Djarmode=tools -jar "${MODULE}/build/libs/${MODULE}.jar" \
        extract --layers --launcher --destination /extracted

FROM eclipse-temurin:25.0.4.1_1-jre-noble@sha256:ce8543ddd3070ccbeaac2106a74c9387ddeb8d47a1c43348345715c1ef245c7f
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
# Ordered from the least to the most often changed, so a code change rebuilds only the last layer.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER app
# The JVM reads the container memory limit; without a percentage it would use only a quarter.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
