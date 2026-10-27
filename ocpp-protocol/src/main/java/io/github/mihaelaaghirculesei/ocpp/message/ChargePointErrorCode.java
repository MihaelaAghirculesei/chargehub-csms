package io.github.mihaelaaghirculesei.ocpp.message;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The error a charge point reports with a status; {@link #NO_ERROR} when there is none. */
public enum ChargePointErrorCode {
  @JsonProperty("ConnectorLockFailure")
  CONNECTOR_LOCK_FAILURE,
  @JsonProperty("EVCommunicationError")
  EV_COMMUNICATION_ERROR,
  @JsonProperty("GroundFailure")
  GROUND_FAILURE,
  @JsonProperty("HighTemperature")
  HIGH_TEMPERATURE,
  @JsonProperty("InternalError")
  INTERNAL_ERROR,
  @JsonProperty("LocalListConflict")
  LOCAL_LIST_CONFLICT,
  @JsonProperty("NoError")
  NO_ERROR,
  @JsonProperty("OtherError")
  OTHER_ERROR,
  @JsonProperty("OverCurrentFailure")
  OVER_CURRENT_FAILURE,
  @JsonProperty("PowerMeterFailure")
  POWER_METER_FAILURE,
  @JsonProperty("PowerSwitchFailure")
  POWER_SWITCH_FAILURE,
  @JsonProperty("ReaderFailure")
  READER_FAILURE,
  @JsonProperty("ResetFailure")
  RESET_FAILURE,
  @JsonProperty("UnderVoltage")
  UNDER_VOLTAGE,
  @JsonProperty("OverVoltage")
  OVER_VOLTAGE,
  @JsonProperty("WeakSignal")
  WEAK_SIGNAL
}
