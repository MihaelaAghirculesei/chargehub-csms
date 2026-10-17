package io.github.mihaelaaghirculesei.csms.ocpp.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param callTimeout how long a charge point may take to answer a call. Short enough for an
 *     operator waiting on a command, long enough for a charge point on a slow mobile network.
 */
@ConfigurationProperties("chargehub.ocpp")
record OcppProperties(@DefaultValue("30s") Duration callTimeout) {}
