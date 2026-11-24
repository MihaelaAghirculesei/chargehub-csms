-- A charge point resends readings it got no answer for; each reading is stored once, see
-- docs/adr/0016. Most readings have no phase, so a missing phase must count as equal. The new
-- index starts with the columns of the old one and serves the same queries.
drop index meter_value_session_time;
create unique index meter_value_once
    on meter_value (transaction_id, sampled_at, measurand, phase, location, context)
    nulls not distinct;
