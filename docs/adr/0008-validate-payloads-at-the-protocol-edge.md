# 0008. Validate OCPP payloads at the protocol edge

- Status: Accepted
- Date: 2026-10-15

## Context

A charge point can send anything: a missing field, a number where a string should be, a
vendor name longer than allowed. OCPP 1.6J says how to answer: a CALLERROR with a specific
error code, such as `OccurenceConstraintViolation` for a missing field.

The Open Charge Alliance publishes a JSON Schema for every request and response of OCPP 1.6.
These schemas are the machine-readable version of the rules in the specification.

We have to decide where the payload is checked, and against what.

## Considered options

1. **Check every payload against the official schemas, as soon as the frame is decoded.**
   The rules come straight from the specification, and the code that handles an action only
   ever sees a valid payload. Chosen.
2. **Write the rules by hand in the message records, or with Bean Validation annotations.**
   The rules would be copied from the specification and could drift from it. Bean Validation
   would also bring a Jakarta dependency into `ocpp-protocol`, which must stay free of
   frameworks (docs/adr/0004).
3. **Rely on JSON parsing alone.** Jackson fails on a wrong type, but it does not know about
   required fields, maximum lengths or allowed values, and its errors do not say which OCPP
   error code to send back.

## Decision

- The official OCPP 1.6 JSON schemas are used as they are published, without edits. They come
  from the "OCPP 1.6 (all files & errata)" package, which already contains the schema fixes of
  the OCPP-J errata (for example the `SendLocalList` schema, which the first release made
  unreadable for most JSON Schema tools).
- The schemas are under CC BY-ND 4.0: they can be shipped with attribution, but not changed.
  A checksum file and a test fail the build if one of them differs from the published file.
- Every incoming CALL payload is checked against the request schema of its action before it
  reaches a handler. Every incoming CALLRESULT is checked against the response schema of the
  call it answers.
- A failed check is answered with a CALLERROR. The JSON Schema keyword that failed decides
  the error code:

  | Keyword | OCPP error code |
  |---|---|
  | `required` | `OccurenceConstraintViolation` |
  | `type` | `TypeConstraintViolation` |
  | anything else (`maxLength`, `enum`, `additionalProperties`, ...) | `PropertyConstraintViolation` |

- An action without a schema is not part of OCPP 1.6 and is answered with `NotImplemented`.
- What the central system sends is checked against the same schemas. A failure there is a
  bug on our side, so the message is not sent.

## Consequences

- Good: the rules are the ones of the specification, not our reading of it.
- Good: handlers can trust their input and stay small.
- Good: a charge point gets a precise error code, which makes field problems easier to debug.
- Bad: every message pays for a schema check. The schemas are loaded once and kept in memory;
  the cost is measured in the performance phase.
- Bad: if a schema has a bug, we cannot patch it ourselves. We wait for an Open Charge Alliance
  errata, or handle the case in code with a test that explains why.
- Bad: the keyword to error code mapping is our interpretation. The specification names the
  codes but does not map them to JSON Schema keywords.
