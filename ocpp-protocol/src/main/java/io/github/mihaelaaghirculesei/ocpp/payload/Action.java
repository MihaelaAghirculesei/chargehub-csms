package io.github.mihaelaaghirculesei.ocpp.payload;

import java.util.Optional;

/** Every action of OCPP 1.6, in all feature profiles. */
public enum Action {
  AUTHORIZE("Authorize"),
  BOOT_NOTIFICATION("BootNotification"),
  CANCEL_RESERVATION("CancelReservation"),
  CHANGE_AVAILABILITY("ChangeAvailability"),
  CHANGE_CONFIGURATION("ChangeConfiguration"),
  CLEAR_CACHE("ClearCache"),
  CLEAR_CHARGING_PROFILE("ClearChargingProfile"),
  DATA_TRANSFER("DataTransfer"),
  DIAGNOSTICS_STATUS_NOTIFICATION("DiagnosticsStatusNotification"),
  FIRMWARE_STATUS_NOTIFICATION("FirmwareStatusNotification"),
  GET_COMPOSITE_SCHEDULE("GetCompositeSchedule"),
  GET_CONFIGURATION("GetConfiguration"),
  GET_DIAGNOSTICS("GetDiagnostics"),
  GET_LOCAL_LIST_VERSION("GetLocalListVersion"),
  HEARTBEAT("Heartbeat"),
  METER_VALUES("MeterValues"),
  REMOTE_START_TRANSACTION("RemoteStartTransaction"),
  REMOTE_STOP_TRANSACTION("RemoteStopTransaction"),
  RESERVE_NOW("ReserveNow"),
  RESET("Reset"),
  SEND_LOCAL_LIST("SendLocalList"),
  SET_CHARGING_PROFILE("SetChargingProfile"),
  START_TRANSACTION("StartTransaction"),
  STATUS_NOTIFICATION("StatusNotification"),
  STOP_TRANSACTION("StopTransaction"),
  TRIGGER_MESSAGE("TriggerMessage"),
  UNLOCK_CONNECTOR("UnlockConnector"),
  UPDATE_FIRMWARE("UpdateFirmware");

  private final String wireName;

  Action(String wireName) {
    this.wireName = wireName;
  }

  /** The name used in a CALL frame. Case-sensitive. */
  public String wireName() {
    return wireName;
  }

  public static Optional<Action> fromWireName(String wireName) {
    for (Action action : values()) {
      if (action.wireName.equals(wireName)) {
        return Optional.of(action);
      }
    }
    return Optional.empty();
  }
}
