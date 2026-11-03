package io.github.mihaelaaghirculesei.csms.station.domain;

/** The error a charge point reports with a status; {@link #NO_ERROR} when there is none. */
public enum ConnectorErrorCode {
  CONNECTOR_LOCK_FAILURE,
  EV_COMMUNICATION_ERROR,
  GROUND_FAILURE,
  HIGH_TEMPERATURE,
  INTERNAL_ERROR,
  LOCAL_LIST_CONFLICT,
  NO_ERROR,
  OTHER_ERROR,
  OVER_CURRENT_FAILURE,
  POWER_METER_FAILURE,
  POWER_SWITCH_FAILURE,
  READER_FAILURE,
  RESET_FAILURE,
  UNDER_VOLTAGE,
  OVER_VOLTAGE,
  WEAK_SIGNAL
}
