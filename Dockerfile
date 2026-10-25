# One image per runnable module, built from the repository with Docker alone (docs/adr/0012):
#   docker build --build-arg MODULE=csms-app .
# The JDK and JRE images must stay on the same Java version.

FROM eclipse-temurin:25.0.4.1_1-jdk-noble@sha256:ba0f0e1a33864105e1d454e88105c617ab40f71062db1f306bf58be32a3f8e5e AS build
ARG MODULE
WORKDIR /workspace
COPY . .
# The cache mount keeps downloaded dependencies between builds without putting them in a layer.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --quiet ":${MODULE}:bootJar" \
    && java -Djarmode=tools -jar "${MODULE}/build/libs/${MODULE}.jar" \
        extract --layers --launcher --destination /extracted

FROM eclipse-temurin:25.0.4.1_1-jre-noble@sha256:3f2223fe04b750aa5a8179e29b5c9a1ddf7665fc9bb08bfe11ad01d143bc13a5
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
