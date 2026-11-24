package io.github.mihaelaaghirculesei.csms.session;

/** Why a charge point stopped a session. */
public enum StopReason {
  EMERGENCY_STOP,
  EV_DISCONNECTED,
  HARD_RESET,
  LOCAL,
  OTHER,
  POWER_LOSS,
  REBOOT,
  REMOTE,
  SOFT_RESET,
  UNLOCK_COMMAND,
  DE_AUTHORIZED
}
