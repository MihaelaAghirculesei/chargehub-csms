package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param clockGapThreshold how far the station time of a start or stop may be from its receive time
 *     before the session is flagged. Shorter flags slow networks and retries; longer hides real
 *     clock errors, see docs/adr/0017.
 */
@ConfigurationProperties("chargehub.session")
record SessionProperties(@DefaultValue("5m") Duration clockGapThreshold) {

  SessionProperties {
    if (clockGapThreshold.isNegative() || clockGapThreshold.isZero()) {
      throw new IllegalArgumentException("The clock gap threshold must be positive");
    }
  }
}
