-- OCPP 1.6 carries the transaction id as an integer. At the end of its range the sequence fails
-- instead of starting over, so an id is never given to two sessions.
create sequence transaction_id as integer;

-- No foreign key to charging_station: each module owns its tables, so a session never blocks a
-- change to the station schema.
create table charging_session (
    transaction_id integer     primary key,
    station_id     varchar(48) not null,
    connector_id   integer     not null check (connector_id > 0),
    id_tag         varchar(20) not null check (id_tag = upper(id_tag)),
    meter_start    bigint      not null,
    started_at     timestamptz not null,
    tag_status     varchar(15) not null check (tag_status in (
                       'ACCEPTED', 'BLOCKED', 'EXPIRED', 'INVALID', 'CONCURRENT_TX')),
    version        bigint      not null
);

-- The handler answers ConcurrentTx before it gets here. The index covers two starts with one tag
-- at the same moment on two charge points: the second insert fails and is answered with an error,
-- so its charge point retries and then gets ConcurrentTx.
create unique index charging_session_one_accepted_per_tag
    on charging_session (id_tag) where tag_status = 'ACCEPTED';
