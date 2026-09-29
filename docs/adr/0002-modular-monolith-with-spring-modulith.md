# 0002. Modular monolith with Spring Modulith

- Status: Accepted
- Date: 2026-09-29

## Context

The system has several clear areas: the OCPP connection to charge points, stations and
connectors, authorization, charging sessions, tariffs, billing and payment. Each area changes
for its own reasons, so they should not be tangled together.

At the same time, the project has one developer, one database and no measured load yet.
Whatever structure we pick has to be cheap to run, easy to test on a laptop, and still keep
the areas apart.

## Considered options

1. **Microservices.** Each area is its own service with its own database. The boundaries are
   very strong. But every call between areas becomes a network call that can fail, data
   consistency needs sagas or distributed transactions everywhere, and one person has to
   deploy and monitor many services. We would pay all of that without a problem that needs
   it: no separate teams, no area that needs to scale on its own.
2. **Classic layered monolith** (controllers, services, repositories for everything). Cheap
   and familiar. But nothing stops the session code from calling billing internals directly.
   Over time every area depends on every other one, and splitting anything out later becomes
   very hard.
3. **Modular monolith, with boundaries checked by the build.** One application and one
   database, but split into modules that only talk through public APIs and domain events.
   Spring Modulith verifies the boundaries in a test, so a violation fails the build instead
   of relying on discipline.

## Decision

We build a modular monolith (option 3) and use Spring Modulith to enforce it.

- Each area is a top-level package under the application package: `ocpp`, `station`,
  `authorization`, `session`, `tariff`, `billing`, `payment`, `api`, plus `shared` for small
  value objects such as `Money`.
- A module may use another module's public types, but never its internal packages.
- Side effects across modules go through domain events (`SessionCompleted`, `CdrIssued`, ...)
  instead of direct calls. Spring Modulith stores published events in the same database
  transaction, which gives us a transactional outbox without a message broker.
- A test runs Spring Modulith's verification on every build.

## Consequences

- Good: one process, one database, one deployment. Local development and integration tests
  stay simple.
- Good: boundaries are checked by the build, not by memory.
- Good: if one module ever needs to run on its own, it already talks to the others through
  events, which is the hardest part of extracting a service.
- Bad: the whole application scales as one unit. If a single module needs much more capacity
  than the rest, we would have to extract it. We will decide that from load test numbers, not
  in advance.
- Bad: events inside one process are easy to overuse. A plain method call between modules is
  still fine when the caller needs an answer right away.
