package io.github.mihaelaaghirculesei.ocpp.message;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Why a charge point stopped a transaction. */
public enum Reason {
  @JsonProperty("EmergencyStop")
  EMERGENCY_STOP,
  @JsonProperty("EVDisconnected")
  EV_DISCONNECTED,
  @JsonProperty("HardReset")
  HARD_RESET,
  @JsonProperty("Local")
  LOCAL,
  @JsonProperty("Other")
  OTHER,
  @JsonProperty("PowerLoss")
  POWER_LOSS,
  @JsonProperty("Reboot")
  REBOOT,
  @JsonProperty("Remote")
  REMOTE,
  @JsonProperty("SoftReset")
  SOFT_RESET,
  @JsonProperty("UnlockCommand")
  UNLOCK_COMMAND,
  @JsonProperty("DeAuthorized")
  DE_AUTHORIZED
}
