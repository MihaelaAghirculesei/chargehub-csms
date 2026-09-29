# 0005. Allow Jackson 2 only inside springdoc

- Status: Accepted
- Date: 2026-09-29

## Context

Spring Boot 4 uses Jackson 3 for JSON. Jackson 3 lives in new packages (`tools.jackson.*`),
so it can sit next to Jackson 2 (`com.fasterxml.jackson.*`) without class conflicts. Our rule
is simple: our code uses Jackson 3 only, and new dependencies should use Jackson 3 or no
Jackson at all.

We want an OpenAPI description of the API. The Phase 7 API publishes it, and ChargeHub
generates its TypeScript types from it in Phase 8. The usual tool is springdoc-openapi. Its
3.x line supports Spring Boot 4, but it is built on swagger-core, and swagger-core still uses
Jackson 2 internally. Adding springdoc 3.1.1 puts `jackson-databind` 2.x on the classpath.

So the rule and the tool disagree, and we have to choose.

## Considered options

1. **Use springdoc and accept Jackson 2 as an internal dependency of it.** springdoc uses
   Jackson 2 only to build and write the OpenAPI document. Our endpoints, our payloads and
   the OCPP messages still go through Jackson 3.
2. **Write the OpenAPI file by hand (contract first), without springdoc.** A good practice
   when outside clients depend on the API. But the file must be kept in sync with the code by
   hand, and the tools that check a running API against the file (swagger-parser, OpenAPI
   validators) also use Jackson 2. The problem comes back, with more work on top.
3. **Postpone springdoc to Phase 7.** The Phase 0 spike exists to find exactly this kind of
   problem early. Postponing it removes the point of the spike.

## Decision

We use springdoc-openapi 3.x (option 1) and keep Jackson 2 contained:

- Jackson 2 is allowed on the classpath only as a transitive dependency of springdoc.
- Our code never imports `com.fasterxml.jackson.databind`. An ArchUnit rule fails the build if
  it does. Jackson annotations (`com.fasterxml.jackson.annotation`) stay allowed, because
  Jackson 3 uses the same annotation package.
- A test checks that the HTTP message converter used by Spring MVC is the Jackson 3 one, so
  our API responses cannot silently switch to Jackson 2.

We revisit this decision when swagger-core moves to Jackson 3.

## Consequences

- Good: the OpenAPI description is generated from the code and cannot drift from it.
- Good: the exception is written down and checked by the build, instead of being a surprise
  found in the dependency tree.
- Bad: two JSON libraries on the classpath. The application is a little larger, and an IDE
  may suggest Jackson 2 imports. The ArchUnit rule catches those.
- Bad: Jackson 2 security fixes must be followed too. Dependabot will propose them, and the
  Spring Boot BOM manages the Jackson 2 version.
