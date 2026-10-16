package io.github.mihaelaaghirculesei.ocpp.call;

import io.github.mihaelaaghirculesei.ocpp.frame.Call;
import io.github.mihaelaaghirculesei.ocpp.frame.CallError;
import io.github.mihaelaaghirculesei.ocpp.frame.CallResult;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * The outbound call each charge point is answering, at most one at a time as OCPP-J requires. A
 * second call is refused, not queued: see docs/adr/0009. Thread-safe.
 */
public final class PendingCalls {

  private final Duration timeout;
  private final ConcurrentMap<String, Pending> pending = new ConcurrentHashMap<>();

  public PendingCalls(Duration timeout) {
    if (timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Timeout must be positive, got " + timeout);
    }
    this.timeout = timeout;
  }

  /**
   * Records the call before it is sent. The future completes with the CALLRESULT, or fails with
   * {@link CallErrorException}, {@link java.util.concurrent.TimeoutException} or {@link
   * ConnectionClosedException}.
   *
   * @throws CallInProgressException if the charge point has not answered its previous call yet
   */
  public CompletableFuture<CallResult> begin(String chargePointId, Call call) {
    CompletableFuture<CallResult> result = new CompletableFuture<>();
    Pending entry = new Pending(call.messageId(), result);
    if (pending.putIfAbsent(chargePointId, entry) != null) {
      throw new CallInProgressException(chargePointId);
    }
    // The returned future completes only once the entry is gone, so a caller reacting to the
    // outcome can already send the next call.
    return result
        .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .whenComplete((answer, failure) -> pending.remove(chargePointId, entry));
  }

  /**
   * Completes the outstanding call this answer belongs to.
   *
   * @return false if no call was waiting for this message id, for example after a timeout
   */
  public boolean answer(String chargePointId, CallResult result) {
    Pending entry = waitingFor(chargePointId, result.messageId());
    return entry != null && entry.result().complete(result);
  }

  /**
   * Fails the outstanding call this error belongs to.
   *
   * @return false if no call was waiting for this message id, for example after a timeout
   */
  public boolean answer(String chargePointId, CallError error) {
    Pending entry = waitingFor(chargePointId, error.messageId());
    return entry != null && entry.result().completeExceptionally(new CallErrorException(error));
  }

  /** Fails the outstanding call at once when the connection closes, instead of at the timeout. */
  public void connectionClosed(String chargePointId) {
    Pending entry = pending.get(chargePointId);
    if (entry != null) {
      entry.result().completeExceptionally(new ConnectionClosedException(chargePointId));
    }
  }

  private @Nullable Pending waitingFor(String chargePointId, String messageId) {
    Pending entry = pending.get(chargePointId);
    return entry != null && entry.messageId().equals(messageId) ? entry : null;
  }

  private record Pending(String messageId, CompletableFuture<CallResult> result) {}
}
