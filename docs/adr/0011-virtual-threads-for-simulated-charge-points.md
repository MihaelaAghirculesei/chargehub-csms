# 0011. Run each simulated charge point on its own virtual thread

- Status: Accepted
- Date: 2026-10-22

## Context

The simulator must run many charge points in one process: 100 for the Phase 2 demo, and
thousands for the load tests of Phase 9. Each charge point holds its own WebSocket and spends
almost all of its time waiting: for the answer to a call, for the next heartbeat, or before it
connects again.

The code of a charge point is written as a plain sequence of blocking steps: connect, boot, wait,
send a heartbeat. That is easy to read, because it follows the protocol. It needs one thread per
charge point while it waits.

So we have to decide which kind of thread runs a charge point.

## Considered options

1. **One virtual thread per charge point.** A virtual thread is cheap: while it waits, it does
   not hold an operating system thread. The blocking code stays as it is. Chosen.
2. **One platform thread per charge point, or a pool of them.** Same code, and for 100 charge
   points it works. But every platform thread is an operating system thread with its own stack,
   so the cost grows with every charge point, and a pool smaller than the number of charge
   points would leave some of them without a thread while others wait.
3. **Asynchronous code** with `CompletableFuture` chains or a reactive library. It scales, but the
   steps of the protocol turn into callbacks, which are harder to read, test and debug.
4. **Structured concurrency** (`StructuredTaskScope`). It fits "start them all, stop them all"
   well, but it is still a preview feature in Java 25: it needs `--enable-preview` and its API
   may change. We do not ship preview features. We can look at it again once it is final.

## Decision

- `ChargePointRunner` starts each charge point on its own virtual thread, named
  `charge-point-<id>`, and interrupts and joins them all when the application stops. The join is
  bounded, so a charge point that ignores the interrupt cannot block the shutdown.
- Virtual threads are daemon threads, so they do not keep the JVM alive. The simulator sets
  `spring.main.keep-alive: true`. Without it, the process exits right after Spring has started
  and no charge point connects: we checked this.
- Each charge point waits a random time, up to `chargehub.simulator.charge-points.start-spread`
  (5 seconds by default), before it connects for the first time. Without it, every charge point
  of a large simulator would connect in the same second.
- All charge points share one `HttpClient`.
- `synchronized` is allowed in code that runs on virtual threads. Since Java 24 (JEP 491) a
  virtual thread that blocks inside `synchronized` no longer pins its carrier thread.

## Consequences

Measured on a laptop against the central system, 30 seconds after the start, every charge
point accepted:

| Charge points | Virtual threads | Platform threads |
|---|---|---|
| 100 | 93 OS threads, 164 MB | 179 OS threads, 171 MB |
| 1000 | 218 OS threads, 302 MB | 1234 OS threads, 371 MB |

- Good: the number of operating system threads stays almost flat as charge points are added.
  At 1000 charge points it is a sixth of the platform-thread version, and the gap grows with
  every charge point.
- Good: the charge point code stays a plain sequence of steps, and its tests stay simple.
- Bad, said plainly: at 100 charge points the difference is small. This decision pays off in the
  load tests of Phase 9, not in the Phase 2 demo.
- Bad: virtual threads hide where a charge point waits. A thread dump has to be read with the
  virtual threads included (`jcmd <pid> Thread.dump_to_file`).
