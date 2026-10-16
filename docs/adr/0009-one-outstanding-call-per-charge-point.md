# 0009. Refuse a second call while one is outstanding

- Status: Accepted
- Date: 2026-10-16

## Context

The central system sends commands to charge points: reset, start or stop a transaction, change
a configuration key. Each command is a CALL frame, and the answer comes back on the same
WebSocket as a CALLRESULT or a CALLERROR with the same message id.

OCPP-J 1.6 allows only one outstanding CALL in each direction. After sending a CALL, the
central system must wait for the answer, or for a timeout, before it sends the next CALL to that
charge point.

So we have to decide what happens when a second command for the same charge point arrives while
the first one is still waiting.

## Considered options

1. **Refuse the second call at once.** The caller gets a clear "a call is in progress" failure
   and decides what to do. Chosen.
2. **Queue it and send it when the first one is answered.** The caller does not see the wait.
   But the queue can grow without limit while a charge point is slow or silent, and a command can
   go out minutes after it was asked for, when it no longer makes sense (a remote start for a
   driver who has left).
3. **A small bounded queue.** It limits the memory, but keeps the stale command problem, and
   adds a second failure case (queue full) that callers must handle anyway.

## Decision

- Each charge point has at most one outstanding call. A second call is refused with an
  exception before anything is sent.
- A CALLRESULT with the message id of the outstanding call completes it. A CALLERROR fails it
  and carries the error code and description. An answer with another message id, or one that
  arrives after the timeout, completes nothing and is reported to the caller as unmatched.
- An outstanding call fails after a configurable timeout, and the charge point can take a new
  call.
- When the connection of a charge point closes, its outstanding call fails at once instead of
  waiting for the timeout.
- The rule lives in `ocpp-protocol`, next to the codec, because it is part of OCPP-J and does
  not depend on Spring or on WebSocket.

## Consequences

- Good: no hidden queue, no unbounded memory, and no command sent long after it was asked for.
- Good: every outcome (answered, refused, rejected by the charge point, timed out, connection
  lost) is a distinct result that the remote command API in Phase 7 can show as a status.
- Bad: callers have to handle "busy". For the remote command API that means a clear status, and
  a retry is the operator's choice.
- Bad: a charge point that never answers blocks new calls until the timeout. The timeout must be
  short enough for operators, and long enough for slow charge points on mobile networks.
