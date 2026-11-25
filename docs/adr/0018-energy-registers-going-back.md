# 0018. Find energy registers that go backwards when they are read, not when they are stored

- Status: Accepted
- Date: 2026-11-25

## Context

The energy register of a meter counts the energy delivered, so during a session it only goes
up. A reading lower than the one before it points to a meter fault, a replaced meter or a
wrong unit. Billing uses these readings, so someone has to see them.

The reading must still be kept. OCPP 1.6 cannot refuse readings, and dropping one would hide
the very fault we want to see.

"The one before it" means earlier by the clock of the charge point. A charge point that was
offline sends its queue late, so readings do not arrive in the order they were taken. A late
reading can make a reading that is already stored go backwards: 1100 Wh at 10:01 and 1300 Wh
at 10:03 look fine until 1400 Wh at 10:02 arrives.

## Considered options

1. **A flag column on each reading, set when it is stored.** A late reading would have to
   correct the flag of its neighbours in the same transaction. Easy to get almost right; the
   cases that slip through are the out-of-order ones we care about.
2. **A view that compares each reading with the one before it every time it is read.**
   Chosen.
3. **A materialized view or a nightly job.** Flags would be stale between refreshes, and
   nothing reads them often enough yet to need it.

## Decision

- The view `energy_register_reading` uses `LAG()` over the readings of a session, ordered by
  station time. Nothing is stored, so there is nothing to correct when a reading arrives late.
- Only `Energy.Active.Import.Register` readings in the `Raw` format with a plain number
  (`1500`, `1.5`) are compared. Signed data, numbers like `1e3` and units other than `Wh` and
  `kWh` are left out: guessing their value would make false alarms.
- Values are compared in Wh. A reading in kWh is multiplied by 1000.
- A register of the whole connector starts from `meterStart`, which OCPP 1.6 gives in Wh. A
  register of one phase, or of one location, is only compared with itself.
- When a message is stored, the readings that go backwards because of it are logged as
  warnings: new readings that go back, and stored readings that go back because a new one
  arrived before them. Only rows the insert really added count, so a resent message is not
  logged twice.
- A `meterStop` lower than `meterStart` is flagged by the generated column
  `charging_session.meter_stop_below_start` and logged when the stop arrives.

## Consequences

- Good: the flag is always right, whatever the order of arrival, with no code to keep it so.
- Good: support finds faulty meters with one query, and the reading itself is never changed.
- Bad: each read of the view sorts the readings of the sessions it asks for. The unique index
  on `(transaction_id, sampled_at, ...)` keeps that cheap per session; reports across many
  sessions may need a materialized view later.
- Bad: values that are not plain numbers are never checked. A meter that sends only signed
  data goes unchecked until signed data is verified.
