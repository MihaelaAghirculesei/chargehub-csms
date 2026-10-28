# 0015. Use a PostgreSQL advisory lock so only one instance runs a scheduled job

- Status: Accepted
- Date: 2026-10-28

## Context

The central system has to notice when a charge point stops sending heartbeats. A scheduled job
does this: every 30 seconds it looks for online stations that have not been seen for three
heartbeat intervals and marks them offline.

The central system runs as several instances behind a load balancer. Each instance has the same
scheduler, so each one would run the job. Two instances working on the same stations at the
same time waste work, can publish `StationWentOffline` twice for one station, and fail each
other's transactions. So only one instance may run the job at a time.

There is no coordinator in the system to pick one. All instances share one PostgreSQL database.

## Considered options

1. **Pick one instance by configuration.** Simple, but that instance becomes special: if it is
   down, nobody detects offline stations, and every deployment has to remember which one it is.
2. **ShedLock.** A well-known library: a table holds a row per job with a lock expiry time. It
   works with many databases. But it is a new dependency and a new table, and the lock is held
   for a fixed time: if an instance dies while it holds the lock, the job does not run until
   the time runs out, and if a run takes longer than that time, two instances can run at once.
3. **A PostgreSQL advisory lock held by the job transaction.** Chosen.

## Decision

- Each batch of the job runs in its own transaction, and the first statement is
  `pg_try_advisory_xact_lock(key)`. If another transaction holds the lock, the call returns
  false at once and this instance skips the run. It does not wait.
- PostgreSQL releases the lock when the transaction commits or rolls back. If the instance
  dies, its connection closes and PostgreSQL releases the lock too. There is no expiry time to
  tune.
- The key is a constant in the code. Each job that needs a lock gets its own number.
- The job reads at most 100 stations per batch, in the order they went silent, and keeps going
  until a batch is not full. Short transactions keep the lock and the row locks short.
- The job is safe to run twice in a row, by the same or another instance: a station already
  offline is skipped, and the domain checks again that the station is still silent before it
  marks it offline.
- A heartbeat can arrive while the job is changing the same station. Optimistic locking on the
  station decides: the second transaction to commit fails. If the job loses, its whole batch
  rolls back, the station stays online with its new last-seen time, and the other stations of
  the batch are taken again on the next run.

## Consequences

- Good: no new dependency, no new table, nothing to clean up after a crash.
- Good: any instance can run the job. Losing one instance loses nothing.
- Good: the lock follows the transaction, so the lock and the changes it protects always end
  together.
- Bad: it works only with PostgreSQL. Moving to another database means changing this decision.
- Bad: a lock key is a plain number. Two jobs that use the same number by mistake block each
  other, and nothing checks it.
- Bad: the job publishes events inside its transaction. A listener that runs before the commit
  can see an event for a change that then rolls back, so listeners that act on these events
  have to run after the commit. The outbox planned for a later phase makes this reliable.
