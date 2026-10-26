# 0013. Model connector status as a state machine that never refuses a report

- Status: Accepted
- Date: 2026-10-26

## Context

A charge point tells the central system the status of each connector with a StatusNotification:
Available, Preparing, Charging, Finishing, Faulted and a few more. Section 4.9 of OCPP 1.6 has a
table of the changes that are allowed. For example a connector cannot go from Available straight
to Finishing, because nothing was charging.

Real reports do not always follow the table:

- A report can get lost, so the next one looks like a jump the table does not allow.
- A report can arrive twice: charge points repeat a call they think was lost.
- Reports can arrive out of order, for example when a charge point sends what it queued while
  offline.
- A charge point can simply have a bug.

OCPP 1.6 gives the central system no way to say no. The answer to a StatusNotification is an
empty confirmation, and a CALLERROR would only make the charge point send the same report again.

So we have to decide what the central system believes when a report does not fit.

## Considered options

1. **Apply every report.** The charge point is the one that knows its connectors. But a jump the
   table does not allow usually means we missed something, and later phases act on the status:
   Finishing ends a session, Faulted alerts an operator. Applying a jump hides the problem and
   can start those actions from a wrong picture.
2. **Answer with a CALLERROR.** Not possible in a useful way: the charge point repeats the call,
   and the report is the same the second time.
3. **Keep the last status we can trust, and record the report that does not fit.** Chosen.

## Decision

- The domain has its own `ConnectorStatus`, separate from the OCPP enum. Its allowed changes are
  the table of OCPP 1.6 section 4.9, and a test checks all 81 pairs against a copy of that table.
- Connector 0 stands for the whole station and only takes Available, Unavailable and Faulted.
- A connector seen for the first time takes any status. OCPP 1.6 does not say at boot how many
  connectors a station has, so connectors are learned from the reports.
- Each report has one of four outcomes. None of them is an exception:
  - **Applied**: the change is allowed. The connector takes the status and a
    `ConnectorStatusChanged` event is produced.
  - **Unchanged**: the connector already has this status. Only the time of the last report moves
    on, and there is no event. A report sent twice ends here.
  - **Stale**: the time in the report is older than the last report we took. Nothing changes.
  - **Rejected**: the table does not allow the change. Nothing changes.
- Order comes from the time the charge point puts in the report, not from the time it arrives.
- The code that handles StatusNotification logs and counts stale and rejected reports.

## Consequences

- Good: duplicates and late reports cannot move a connector back to an old status.
- Good: a jump that does not fit never starts the actions that depend on the status, and it
  shows up in logs and metrics instead of disappearing.
- Good: the rules are plain Java, tested in milliseconds, with no Spring or OCPP types.
- Bad: after a lost report the central system can be behind the charge point until the next
  report that fits. Most of them fit quickly: Available and Faulted can be reached from almost
  every status, and a charge point reports all its connectors again after a reboot.
- Bad: we trust the clock of the charge point to order reports. A charge point with a wrong
  clock can have its reports taken as stale. The offline detection and the clock skew fault of
  the simulator will tell us how much this matters.
