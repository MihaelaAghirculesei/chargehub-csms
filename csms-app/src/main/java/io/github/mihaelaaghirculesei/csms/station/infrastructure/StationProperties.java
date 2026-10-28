package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param heartbeatInterval what an accepted charge point is told to use. Shorter detects a lost
 *     charge point sooner, at the cost of more traffic from every charge point.
 * @param offlineCheckInterval how often each instance of the central system looks for stations that
 *     went silent.
 * @param missedHeartbeats how many heartbeat intervals a station may stay silent before it is taken
 *     as offline. More than one, so that a single late heartbeat is not taken as an outage.
 */
@ConfigurationProperties("chargehub.station")
record StationProperties(
    @DefaultValue("300s") Duration heartbeatInterval,
    @DefaultValue("30s") Duration offlineCheckInterval,
    @DefaultValue("3") int missedHeartbeats) {

  StationProperties {
    if (missedHeartbeats < 1) {
      throw new IllegalArgumentException("Missed heartbeats must be at least 1");
    }
  }

  /** How long a station may stay silent before it is taken as offline. */
  Duration silenceLimit() {
    return heartbeatInterval.multipliedBy(missedHeartbeats);
  }
}
