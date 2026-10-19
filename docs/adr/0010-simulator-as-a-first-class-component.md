# 0010. Build the charge point simulator as a first-class component

- Status: Accepted
- Date: 2026-10-19

## Context

A central system is only useful with charge points connected to it. We have no real charge
points, and we need many of them: for the demo, for load tests (Phase 9), and for integration
tests that run a whole charging session (Phases 3 and 4).

Public tools exist, but they simulate one charge point at a time through a web page, or need
their own setup and scripts. None of them can start a hundred charge points from one file, inject
the faults we care about, or run inside our tests.

So we have to decide how much the simulator is part of this project.

## Considered options

1. **A module of this repository, held to the same rules as the central system.** Same build,
   same quality gates, same tests, and it reuses `ocpp-protocol`. Chosen.
2. **A script next to the project**, for example in Python. Quick to start, but it would encode
   OCPP a second time. A bug in the script and a bug in the central system would look the same,
   and nothing would check the script.
3. **An existing open-source simulator.** No code to write, but we would depend on its features
   and its release pace, and we could not use it as a test fixture.

## Decision

- The simulator is the Gradle module `charge-point-simulator`, a Spring Boot application without
  a web server.
- It uses the codec, the call correlation and the official schemas of `ocpp-protocol`. Every
  request it sends and every response it receives is validated, so a broken message is caught
  on the side that produced it.
- It talks to the central system only over OCPP. It never depends on `csms-app`, so it tests the
  central system the way a real charge point would.
- The code that plays a charge point has no Spring dependency. Spring only wires and starts it,
  so tests and, later, the integration tests of `csms-app` can run it directly.
- It follows the same rules as the rest of the code: tests in the same pull request, coverage
  gate, architecture rules, ADRs for its decisions.

## Consequences

- Good: one implementation of OCPP-J. A fix in `ocpp-protocol` reaches both sides.
- Good: the simulator can grow with the central system: each new message is added on both sides
  in the same phase.
- Good: tests can start real charge points instead of mocking the protocol.
- Bad: it is code we maintain, with its own tests and its own bugs.
- Bad: sharing `ocpp-protocol` means both sides can share a misunderstanding of the
  specification. The examples of the specification in the tests of `csms-app` stay the
  reference for that.
