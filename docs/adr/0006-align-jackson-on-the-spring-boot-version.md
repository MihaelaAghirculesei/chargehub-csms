# 0006. Align Jackson across modules on the Spring Boot version

- Status: Accepted
- Date: 2026-09-29

## Context

`csms-app` gets its Jackson 3 version from the Spring Boot BOM: 3.1.5 with Spring Boot 4.1.1.
`ocpp-protocol` does not use the Spring Boot BOM ([0004](0004-gradle-multi-module-layout.md)),
so it gets whatever version its own libraries ask for.

The main library there is json-schema-validator, which validates OCPP payloads. Its versions
ask for different Jackson lines:

| json-schema-validator | Jackson it asks for |
|---|---|
| 3.0.6 | 3.1.4 |
| 3.0.7 (latest) | 3.2.1 |

Once `csms-app` depends on `ocpp-protocol`, Gradle picks the highest version it sees. With
3.0.7, that would move the whole application, Spring included, to Jackson 3.2.1: a version
Spring Boot 4.1 was not released with. Nobody would notice until something failed at runtime.

3.0.7 moved to Jackson 3.2.1 to fix CVE-2026-59889. The advisory lists 3.1.5 as a patched
version too, so the Spring Boot version is not affected.

## Considered options

1. **json-schema-validator 3.0.6, Jackson aligned to 3.1.5.** Every library runs on the Jackson
   line it was built for; 3.1.4 to 3.1.5 is a patch upgrade. We lose two small fixes from 3.0.7
   (number comparison in `uniqueItems`, whitespace in email formats) that OCPP schemas do not
   use.
2. **json-schema-validator 3.0.7, Jackson forced down to 3.1.5.** A library compiled against
   3.2 would run on 3.1. If it uses anything new in 3.2, it fails with `NoSuchMethodError` at
   runtime, possibly on a path no test covers.
3. **json-schema-validator 3.0.7, the whole application on Jackson 3.2.1.** Overrides the
   Spring Boot BOM, so Spring runs on a Jackson version it was not tested with.

## Decision

We use option 1.

- The Jackson version is one variable in the version catalog. It must equal the version the
  Spring Boot BOM manages.
- `ocpp-protocol` imports the Jackson BOM with that variable.
- A build check fails when any module resolves `tools.jackson.core:jackson-databind` to a
  different version. Upgrading json-schema-validator alone to a release that needs a newer
  Jackson line therefore fails the build. Jackson, the validator and Spring Boot move together,
  in one grouped update.

## Consequences

- Good: one Jackson 3 version in every module, the one Spring Boot was released with.
- Good: drift is caught by the build, not found in production.
- Good: the reason for staying on 3.0.6 is written down, so nobody "fixes" it by upgrading.
- Bad: we stay one validator release behind until Spring Boot moves to Jackson 3.2.
- Bad: the catalog repeats a version the Spring Boot BOM already knows. The build check keeps
  the two equal.
