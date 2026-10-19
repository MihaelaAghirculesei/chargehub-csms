package io.github.mihaelaaghirculesei.simulator;

import io.github.mihaelaaghirculesei.simulator.chargepoint.ChargePoint;
import io.github.mihaelaaghirculesei.simulator.chargepoint.SessionFailedException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs the charge point on its own thread once the context is ready, and stops it with the context.
 * The thread is not a daemon: it keeps the application alive while the charge point runs, and the
 * application ends when the session fails.
 */
class ChargePointRunner implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(ChargePointRunner.class);

  private final ChargePoint chargePoint;
  private volatile @Nullable Thread thread;

  ChargePointRunner(ChargePoint chargePoint) {
    this.chargePoint = chargePoint;
  }

  @Override
  public void start() {
    thread = Thread.ofPlatform().name("charge-point-" + chargePoint.id()).start(this::run);
  }

  private void run() {
    try {
      chargePoint.run();
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    } catch (SessionFailedException e) {
      log.error("Charge point {} stopped", chargePoint.id(), e);
    }
  }

  @Override
  public void stop() {
    Thread current = thread;
    if (current != null) {
      current.interrupt();
      try {
        current.join();
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public boolean isRunning() {
    Thread current = thread;
    return current != null && current.isAlive();
  }
}
