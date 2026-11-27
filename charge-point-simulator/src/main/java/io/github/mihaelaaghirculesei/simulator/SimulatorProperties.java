package io.github.mihaelaaghirculesei.simulator;

import io.github.mihaelaaghirculesei.simulator.chargepoint.FaultPolicy;
import io.github.mihaelaaghirculesei.simulator.chargepoint.ReconnectPolicy;
import io.github.mihaelaaghirculesei.simulator.chargepoint.SessionPlan;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.random.RandomGenerator;
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
 * @param sessions how the charge points charge, see {@link Sessions}
 */
@ConfigurationProperties("chargehub.simulator")
record SimulatorProperties(
    @DefaultValue("ws://localhost:8080/ocpp") URI centralSystem,
    @DefaultValue ChargePoints chargePoints,
    @DefaultValue("30s") Duration callTimeout,
    @DefaultValue Reconnect reconnect,
    @DefaultValue Faults faults,
    @DefaultValue Sessions sessions) {

  /**
   * @param count how many charge points run, each on its own connection
   * @param idPrefix the ids are this prefix and a number: SIM-0001, SIM-0002 and so on
   * @param connectors how many connectors each charge point has
   * @param startSpread each charge point connects first after a random wait of up to this long, so
   *     a large simulator does not open all its connections in the same second
   */
  record ChargePoints(
      @DefaultValue("1") int count,
      @DefaultValue("SIM-") String idPrefix,
      @DefaultValue("2") int connectors,
      @DefaultValue("5s") Duration startSpread) {

    ChargePoints {
      if (count < 1) {
        throw new IllegalArgumentException("count must be at least 1, got " + count);
      }
      if (connectors < 1) {
        throw new IllegalArgumentException("connectors must be at least 1, got " + connectors);
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
   * @param duplicateCallProbability see {@link FaultPolicy}
   * @param connectorFaultProbability see {@link FaultPolicy}
   * @param clockSkewMinimum the lowest offset of a charge point's clock from the real time
   * @param clockSkewMaximum the highest offset; equal to the minimum, every clock is off by exactly
   *     that much
   */
  record Faults(
      @Nullable Duration meanTimeBetweenDrops,
      @DefaultValue("0") double duplicateCallProbability,
      @DefaultValue("0") double connectorFaultProbability,
      @DefaultValue("0s") Duration clockSkewMinimum,
      @DefaultValue("0s") Duration clockSkewMaximum) {

    Faults {
      if (clockSkewMinimum.compareTo(clockSkewMaximum) > 0) {
        throw new IllegalArgumentException("clockSkewMinimum must not exceed clockSkewMaximum");
      }
    }

    FaultPolicy policy() {
      return new FaultPolicy(
          meanTimeBetweenDrops, duplicateCallProbability, connectorFaultProbability);
    }

    /** The offset for one charge point's clock, which it keeps for as long as it runs. */
    Duration clockSkew(RandomGenerator random) {
      long minimum = clockSkewMinimum.toMillis();
      long range = clockSkewMaximum.toMillis() - minimum;
      return Duration.ofMillis(minimum + random.nextLong(range + 1));
    }
  }

  /**
   * The charging sessions every charge point runs, one after the other. The defaults are those of
   * an 11 kW wall box.
   *
   * @param idTags the simulated RFID cards, given to the charge points in turn; empty, no charge
   *     point charges. The central system accepts only the tags it knows.
   * @param duration see {@link SessionPlan}
   * @param idleTime see {@link SessionPlan}
   * @param powerWatts see {@link SessionPlan}
   * @param meterValueInterval see {@link SessionPlan}
   */
  record Sessions(
      @DefaultValue List<String> idTags,
      @DefaultValue("5m") Duration duration,
      @DefaultValue("1m") Duration idleTime,
      @DefaultValue("11000") long powerWatts,
      @DefaultValue("1m") Duration meterValueInterval) {

    // A wrong setting stops the simulator at startup: each charge point gets its plan there.
    Sessions {
      idTags = List.copyOf(idTags);
    }

    /** The plan of the charge point at the given position, from 0, or null when none charges. */
    @Nullable SessionPlan plan(int chargePoint) {
      if (idTags.isEmpty()) {
        return null;
      }
      return new SessionPlan(
          idTags.get(chargePoint % idTags.size()),
          duration,
          idleTime,
          powerWatts,
          meterValueInterval);
    }
  }
}
