package io.github.mihaelaaghirculesei.ocpp.message;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The status of a connector as OCPP 1.6 names it in StatusNotification.req. */
public enum ChargePointStatus {
  @JsonProperty("Available")
  AVAILABLE,
  @JsonProperty("Preparing")
  PREPARING,
  @JsonProperty("Charging")
  CHARGING,
  @JsonProperty("SuspendedEVSE")
  SUSPENDED_EVSE,
  @JsonProperty("SuspendedEV")
  SUSPENDED_EV,
  @JsonProperty("Finishing")
  FINISHING,
  @JsonProperty("Reserved")
  RESERVED,
  @JsonProperty("Unavailable")
  UNAVAILABLE,
  @JsonProperty("Faulted")
  FAULTED
}
