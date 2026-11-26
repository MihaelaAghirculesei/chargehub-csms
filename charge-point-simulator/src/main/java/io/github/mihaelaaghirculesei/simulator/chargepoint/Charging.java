package io.github.mihaelaaghirculesei.simulator.chargepoint;

import io.github.mihaelaaghirculesei.ocpp.message.AuthorizationStatus;
import io.github.mihaelaaghirculesei.ocpp.message.AuthorizeRequest;
import io.github.mihaelaaghirculesei.ocpp.message.AuthorizeResponse;
import io.github.mihaelaaghirculesei.ocpp.message.ChargePointStatus;
import io.github.mihaelaaghirculesei.ocpp.message.Reason;
import io.github.mihaelaaghirculesei.ocpp.message.StartTransactionRequest;
import io.github.mihaelaaghirculesei.ocpp.message.StartTransactionResponse;
import io.github.mihaelaaghirculesei.ocpp.message.StatusNotificationRequest;
import io.github.mihaelaaghirculesei.ocpp.message.StopTransactionRequest;
import io.github.mihaelaaghirculesei.ocpp.message.StopTransactionResponse;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import java.time.Clock;
import java.time.Duration;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The charging sessions of one charge point, one step at a time. A driver presents the tag, plugs
 * in, charges, presents the tag again and unplugs. Each step sends one message and says how long to
 * wait before the next one; the charge point decides when that is. Not thread-safe.
 *
 * <p>A transaction message moves the session on only once it is answered, so a step that fails is
 * sent again when the charge point is connected again, as OCPP 1.6 asks. A status report moves the
 * connector on before it is sent: if it is lost, the report of all connectors after the
 * reconnection carries it.
 */
final class Charging {

  /** How the steps reach the central system. */
  interface Link {
    <T> T call(Action action, Object request, Class<T> responseType) throws InterruptedException;

    void report(StatusNotificationRequest report) throws InterruptedException;
  }

  private enum Step {
    AUTHORIZE,
    PREPARE,
    START,
    CHARGE,
    FINISH,
    STOP,
    RELEASE
  }

  private static final Logger log = LoggerFactory.getLogger(Charging.class);

  private final String chargePointId;
  private final SessionPlan plan;
  private final Connectors connectors;
  private final Clock clock;
  private Step step = Step.AUTHORIZE;
  private int connectorId;
  private int transactionId;
  private Reason stopReason = Reason.LOCAL;

  /**
   * @param clock what the charge point believes the time is, for every timestamp it sends
   */
  Charging(String chargePointId, SessionPlan plan, Connectors connectors, Clock clock) {
    this.chargePointId = chargePointId;
    this.plan = plan;
    this.connectors = connectors;
    this.clock = clock;
  }

  /** The wait before the first step. */
  Duration firstWait() {
    return plan.idleTime();
  }

  /**
   * Sends the message of the current step and moves on.
   *
   * @return how long to wait before the next step
   * @throws SessionFailedException when the message gets no answer
   */
  Duration step(Link link) throws InterruptedException {
    return switch (step) {
      case AUTHORIZE -> authorize(link);
      case PREPARE -> report(link, ChargePointStatus.PREPARING, Step.START, Duration.ZERO);
      case START -> start(link);
      case CHARGE -> report(link, ChargePointStatus.CHARGING, Step.FINISH, plan.duration());
      case FINISH -> {
        connectors.deliver(connectorId, plan.energyWattHours());
        yield report(link, ChargePointStatus.FINISHING, Step.STOP, Duration.ZERO);
      }
      case STOP -> stop(link);
      case RELEASE -> {
        step = Step.AUTHORIZE;
        link.report(connectors.release(connectorId, clock.instant()));
        yield plan.idleTime();
      }
    };
  }

  private Duration authorize(Link link) throws InterruptedException {
    OptionalInt free = connectors.free();
    if (free.isEmpty()) {
      return plan.idleTime();
    }
    AuthorizeResponse answer =
        link.call(Action.AUTHORIZE, new AuthorizeRequest(plan.idTag()), AuthorizeResponse.class);
    AuthorizationStatus status = answer.idTagInfo().status();
    if (status != AuthorizationStatus.ACCEPTED) {
      log.info(
          "Charge point {} does not charge: tag {} is {}", chargePointId, plan.idTag(), status);
      return plan.idleTime();
    }
    connectorId = free.getAsInt();
    step = Step.PREPARE;
    return Duration.ZERO;
  }

  private Duration start(Link link) throws InterruptedException {
    StartTransactionResponse answer =
        link.call(
            Action.START_TRANSACTION,
            new StartTransactionRequest(
                connectorId, plan.idTag(), connectors.register(connectorId), null, clock.instant()),
            StartTransactionResponse.class);
    transactionId = answer.transactionId();
    AuthorizationStatus status = answer.idTagInfo().status();
    // The central system has opened the transaction whatever the status, so it must be stopped.
    if (status == AuthorizationStatus.ACCEPTED) {
      log.info(
          "Charge point {} charges on connector {} in transaction {}",
          chargePointId,
          connectorId,
          transactionId);
      stopReason = Reason.LOCAL;
      step = Step.CHARGE;
    } else {
      log.info(
          "Charge point {} stops transaction {} at once: tag {} is {}",
          chargePointId,
          transactionId,
          plan.idTag(),
          status);
      stopReason = Reason.DE_AUTHORIZED;
      step = Step.STOP;
    }
    return Duration.ZERO;
  }

  private Duration stop(Link link) throws InterruptedException {
    link.call(
        Action.STOP_TRANSACTION,
        new StopTransactionRequest(
            plan.idTag(),
            connectors.register(connectorId),
            clock.instant(),
            transactionId,
            stopReason,
            null),
        StopTransactionResponse.class);
    log.info("Charge point {} stopped transaction {}", chargePointId, transactionId);
    step = Step.RELEASE;
    return Duration.ZERO;
  }

  private Duration report(Link link, ChargePointStatus status, Step next, Duration wait)
      throws InterruptedException {
    step = next;
    link.report(connectors.use(connectorId, status, clock.instant()));
    return wait;
  }
}
