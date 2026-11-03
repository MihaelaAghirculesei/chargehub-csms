package io.github.mihaelaaghirculesei.simulator.chargepoint;

import io.github.mihaelaaghirculesei.ocpp.message.ChargePointErrorCode;
import io.github.mihaelaaghirculesei.ocpp.message.ChargePointStatus;
import io.github.mihaelaaghirculesei.ocpp.message.StatusNotificationRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The connectors of one simulated charge point. A connector is Available, or Faulted with an error
 * code. It keeps its status across reconnections, like the hardware it stands for. Not thread-safe.
 */
final class Connectors {

  private static final List<ChargePointErrorCode> FAULTS =
      Arrays.stream(ChargePointErrorCode.values())
          .filter(code -> code != ChargePointErrorCode.NO_ERROR)
          .toList();

  // The error of each connector, so connector n is at index n - 1. NO_ERROR means Available.
  private final ChargePointErrorCode[] errors;

  Connectors(int count) {
    errors = new ChargePointErrorCode[count];
    Arrays.fill(errors, ChargePointErrorCode.NO_ERROR);
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
   *
   * @return the reports of the connectors that changed
   */
  List<StatusNotificationRequest> advance(FaultPolicy faults, RandomGenerator random, Instant now) {
    List<StatusNotificationRequest> changes = new ArrayList<>();
    for (int index = 0; index < errors.length; index++) {
      ChargePointErrorCode next = next(errors[index], faults, random);
      if (next != errors[index]) {
        errors[index] = next;
        changes.add(report(index, now));
      }
    }
    return changes;
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
    ChargePointStatus status =
        error == ChargePointErrorCode.NO_ERROR
            ? ChargePointStatus.AVAILABLE
            : ChargePointStatus.FAULTED;
    return new StatusNotificationRequest(index + 1, error, null, status, now, null, null);
  }
}
