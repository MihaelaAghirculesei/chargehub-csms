package io.github.mihaelaaghirculesei.simulator.chargepoint;

import io.github.mihaelaaghirculesei.ocpp.message.AuthorizationStatus;
import io.github.mihaelaaghirculesei.ocpp.message.AuthorizeRequest;
import io.github.mihaelaaghirculesei.ocpp.message.AuthorizeResponse;
import io.github.mihaelaaghirculesei.ocpp.message.ChargePointStatus;
import io.github.mihaelaaghirculesei.ocpp.message.MeterValue;
import io.github.mihaelaaghirculesei.ocpp.message.MeterValuesRequest;
import io.github.mihaelaaghirculesei.ocpp.message.Reason;
import io.github.mihaelaaghirculesei.ocpp.message.SampledValue;
import io.github.mihaelaaghirculesei.ocpp.message.StartTransactionRequest;
import io.github.mihaelaaghirculesei.ocpp.message.StartTransactionResponse;
import io.github.mihaelaaghirculesei.ocpp.message.StatusNotificationRequest;
import io.github.mihaelaaghirculesei.ocpp.message.StopTransactionRequest;
import io.github.mihaelaaghirculesei.ocpp.message.StopTransactionResponse;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The charging sessions of one charge point, one step at a time. A driver presents the tag, plugs
 * in, charges, presents the tag again and unplugs. Each step sends one message and says how long to
 * wait before the next one; the charge point decides when that is. While charging, the energy
 * register is sent at each interval of the plan. Not thread-safe.
 *
 * <p>A transaction message, readings of the transaction included, moves the session on only once it
 * is answered, so a step that fails is sent again when the charge point is connected again, as OCPP
 * 1.6 asks. A status report moves the connector on before it is sent: if it is lost, the report of
 * all connectors after the reconnection carries it.
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
    METER,
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
  private long meterStart;
  // The charging time covered by the readings sent so far in this session.
  private Duration charged = Duration.ZERO;
  // A reading built but not answered yet: sent again as it is, with the time it was taken.
  private @Nullable MeterValuesRequest reading;

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
      case CHARGE -> {
        charged = Duration.ZERO;
        Step next = afterReading();
        yield report(link, ChargePointStatus.CHARGING, next, waitBefore(next));
      }
      case METER -> meter(link);
      case FINISH -> {
        deliverUntil(meterStart + plan.energyWattHours());
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
    meterStart = connectors.register(connectorId);
    StartTransactionResponse answer =
        link.call(
            Action.START_TRANSACTION,
            new StartTransactionRequest(
                connectorId, plan.idTag(), meterStart, null, clock.instant()),
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

  private Duration meter(Link link) throws InterruptedException {
    MeterValuesRequest pending = reading;
    if (pending == null) {
      long register = deliverUntil(meterStart + plan.energyWattHours(nextReading()));
      pending =
          new MeterValuesRequest(
              connectorId,
              transactionId,
              List.of(
                  new MeterValue(
                      clock.instant(),
                      List.of(
                          new SampledValue(
                              Long.toString(register),
                              "Sample.Periodic",
                              null,
                              "Energy.Active.Import.Register",
                              null,
                              null,
                              "Wh")))));
      reading = pending;
    }
    // MeterValues.conf has no fields, so there is nothing to read from the answer.
    link.call(Action.METER_VALUES, pending, ObjectNode.class);
    reading = null;
    charged = nextReading();
    step = afterReading();
    return waitBefore(step);
  }

  private Duration nextReading() {
    return charged.plus(plan.meterValueInterval());
  }

  // A reading at the very end would only repeat meterStop.
  private Step afterReading() {
    return nextReading().compareTo(plan.duration()) < 0 ? Step.METER : Step.FINISH;
  }

  private Duration waitBefore(Step next) {
    return next == Step.METER ? plan.meterValueInterval() : plan.duration().minus(charged);
  }

  // The register follows the charging time, so rounding to whole Wh never adds up across readings.
  private long deliverUntil(long register) {
    connectors.deliver(connectorId, register - connectors.register(connectorId));
    return register;
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
