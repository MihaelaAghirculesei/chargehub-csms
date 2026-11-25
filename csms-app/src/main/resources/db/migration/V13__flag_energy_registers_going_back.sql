-- An energy register only goes up during a session, see docs/adr/0018. The flags are worked out
-- when they are read, so a reading that arrives late puts the ones around it right without any
-- stored row to correct.

-- Null while the session is open.
alter table charging_session
    add column meter_stop_below_start boolean generated always as (meter_stop < meter_start) stored;

-- Only plain numbers in Wh or kWh are compared, in Wh; signed data and other units are left out.
-- A register of the whole connector starts from meterStart, which OCPP 1.6 gives in Wh. A register
-- of one phase, or of one location, is compared only with itself.
create view energy_register_reading as
with register as (
    select m.id, m.transaction_id, m.sampled_at, m.received_at, m.phase, m.location,
           s.meter_start,
           case when m.value !~ '^[0-9]+(\.[0-9]+)?$' then null
                when m.unit = 'Wh' then m.value::numeric
                when m.unit = 'kWh' then m.value::numeric * 1000
           end as energy_wh
    from meter_value m
    join charging_session s using (transaction_id)
    where m.measurand = 'Energy.Active.Import.Register'
      and m.format = 'Raw'
),
ordered as (
    select id, transaction_id, sampled_at, received_at, phase, location, energy_wh,
           lag(id) over by_station_time as previous_id,
           coalesce(lag(energy_wh) over by_station_time,
                    case when phase is null then meter_start end) as previous_wh
    from register
    where energy_wh is not null
    window by_station_time as (
        partition by transaction_id, phase, location order by sampled_at, id)
)
select id, transaction_id, sampled_at, received_at, phase, location, energy_wh,
       previous_id, previous_wh, coalesce(energy_wh < previous_wh, false) as went_back
from ordered;

comment on column charging_session.meter_stop_below_start is
    'The energy register at the stop is lower than at the start';
comment on view energy_register_reading is
    'Energy register readings in Wh, each with the one before it by station time';
