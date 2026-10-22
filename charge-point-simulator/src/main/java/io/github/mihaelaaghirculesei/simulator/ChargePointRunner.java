package io.github.mihaelaaghirculesei.simulator;

import io.github.mihaelaaghirculesei.simulator.chargepoint.ChargePoint;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs each charge point on its own virtual thread once the context is ready, and stops them all
 * with the context. See docs/adr/0011.
 */
class ChargePointRunner implements SmartLifecycle {

  private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);
  private static final Logger log = LoggerFactory.getLogger(ChargePointRunner.class);

  private final List<ChargePoint> chargePoints;
  private final Duration startSpread;
  private final RandomGenerator random = RandomGenerator.getDefault();
  private final List<Thread> threads = new CopyOnWriteArrayList<>();

  ChargePointRunner(List<ChargePoint> chargePoints, Duration startSpread) {
    this.chargePoints = List.copyOf(chargePoints);
    this.startSpread = startSpread;
  }

  @Override
  public void start() {
    for (ChargePoint chargePoint : chargePoints) {
      Duration firstWait = Duration.ofMillis(random.nextLong(startSpread.toMillis() + 1));
      threads.add(
          Thread.ofVirtual()
              .name("charge-point-" + chargePoint.id())
              .start(() -> run(chargePoint, firstWait)));
    }
  }

  private static void run(ChargePoint chargePoint, Duration firstWait) {
    try {
      Thread.sleep(firstWait);
      chargePoint.run();
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }

  // Interrupts every charge point first, so they all close their connections at once. The wait is
  // bounded: a charge point that ignores the interrupt must not block the shutdown.
  @Override
  public void stop() {
    threads.forEach(Thread::interrupt);
    long deadline = System.nanoTime() + STOP_TIMEOUT.toNanos();
    try {
      for (Thread thread : threads) {
        long left = deadline - System.nanoTime();
        if (left > 0) {
          thread.join(Duration.ofNanos(left));
        }
      }
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
    threads.removeIf(thread -> !thread.isAlive());
    if (!threads.isEmpty()) {
      log.warn("{} charge points did not stop within {}", threads.size(), STOP_TIMEOUT);
    }
  }

  @Override
  public boolean isRunning() {
    return threads.stream().anyMatch(Thread::isAlive);
  }
}
