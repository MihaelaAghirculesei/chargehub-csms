package io.github.mihaelaaghirculesei.simulator;

import io.github.mihaelaaghirculesei.simulator.chargepoint.ChargePoint;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.context.SmartLifecycle;

/**
 * Runs the charge point on its own thread once the context is ready, and stops it with the context.
 * The thread is not a daemon: it keeps the application alive until the application is stopped.
 */
class ChargePointRunner implements SmartLifecycle {

  private final ChargePoint chargePoint;
  private final AtomicReference<@Nullable Thread> thread = new AtomicReference<>();

  ChargePointRunner(ChargePoint chargePoint) {
    this.chargePoint = chargePoint;
  }

  @Override
  public void start() {
    thread.set(Thread.ofPlatform().name("charge-point-" + chargePoint.id()).start(this::run));
  }

  private void run() {
    try {
      chargePoint.run();
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void stop() {
    Thread current = thread.get();
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
    Thread current = thread.get();
    return current != null && current.isAlive();
  }
}
