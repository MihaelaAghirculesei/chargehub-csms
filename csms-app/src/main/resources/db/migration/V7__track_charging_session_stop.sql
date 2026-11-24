alter table charging_session
    add column stop_id_tag varchar(20) check (stop_id_tag = upper(stop_id_tag)),
    add column meter_stop  bigint,
    add column stopped_at  timestamptz,
    add column stop_reason varchar(15) check (stop_reason in (
                               'EMERGENCY_STOP', 'EV_DISCONNECTED', 'HARD_RESET', 'LOCAL', 'OTHER',
                               'POWER_LOSS', 'REBOOT', 'REMOTE', 'SOFT_RESET', 'UNLOCK_COMMAND',
                               'DE_AUTHORIZED')),
    -- A stop is stored whole or not at all. Its tag is optional, but only a stopped session has one.
    add constraint charging_session_stop_is_whole check (
        (meter_stop is null) = (stopped_at is null)
        and (stopped_at is null) = (stop_reason is null)
        and (stop_id_tag is null or stopped_at is not null));

-- A tag is busy only while its accepted session is open; once stopped it may charge again.
drop index charging_session_one_accepted_per_tag;
create unique index charging_session_one_open_accepted_per_tag
    on charging_session (id_tag) where tag_status = 'ACCEPTED' and stopped_at is null;
