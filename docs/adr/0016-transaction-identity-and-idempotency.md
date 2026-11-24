# 0016. Give transactions their id in the database and recognise repeated messages by content

- Status: Accepted
- Date: 2026-11-24

## Context

In OCPP 1.6 the central system chooses the id of each transaction. The charge point gets it in
the answer to StartTransaction and sends it back in MeterValues and StopTransaction.

Charge points resend transaction messages. If an answer does not arrive, for example because
the connection dropped, the charge point sends the same message again, later or on a new
connection. The specification asks for this, because a lost stop means a lost bill.

So the central system receives some messages twice, and it must not count them twice: no
second session for one start, no second stop, no readings stored twice. OCPP 1.6 gives no
idempotency key for this. The message id of a frame does not help: a resent message can carry
a new one.

The central system runs as several instances on one PostgreSQL database, so two copies of one
message can be handled at the same moment by two instances.

## Considered options

1. **Keep the message ids already seen.** Does not work: a resent message may have a new id.
2. **Recognise a repeated message by its content, in code only.** Look for an existing session
   before creating one. Simple, but two copies handled at the same moment both find nothing and
   both insert.
3. **Recognise it by content in code, and enforce the same rule with database constraints.**
   Chosen.

## Decision

- Transaction ids come from a PostgreSQL sequence of type `integer`, the type OCPP gives the
  field. A sequence gives each value once, also across instances and rolled back transactions.
  It fails at the end of its range instead of starting over.
- The session is committed before the answer leaves. If the central system stops in between,
  the charge point has no id and sends the start again; it never holds an id the central system
  does not know.
- A start is identified by charge point, connector, id tag, meter start and timestamp. A start
  equal to a stored one gets the stored transaction id and tag status, and nothing new is
  stored or published. This check runs before the check for a tag that is already charging,
  or the repeated start would find its own session and get `ConcurrentTx`.
- A unique constraint on those five columns enforces the same rule. When two copies arrive at
  the same moment, the second insert fails and that copy is answered with an error. The charge
  point resends it, and then it finds the stored session.
- The first stop of a session counts. A later stop is answered normally and changes nothing.
  Two stops at the same moment are decided by the version of the session: the second commit
  fails, and its resent copy is treated as a later stop.
- A reading is identified by transaction, time, measurand, phase, location and context. A
  unique index with `nulls not distinct` enforces it, and the insert skips rows that are
  already there with `on conflict do nothing`. Readings never cause an error.
- Domain events are published inside the transaction. Listeners that act on them run after
  the commit, so a copy that rolls back publishes nothing they see.

## Consequences

- Good: every rule holds with any number of instances, because the database checks it.
- Good: a repeated message gets the same answer as the first one, so the charge point and the
  central system agree on the transaction.
- Bad: two copies of a start at the same moment make one of them fail with an error, and an
  error is logged. The charge point recovers by resending. An `insert ... on conflict` for
  sessions would avoid the error, but it would bypass JPA for a rare case.
- Bad: two different starts with the same five values would be taken as one. A charge point
  cannot start twice on one connector at the same moment with the same meter reading, so we
  accept it.
- Bad: two readings that differ only in value or unit for the same key keep the first one.
