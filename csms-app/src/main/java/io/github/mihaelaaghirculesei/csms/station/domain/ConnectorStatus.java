package io.github.mihaelaaghirculesei.csms.station.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * The status of a connector, with the transitions of the OCPP 1.6 table in section 4.9.
 *
 * <p>Kept apart from the OCPP enum so that a later protocol version, with its own status names,
 * maps onto the same model.
 */
public enum ConnectorStatus {
  AVAILABLE,
  PREPARING,
  CHARGING,
  SUSPENDED_EV,
  SUSPENDED_EVSE,
  FINISHING,
  RESERVED,
  UNAVAILABLE,
  FAULTED;

  private static final Set<ConnectorStatus> OF_THE_WHOLE_STATION =
      EnumSet.of(AVAILABLE, UNAVAILABLE, FAULTED);

  /** Whether the specification allows a connector to go from this status to {@code next}. */
  public boolean allowsChangeTo(ConnectorStatus next) {
    return reachable().contains(next);
  }

  /** Whether connector 0, which stands for the whole station, can take this status. */
  public boolean appliesToTheWholeStation() {
    return OF_THE_WHOLE_STATION.contains(this);
  }

  private Set<ConnectorStatus> reachable() {
    return switch (this) {
      case AVAILABLE ->
          EnumSet.of(
              PREPARING, CHARGING, SUSPENDED_EV, SUSPENDED_EVSE, RESERVED, UNAVAILABLE, FAULTED);
      case PREPARING ->
          EnumSet.of(AVAILABLE, CHARGING, SUSPENDED_EV, SUSPENDED_EVSE, FINISHING, FAULTED);
      case CHARGING ->
          EnumSet.of(AVAILABLE, SUSPENDED_EV, SUSPENDED_EVSE, FINISHING, UNAVAILABLE, FAULTED);
      case SUSPENDED_EV ->
          EnumSet.of(AVAILABLE, CHARGING, SUSPENDED_EVSE, FINISHING, UNAVAILABLE, FAULTED);
      case SUSPENDED_EVSE ->
          EnumSet.of(AVAILABLE, CHARGING, SUSPENDED_EV, FINISHING, UNAVAILABLE, FAULTED);
      case FINISHING -> EnumSet.of(AVAILABLE, PREPARING, UNAVAILABLE, FAULTED);
      case RESERVED -> EnumSet.of(AVAILABLE, PREPARING, UNAVAILABLE, FAULTED);
      case UNAVAILABLE ->
          EnumSet.of(AVAILABLE, PREPARING, CHARGING, SUSPENDED_EV, SUSPENDED_EVSE, FAULTED);
      case FAULTED -> EnumSet.complementOf(EnumSet.of(FAULTED));
    };
  }
}
