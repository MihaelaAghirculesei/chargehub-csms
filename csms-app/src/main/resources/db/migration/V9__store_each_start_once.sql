-- A charge point resends a start it got no answer for. The handler looks for the stored start
-- first; this constraint covers two copies handled at the same moment, see docs/adr/0016.
alter table charging_session
    add constraint charging_session_start_once
        unique (station_id, connector_id, id_tag, meter_start, started_at);
