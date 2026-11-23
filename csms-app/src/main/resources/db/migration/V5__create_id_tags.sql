-- Tags are stored in upper case so that the case-insensitive match of OCPP 1.6 is a key lookup.
create table id_tag (
    id_tag        varchar(20) primary key check (id_tag = upper(id_tag)),
    status        varchar(10) not null check (status in ('ACCEPTED', 'BLOCKED', 'EXPIRED')),
    expires_at    timestamptz,
    parent_id_tag varchar(20) check (parent_id_tag = upper(parent_id_tag))
);
