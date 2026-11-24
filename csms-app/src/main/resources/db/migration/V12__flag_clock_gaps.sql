-- Set when the station time of the start or the stop is far from its receive time, see
-- docs/adr/0017. Sessions stored before cannot be judged and stay unflagged.
alter table charging_session add column clock_gap_flagged boolean not null default false;
