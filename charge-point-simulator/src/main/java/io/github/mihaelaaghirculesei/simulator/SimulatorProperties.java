package io.github.mihaelaaghirculesei.simulator;

import io.github.mihaelaaghirculesei.simulator.chargepoint.ReconnectPolicy;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param centralSystem the OCPP endpoint without the charge point id, which is appended to it
 * @param callTimeout how long the central system may take to answer
 * @param reconnect the back-off before connecting again, see {@link ReconnectPolicy}
 */
@ConfigurationProperties("chargehub.simulator")
record SimulatorProperties(
    @DefaultValue("ws://localhost:8080/ocpp") URI centralSystem,
    @DefaultValue("SIM-0001") String chargePointId,
    @DefaultValue("30s") Duration callTimeout,
    @DefaultValue Reconnect reconnect) {

  /** With these defaults the wait grows from 2s to 64s, plus up to 10s at random. */
  record Reconnect(
      @DefaultValue("2s") Duration waitMinimum,
      @DefaultValue("10s") Duration randomRange,
      @DefaultValue("5") int repeatTimes) {

    ReconnectPolicy policy() {
      return new ReconnectPolicy(waitMinimum, randomRange, repeatTimes);
    }
  }
}
