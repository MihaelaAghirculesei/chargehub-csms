# ChargeHub CSMS

Charging Station Management System for [ChargeHub](https://github.com/MihaelaAghirculesei/ChargeHub): a Java / Spring Boot backend that talks to charge points over OCPP 1.6J and will replace the simulated data source of the ChargeHub dashboard.

[![CI](https://github.com/MihaelaAghirculesei/chargehub-csms/actions/workflows/ci.yml/badge.svg)](https://github.com/MihaelaAghirculesei/chargehub-csms/actions/workflows/ci.yml)
[![CodeQL](https://github.com/MihaelaAghirculesei/chargehub-csms/actions/workflows/codeql.yml/badge.svg)](https://github.com/MihaelaAghirculesei/chargehub-csms/actions/workflows/codeql.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=MihaelaAghirculesei_chargehub-csms&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=MihaelaAghirculesei_chargehub-csms)

## Status

A charge point can connect over OCPP 1.6J, is accepted on BootNotification, and its heartbeats are answered. Nothing is stored about it yet.

- **Done:** foundations (multi-module build, CI, quality gates, local infrastructure, database baseline, architecture tests) and the OCPP 1.6J protocol layer: OCPP-J codec, validation against the official JSON schemas, the WebSocket endpoint, outbound calls matched with their answers, and an answer to every incoming call. A charge point simulator that runs many charge points, each booting, sending heartbeats and reconnecting like a real one, and dropping connections or repeating calls on purpose.
- **Next:** the simulator as a test fixture for the central system, then the charge point registry and live connector status.

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
| `ocpp-protocol` | OCPP-J codec, official OCPP 1.6 JSON schemas and validation, outbound call matching, typed messages; free of Spring |
| `csms-app` | Spring Boot application. Spring Modulith modules: `ocpp` (WebSocket endpoint, connections, call routing) and `station` (charge points) |
| `charge-point-simulator` | Simulated charge points that speak OCPP only, through `ocpp-protocol` ([ADR 0010](docs/adr/0010-simulator-as-a-first-class-component.md)), one virtual thread each ([ADR 0011](docs/adr/0011-virtual-threads-for-simulated-charge-points.md)) |

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
- coverage below 80% of lines or branches in `ocpp-protocol` and in domain packages (JaCoCo)
- a change to any of the official OCPP schemas, which are kept byte for byte as published

CodeQL scans the code and SonarQube Cloud analyses every pull request. Test and coverage (JaCoCo) reports are uploaded as CI artifacts on every run, including failed ones.

## Quick start

### Demo with Docker only

The central system and 100 simulated charge points, built from the repository. The first build downloads every dependency and takes several minutes.

```bash
cp deploy/.env.example deploy/.env    # then set the passwords
docker compose -f deploy/docker-compose.yml --profile sim up -d --build
deploy/check-simulated-charge-points.sh    # all 100 accepted, none reconnecting for a minute
```

CI runs the same check on every change.

The same charge points can misbehave on purpose: with the `chaos` profile their connections drop at random and some calls arrive twice (`charge-point-simulator/src/main/resources/application-chaos.yaml`).

```bash
docker compose -f deploy/docker-compose.yml --profile chaos up -d --build
```

### Development

Requirements: JDK 25, Docker.

```bash
cp deploy/.env.example deploy/.env    # then set the passwords
docker compose -f deploy/docker-compose.yml up -d
./gradlew build
```

PostgreSQL listens on `127.0.0.1:5432`, Keycloak on `127.0.0.1:8180`.

Run the application with readable logs (the database password comes from `deploy/.env`):

```bash
set -a && . deploy/.env && set +a
SPRING_PROFILES_ACTIVE=dev ./gradlew :csms-app:bootRun
```

Or run it as a container, built from the repository with Docker alone ([ADR 0012](docs/adr/0012-container-images-from-a-multi-stage-dockerfile.md)). The first build downloads every dependency and takes several minutes:

```bash
docker compose -f deploy/docker-compose.yml --profile app up -d --build
```

A charge point connects to `ws://localhost:8080/ocpp/{chargePointId}` with the WebSocket subprotocol `ocpp1.6`. The id may contain letters, digits and `._:-`, up to 48 characters.

With the application running, start 100 simulated charge points in a second terminal:

```bash
./gradlew :charge-point-simulator:bootRun --args="--chargehub.simulator.charge-points.count=100"
```

Their ids are `SIM-0001` to `SIM-0100` (`chargehub.simulator.charge-points.id-prefix`), and they connect over the first 5 seconds (`chargehub.simulator.charge-points.start-spread`). Each one connects to `ws://localhost:8080/ocpp` (`chargehub.simulator.central-system`), boots and sends heartbeats at the interval the central system answers with. When the connection is lost it connects again, waiting longer after each failed attempt (`chargehub.simulator.reconnect.*`).

## Out of scope

- OCPP 2.0.1: the protocol sits behind its own module, so it can be added later ([ADR 0003](docs/adr/0003-ocpp-1-6j-as-first-protocol-version.md))
- Full OCPI roaming
- Signed meter values (Eichrecht)
- Kubernetes and Kafka

## License

[Apache License 2.0](LICENSE).

The OCPP 1.6 JSON schemas in `ocpp-protocol` are published by the Open Charge Alliance under [CC BY-ND 4.0](https://creativecommons.org/licenses/by-nd/4.0/legalcode) and are included unchanged; see their [NOTICE](ocpp-protocol/src/main/resources/ocpp16/schemas/NOTICE).
