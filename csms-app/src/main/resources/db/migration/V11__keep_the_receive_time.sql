-- Each transaction message keeps two times: the one the charge point gave it, for billing, and the
-- one the central system received it at, for auditing, see docs/adr/0017. Rows stored before get
-- their station time as receive time, the best guess there is.
alter table charging_session
    add column start_received_at timestamptz,
    add column stop_received_at  timestamptz;
update charging_session
   set start_received_at = started_at,
       stop_received_at  = stopped_at;
alter table charging_session
    alter column start_received_at set not null,
    add constraint charging_session_stop_received
        check ((stop_received_at is null) = (stopped_at is null));

alter table meter_value add column received_at timestamptz;
update meter_value set received_at = sampled_at;
alter table meter_value alter column received_at set not null;

comment on column charging_session.started_at is 'Start time by the clock of the charge point';
comment on column charging_session.start_received_at is 'When the central system received the start';
comment on column charging_session.stopped_at is 'Stop time by the clock of the charge point';
comment on column charging_session.stop_received_at is 'When the central system received the stop';
comment on column meter_value.sampled_at is 'Reading time by the clock of the charge point';
comment on column meter_value.received_at is 'When the central system received the reading';
