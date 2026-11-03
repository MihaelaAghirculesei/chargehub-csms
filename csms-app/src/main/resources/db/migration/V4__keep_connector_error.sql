-- Lengths are the limits of OCPP 1.6. Connectors stored before this migration had their error
-- dropped, so no error is the only value there is to give them.
alter table connector
    add column error_code        varchar(30) not null default 'NO_ERROR' check (error_code in (
                                     'CONNECTOR_LOCK_FAILURE', 'EV_COMMUNICATION_ERROR',
                                     'GROUND_FAILURE', 'HIGH_TEMPERATURE', 'INTERNAL_ERROR',
                                     'LOCAL_LIST_CONFLICT', 'NO_ERROR', 'OTHER_ERROR',
                                     'OVER_CURRENT_FAILURE', 'POWER_METER_FAILURE',
                                     'POWER_SWITCH_FAILURE', 'READER_FAILURE', 'RESET_FAILURE',
                                     'UNDER_VOLTAGE', 'OVER_VOLTAGE', 'WEAK_SIGNAL')),
    add column info              varchar(50),
    add column vendor_error_code varchar(50);

alter table connector alter column error_code drop default;
