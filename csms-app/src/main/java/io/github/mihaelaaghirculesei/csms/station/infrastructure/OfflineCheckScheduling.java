package io.github.mihaelaaghirculesei.csms.station.infrastructure;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/** Runs the offline check on every instance; the check itself lets only one of them act. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class OfflineCheckScheduling implements SchedulingConfigurer {

  private final OfflineStationCheck check;
  private final Duration interval;

  OfflineCheckScheduling(OfflineStationCheck check, StationProperties properties) {
    this.check = check;
    this.interval = properties.offlineCheckInterval();
  }

  // The first run waits one interval too, so a long interval keeps the check from running at all.
  // Tests rely on that to run it themselves.
  @Override
  public void configureTasks(ScheduledTaskRegistrar registrar) {
    registrar.addFixedDelayTask(new FixedDelayTask(check::run, interval, interval));
  }
}
