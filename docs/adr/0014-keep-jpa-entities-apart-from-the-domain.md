# 0014. Keep JPA entities apart from the domain model

- Status: Accepted
- Date: 2026-10-27

## Context

Charging stations now have to be stored in PostgreSQL. The station is already modelled in plain
Java: `ChargingStation` with its `Connector` values and the status rules of ADR 0013. An
architecture test keeps Spring, JPA and Hibernate out of every `domain` package.

JPA wants things the domain does not: a no-argument constructor, fields it can set by
reflection, a version field, and mutable collections it can track. So we have to decide where
the JPA annotations go.

## Considered options

1. **Put the annotations on the domain classes.** One class instead of two, and no mapping code.
   But the domain would depend on JPA, the architecture test would have to allow it, and the
   rules would bend to what Hibernate needs: no final fields, a constructor that skips every
   check, collections that are lazy proxies. The cost grows with every aggregate.
2. **Use plain JDBC and write the SQL by hand.** The domain stays clean and nothing is hidden.
   But we lose optimistic locking, dirty checking and the schema validation at startup, and we
   would write and test that code ourselves for every table.
3. **Keep a JPA entity next to each aggregate, in the infrastructure package, and map between
   the two by hand.** Chosen.

## Decision

- The domain declares what it needs as a small interface, `ChargingStationRepository`, with
  `findById` and `save`. It knows nothing about how stations are stored.
- The infrastructure package has `ChargingStationEntity` and an adapter that implements the
  interface with Spring Data JPA. The mapping is written out by hand, as the project rules ask:
  no MapStruct.
- Connectors are not entities. They are an `@ElementCollection` of the station, keyed by
  connector id, always loaded with the station. They have no life of their own, so they have no
  id, no repository and no version of their own.
- The station has a `@Version` column. A station is loaded, changed and saved in one
  transaction; if another transaction changed it in between, the save fails instead of
  overwriting that change.
- The domain gets two ways to build a station: `register` for a first boot, which checks the
  input, and `restore` for a station read from the database, which takes the stored connectors
  as they are because they passed the rules when they were reported.
- Flyway owns the schema. Hibernate only validates that the entity matches it.

## Consequences

- Good: domain rules stay plain Java with no framework, tested in milliseconds.
- Good: the table can change shape without touching the domain, and the domain can change
  without a migration as long as the mapping still fits.
- Good: the domain interface is small enough that unit tests of the OCPP handlers use a map
  instead of a database. The adapter itself is tested against a real PostgreSQL.
- Bad: two classes and a mapping for each aggregate. A field added to one and forgotten in the
  other is a bug the compiler does not catch; the round-trip test of each adapter is what
  catches it.
- Bad: the version lives only in the entity. Lost updates are caught only when the load and the
  save happen in the same transaction, which the repository interface documents.
