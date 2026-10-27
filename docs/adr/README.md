# Architecture decision records

Why the project looks the way it does. How these records work is described in
[0001](0001-record-architecture-decisions.md); new records start from [the template](template.md).

| Number | Decision | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-modular-monolith-with-spring-modulith.md) | Modular monolith with Spring Modulith | Accepted |
| [0003](0003-ocpp-1-6j-as-first-protocol-version.md) | OCPP 1.6J as the first protocol version | Accepted |
| [0004](0004-gradle-multi-module-layout.md) | Gradle multi-module layout | Accepted |
| [0005](0005-jackson-2-only-inside-springdoc.md) | Allow Jackson 2 only inside springdoc | Accepted |
| [0006](0006-align-jackson-on-the-spring-boot-version.md) | Align Jackson across modules on the Spring Boot version | Accepted |
| [0007](0007-plain-websocket-for-ocpp.md) | Use plain WebSocket for OCPP, not STOMP | Accepted |
| [0008](0008-validate-payloads-at-the-protocol-edge.md) | Validate OCPP payloads at the protocol edge | Accepted |
| [0009](0009-one-outstanding-call-per-charge-point.md) | Refuse a second call while one is outstanding | Accepted |
| [0010](0010-simulator-as-a-first-class-component.md) | Build the charge point simulator as a first-class component | Accepted |
| [0011](0011-virtual-threads-for-simulated-charge-points.md) | Run each simulated charge point on its own virtual thread | Accepted |
| [0012](0012-container-images-from-a-multi-stage-dockerfile.md) | Build container images with one multi-stage Dockerfile | Accepted |
| [0013](0013-connector-status-never-refuses-a-report.md) | Model connector status as a state machine that never refuses a report | Accepted |
| [0014](0014-keep-jpa-entities-apart-from-the-domain.md) | Keep JPA entities apart from the domain model | Accepted |
