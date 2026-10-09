# ChargeHub CSMS

Charging Station Management System for [ChargeHub](https://github.com/MihaelaAghirculesei/ChargeHub): a Java / Spring Boot backend that talks to charge points over OCPP 1.6J and will replace the simulated data source of the ChargeHub dashboard.

[![CI](https://github.com/MihaelaAghirculesei/chargehub-csms/actions/workflows/ci.yml/badge.svg)](https://github.com/MihaelaAghirculesei/chargehub-csms/actions/workflows/ci.yml)
[![CodeQL](https://github.com/MihaelaAghirculesei/chargehub-csms/actions/workflows/codeql.yml/badge.svg)](https://github.com/MihaelaAghirculesei/chargehub-csms/actions/workflows/codeql.yml)

## Status

Early stage. The foundations are in place; no OCPP message handling yet.

- **Done:** multi-module build, CI, quality gates, local infrastructure, database baseline, architecture tests.
- **Next:** the OCPP 1.6J protocol layer (messages, OCPP-J codec, JSON Schema validation), then a charge point simulator.

## Planned scope

- OCPP 1.6J Core Profile and Remote Trigger
- Charge point registry, live connector status, offline detection
- idTag authorization, charging sessions and meter values, including offline transactions
- Tariffs (energy, time, fixed fee, time-of-day) and charge detail records with VAT
- Payment with pre-authorization and capture (Stripe test mode)
- Versioned REST API and server-sent events for the ChargeHub dashboard

## Architecture

A modular monolith on Spring Modulith ([ADR 0002](docs/adr/0002-modular-monolith-with-spring-modulith.md)), split into Gradle modules ([ADR 0004](docs/adr/0004-gradle-multi-module-layout.md)):

| Module | Purpose |
|---|---|
| `ocpp-protocol` | OCPP 1.6J messages and JSON Schema validation, free of Spring |
| `csms-app` | Spring Boot application |
| `charge-point-simulator` | Simulated charge points, also used as a test fixture (not started yet) |

Module boundaries are enforced by tests: Spring Modulith verification and ArchUnit rules fail the build when one module reaches into another's internals.

All decisions: [docs/adr](docs/adr/README.md).

## Stack

Java 25 (virtual threads) · Spring Boot 4.1 · Spring Modulith 2.1 · PostgreSQL 18 with Flyway · Jackson 3 · Keycloak 26 · Gradle 9 (Kotlin DSL) · JUnit, AssertJ, Testcontainers, ArchUnit, jqwik

## Quality gates

Every pull request runs `./gradlew build` and starts the Docker Compose stack in CI. The build fails on:

- formatting drift (Spotless, google-java-format)
- Error Prone findings and nullness violations (NullAway with JSpecify `@NullMarked` packages)
- architecture violations (Spring Modulith, ArchUnit)
- Jackson version drift between modules ([ADR 0006](docs/adr/0006-align-jackson-on-the-spring-boot-version.md))

CodeQL scans the code. Test and coverage (JaCoCo) reports are uploaded as CI artifacts on every run, including failed ones.

## Quick start

Requirements: JDK 25, Docker.

```bash
cp deploy/.env.example deploy/.env    # then set the passwords
docker compose -f deploy/docker-compose.yml up -d
./gradlew build
```

PostgreSQL listens on `127.0.0.1:5432`, Keycloak on `127.0.0.1:8180`.

## Out of scope

- OCPP 2.0.1: the protocol sits behind its own module, so it can be added later ([ADR 0003](docs/adr/0003-ocpp-1-6j-as-first-protocol-version.md))
- Full OCPI roaming
- Signed meter values (Eichrecht)
- Kubernetes and Kafka

## License

[MIT](LICENSE)
