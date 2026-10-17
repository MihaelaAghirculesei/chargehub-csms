package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param heartbeatInterval what an accepted charge point is told to use. Shorter detects a lost
 *     charge point sooner, at the cost of more traffic from every charge point.
 */
@ConfigurationProperties("chargehub.station")
record StationProperties(@DefaultValue("300s") Duration heartbeatInterval) {}
