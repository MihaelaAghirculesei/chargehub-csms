-- Lengths are the limits of OCPP 1.6; the id limit is the one the WebSocket endpoint enforces.
create table charging_station (
    id               varchar(48) primary key,
    vendor           varchar(20) not null,
    model            varchar(20) not null,
    serial_number    varchar(25),
    firmware_version varchar(50),
    last_boot_at     timestamptz not null,
    version          bigint      not null
);

-- Connectors belong to their station and have no life of their own, so the key is the pair.
create table connector (
    charging_station_id varchar(48) not null references charging_station (id),
    connector_id        integer     not null check (connector_id >= 0),
    status              varchar(20) not null check (status in (
                            'AVAILABLE', 'PREPARING', 'CHARGING', 'SUSPENDED_EV', 'SUSPENDED_EVSE',
                            'FINISHING', 'RESERVED', 'UNAVAILABLE', 'FAULTED')),
    reported_at         timestamptz not null,
    primary key (charging_station_id, connector_id)
);
