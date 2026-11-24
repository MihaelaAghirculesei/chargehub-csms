-- Readings are only added, never changed, so the key is just a running number. Text columns keep
-- the names of OCPP 1.6; their lengths fit the longest name the specification defines.
create table meter_value (
    id             bigint      generated always as identity primary key,
    transaction_id integer     not null references charging_session (transaction_id),
    sampled_at     timestamptz not null,
    value          text        not null,
    measurand      varchar(32) not null,
    unit           varchar(10) not null,
    phase          varchar(5),
    context        varchar(20) not null,
    location       varchar(10) not null,
    format         varchar(10) not null
);

-- Readings are read per session in time order. The index also serves the foreign key.
create index meter_value_session_time on meter_value (transaction_id, sampled_at);
