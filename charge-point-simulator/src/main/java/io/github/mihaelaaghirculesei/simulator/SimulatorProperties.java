package io.github.mihaelaaghirculesei.simulator;

import io.github.mihaelaaghirculesei.simulator.chargepoint.FaultPolicy;
import io.github.mihaelaaghirculesei.simulator.chargepoint.ReconnectPolicy;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param centralSystem the OCPP endpoint without the charge point id, which is appended to it
 * @param chargePoints how many charge points to run and how they are named
 * @param callTimeout how long the central system may take to answer
 * @param reconnect the back-off before connecting again, see {@link ReconnectPolicy}
 * @param faults the faults the charge points produce on purpose, all off unless configured
 */
@ConfigurationProperties("chargehub.simulator")
record SimulatorProperties(
    @DefaultValue("ws://localhost:8080/ocpp") URI centralSystem,
    @DefaultValue ChargePoints chargePoints,
    @DefaultValue("30s") Duration callTimeout,
    @DefaultValue Reconnect reconnect,
    @DefaultValue Faults faults) {

  /**
   * @param count how many charge points run, each on its own connection
   * @param idPrefix the ids are this prefix and a number: SIM-0001, SIM-0002 and so on
   * @param startSpread each charge point connects first after a random wait of up to this long, so
   *     a large simulator does not open all its connections in the same second
   */
  record ChargePoints(
      @DefaultValue("1") int count,
      @DefaultValue("SIM-") String idPrefix,
      @DefaultValue("5s") Duration startSpread) {

    ChargePoints {
      if (count < 1) {
        throw new IllegalArgumentException("count must be at least 1, got " + count);
      }
      if (startSpread.isNegative()) {
        throw new IllegalArgumentException("startSpread must not be negative");
      }
    }

    List<String> ids() {
      return IntStream.rangeClosed(1, count)
          .mapToObj(n -> "%s%04d".formatted(idPrefix, n))
          .toList();
    }
  }

  /** With these defaults the wait grows from 2s to 64s, plus up to 10s at random. */
  record Reconnect(
      @DefaultValue("2s") Duration waitMinimum,
      @DefaultValue("10s") Duration randomRange,
      @DefaultValue("5") int repeatTimes) {

    ReconnectPolicy policy() {
      return new ReconnectPolicy(waitMinimum, randomRange, repeatTimes);
    }
  }

  /**
   * @param meanTimeBetweenDrops see {@link FaultPolicy}; unset, connections never drop
   */
  record Faults(@Nullable Duration meanTimeBetweenDrops) {

    FaultPolicy policy() {
      return new FaultPolicy(meanTimeBetweenDrops);
    }
  }
}
