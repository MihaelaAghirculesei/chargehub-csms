# 0012. Build container images with one multi-stage Dockerfile

- Status: Accepted
- Date: 2026-10-25

## Context

The central system and the simulator have to run as containers: the Phase 2 demo starts them
together with Docker Compose, and in Phase 10 someone who has just cloned the repository should
see the whole system work with one command. That person may have Docker and nothing else, not
even a JDK.

So we have to decide how a Spring Boot module becomes an image.

## Considered options

1. **A multi-stage Dockerfile that builds the jar inside Docker.** The first stage runs Gradle
   on a JDK image, the second keeps only a JRE and the application. Chosen.
2. **Cloud Native Buildpacks** (`./gradlew bootBuildImage`). No Dockerfile to maintain, but it
   needs a JDK on the machine to run Gradle, it downloads a builder image of about a gigabyte,
   and what ends up in the image is decided by the builder, not visible in the repository.
3. **Jib.** Fast and without a Docker daemon, but it is one more Gradle plugin, it also needs a
   JDK on the machine, and the image cannot be rebuilt with a plain `docker build`.
4. **A Dockerfile that copies a jar built on the host.** Simple, but the image then depends on
   whatever the host built, and it cannot be built without a JDK.

## Decision

- One `Dockerfile` at the root builds any runnable module, chosen with the `MODULE` build
  argument: `docker build --build-arg MODULE=csms-app .`
- The build stage runs `bootJar` on `eclipse-temurin` JDK. Gradle's downloads are kept in a
  BuildKit cache mount, so they are reused between builds without ending up in a layer.
- The runtime stage is `eclipse-temurin` JRE. The jar is extracted into Spring Boot layers,
  copied from the least to the most often changed, and run by a non-root user.
- Both base images are pinned to an exact version and digest, and Dependabot updates them
  together. A new Java major is planned by hand: only LTS versions are used.
- `.dockerignore` is an allow list: only the Gradle build and the modules reach Docker, so
  secrets such as `deploy/.env` cannot end up in an image.
- The JVM gets `-XX:MaxRAMPercentage=75`. Without it the heap is a quarter of the container
  memory.

## Consequences

Measured on a laptop for `csms-app`:

- Good: a code change rebuilds a layer of about 280 kB; the dependencies (63 MB) and the base
  image stay cached, locally and in a registry.
- Good: the image can be built and checked with Docker alone, which is what CI does.
- Bad: the first build is slow, because Gradle downloads everything: 13.5 minutes on a home
  connection with an empty cache, 1 minute 45 seconds with a warm one.
- Bad: tests do not run in the image build. CI runs them in the build job; the image build only
  compiles.
