package io.github.mihaelaaghirculesei.simulator.chargepoint;

import io.github.mihaelaaghirculesei.ocpp.message.ChargePointErrorCode;
import io.github.mihaelaaghirculesei.ocpp.message.ChargePointStatus;
import io.github.mihaelaaghirculesei.ocpp.message.StatusNotificationRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * The connectors of one simulated charge point. A connector is Available, in one of the statuses of
 * a charging session, or Faulted with an error code. It keeps its status and its energy register
 * across reconnections, like the hardware it stands for. Not thread-safe.
 */
final class Connectors {

  private static final List<ChargePointErrorCode> FAULTS =
      Arrays.stream(ChargePointErrorCode.values())
          .filter(code -> code != ChargePointErrorCode.NO_ERROR)
          .toList();

  // Connector n is at index n - 1 in each array.
  // NO_ERROR means the connector works.
  private final ChargePointErrorCode[] errors;
  // The status of the session on the connector, or null when it is free.
  private final @Nullable ChargePointStatus[] sessions;
  // The energy register in Wh. It only goes up, as on a real meter.
  private final long[] registers;

  Connectors(int count) {
    errors = new ChargePointErrorCode[count];
    Arrays.fill(errors, ChargePointErrorCode.NO_ERROR);
    sessions = new ChargePointStatus[count];
    registers = new long[count];
  }

  /** The status of every connector, as a charge point reports after it boots. */
  List<StatusNotificationRequest> reports(Instant now) {
    List<StatusNotificationRequest> reports = new ArrayList<>(errors.length);
    for (int index = 0; index < errors.length; index++) {
      reports.add(report(index, now));
    }
    return reports;
  }

  /**
   * Moves time on: a Faulted connector is Available again, and an Available one may become Faulted.
   * A connector in a session never faults: the session would have to end first.
   *
   * @return the reports of the connectors that changed
   */
  List<StatusNotificationRequest> advance(FaultPolicy faults, RandomGenerator random, Instant now) {
    List<StatusNotificationRequest> changes = new ArrayList<>();
    for (int index = 0; index < errors.length; index++) {
      if (sessions[index] != null) {
        continue;
      }
      ChargePointErrorCode next = next(errors[index], faults, random);
      if (next != errors[index]) {
        errors[index] = next;
        changes.add(report(index, now));
      }
    }
    return changes;
  }

  /** The first connector that works and has no session. */
  OptionalInt free() {
    for (int index = 0; index < errors.length; index++) {
      if (errors[index] == ChargePointErrorCode.NO_ERROR && sessions[index] == null) {
        return OptionalInt.of(index + 1);
      }
    }
    return OptionalInt.empty();
  }

  /** Puts a session on the connector in the given status, and returns the report of it. */
  StatusNotificationRequest use(int connectorId, ChargePointStatus status, Instant now) {
    sessions[connectorId - 1] = status;
    return report(connectorId - 1, now);
  }

  /** Ends the session on the connector, and returns the report of it. */
  StatusNotificationRequest release(int connectorId, Instant now) {
    sessions[connectorId - 1] = null;
    return report(connectorId - 1, now);
  }

  long register(int connectorId) {
    return registers[connectorId - 1];
  }

  /** Counts energy delivered through the connector. */
  void deliver(int connectorId, long wattHours) {
    if (wattHours < 0) {
      throw new IllegalArgumentException("An energy register cannot go back, got " + wattHours);
    }
    registers[connectorId - 1] += wattHours;
  }

  private static ChargePointErrorCode next(
      ChargePointErrorCode current, FaultPolicy faults, RandomGenerator random) {
    if (current != ChargePointErrorCode.NO_ERROR) {
      return ChargePointErrorCode.NO_ERROR;
    }
    return faults.faultsNextConnector(random)
        ? FAULTS.get(random.nextInt(FAULTS.size()))
        : ChargePointErrorCode.NO_ERROR;
  }

  private StatusNotificationRequest report(int index, Instant now) {
    ChargePointErrorCode error = errors[index];
    ChargePointStatus session = sessions[index];
    ChargePointStatus status;
    if (error != ChargePointErrorCode.NO_ERROR) {
      status = ChargePointStatus.FAULTED;
    } else {
      status = session == null ? ChargePointStatus.AVAILABLE : session;
    }
    return new StatusNotificationRequest(index + 1, error, null, status, now, null, null);
  }
}
