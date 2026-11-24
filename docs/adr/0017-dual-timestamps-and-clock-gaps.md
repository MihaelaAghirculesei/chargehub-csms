# 0017. Keep the station time and the receive time, and flag sessions where they drift apart

- Status: Accepted
- Date: 2026-11-24

## Context

A charge point that loses its connection keeps charging. It queues StartTransaction,
MeterValues and StopTransaction and sends them when it is back, minutes or hours later. Each
of these messages carries a time taken by the clock of the charge point.

That clock can be wrong. Charge points sync it from the central system or from the network, and
some drift or start with a wrong date after a power loss.

So for every transaction message there are two times: when the charge point says it happened,
and when the central system received it. Billing needs the first: the driver pays for the
energy at the time it was delivered. Auditing and support need the second, and the difference
between them is the only hint we have that a clock is wrong.

## Considered options

1. **Keep only the station time.** Simple, but a wrong clock goes unnoticed and late messages
   cannot be told from live ones.
2. **Replace the station time with the receive time when they differ too much.** Hides the
   problem and bills queued energy at the wrong time.
3. **Keep both, bill on the station time, and flag a session when the two drift apart.**
   Chosen.
4. **Reject messages that are too old.** The specification asks charge points to resend
   transaction messages until they are answered. Rejecting them would lose energy that was
   really delivered.

## Decision

- Starts, stops and meter readings keep the station time in the columns they already had
  (`started_at`, `stopped_at`, `sampled_at`) and get the receive time next to it. The receive
  time comes from the injected `Clock`. Both are stored in UTC. Column comments in the database
  say which time is which.
- Late messages are accepted whatever their age.
- One message cannot tell a late delivery from a clock that runs behind: both look like a
  station time in the past. It can tell a clock that runs ahead: no message arrives before it
  was sent. So we classify the gap of each start and stop:
  - on time: the two times are within the threshold;
  - late: the station time is older than the threshold. Logged at info level, because it is
    normal after a lost connection;
  - ahead: the station time is later than the receive time by more than the threshold. Logged
    as a warning, because the clock is certainly wrong.
- A session whose start or stop is late or ahead gets `clock_gap_flagged`. The flag means
  "check this session", not "this clock is wrong".
- The threshold is `chargehub.session.clock-gap-threshold`, 5 minutes by default. Shorter
  flags slow networks and retries; longer hides real clock errors.
- Rows stored before this change get their station time as receive time, the best guess
  available.

## Consequences

- Good: no message is lost or rewritten, and billing uses the time the energy was delivered.
- Good: support can find suspicious sessions with one column, and see both times.
- Bad: every session recorded during an outage is flagged, so the flag alone does not prove a
  clock error. The log level and the direction of the gap tell the cases apart.
- Bad: a clock that runs behind by more than the threshold looks like a late delivery. Telling
  them apart would need the station to report its clock while connected, for example in
  heartbeats, which OCPP 1.6 does not do.
