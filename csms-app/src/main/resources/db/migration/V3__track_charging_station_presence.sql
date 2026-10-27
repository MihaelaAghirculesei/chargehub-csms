alter table charging_station
    add column last_seen_at timestamptz,
    add column online       boolean not null default true;

-- Stations stored before this migration were last heard from at their last boot at the latest.
update charging_station set last_seen_at = last_boot_at;

alter table charging_station
    alter column last_seen_at set not null,
    alter column online drop default;

-- The offline check looks only for online stations not seen since a given time.
create index charging_station_online_last_seen_at on charging_station (last_seen_at) where online;
