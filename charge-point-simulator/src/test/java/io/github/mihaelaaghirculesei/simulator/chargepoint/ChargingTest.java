package io.github.mihaelaaghirculesei.simulator.chargepoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mihaelaaghirculesei.ocpp.message.AuthorizationStatus;
import io.github.mihaelaaghirculesei.ocpp.message.AuthorizeResponse;
import io.github.mihaelaaghirculesei.ocpp.message.ChargePointStatus;
import io.github.mihaelaaghirculesei.ocpp.message.IdTagInfo;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class ChargingTest {

  private static final Instant NOW = Instant.parse("2026-11-26T08:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  // 11 kW for 30 minutes delivers 5500 Wh, with readings after 10 and 20 minutes.
  private static final SessionPlan PLAN =
      new SessionPlan(
          "SIM-TAG", Duration.ofMinutes(30), Duration.ofMinutes(1), 11_000, Duration.ofMinutes(10));

  private final Connectors connectors = new Connectors(2);
  private final Charging charging = new Charging("CP-1", PLAN, connectors, CLOCK);
  private final FakeLink link = new FakeLink();

  @Test
  void waitsTheIdleTimeBeforeTheFirstSession() {
    assertThat(charging.firstWait()).isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void runsASessionFromAuthorizeToAvailable() throws InterruptedException {
    List<Duration> waits = steps(10);

    assertThat(link.sent)
        .containsExactly(
            "Authorize",
            "PREPARING",
            "StartTransaction",
            "CHARGING",
            "MeterValues",
            "MeterValues",
            "FINISHING",
            "StopTransaction",
            "AVAILABLE",
            "Authorize");
    assertThat(waits)
        .containsExactly(
            Duration.ZERO,
            Duration.ZERO,
            Duration.ZERO,
            Duration.ofMinutes(10),
            Duration.ofMinutes(10),
            Duration.ofMinutes(10),
            Duration.ZERO,
            Duration.ZERO,
            Duration.ofMinutes(1),
            Duration.ZERO);
    StartTransactionRequest start = link.starts.getFirst();
    assertThat(start.connectorId()).isEqualTo(1);
    assertThat(start.idTag()).isEqualTo("SIM-TAG");
    assertThat(start.meterStart()).isZero();
    assertThat(start.timestamp()).isEqualTo(NOW);
    StopTransactionRequest stop = link.stops.getFirst();
    assertThat(stop.transactionId()).isEqualTo(1);
    assertThat(stop.meterStop()).isEqualTo(5500);
    assertThat(stop.reason()).isEqualTo(Reason.LOCAL);
    assertThat(stop.idTag()).isEqualTo("SIM-TAG");
  }

  @Test
  void startsTheNextSessionWhereTheRegisterStopped() throws InterruptedException {
    steps(18);

    assertThat(link.starts)
        .extracting(StartTransactionRequest::meterStart)
        .containsExactly(0L, 5500L);
    assertThat(link.stops)
        .extracting(StopTransactionRequest::meterStop)
        .containsExactly(5500L, 11_000L);
    assertThat(link.stops).extracting(StopTransactionRequest::transactionId).containsExactly(1, 2);
    assertThat(link.readings)
        .extracting(ChargingTest::energy)
        .containsExactly("1833", "3666", "7333", "9166");
  }

  @Test
  void sendsTheEnergyRegisterOfTheTransactionWhileCharging() throws InterruptedException {
    steps(9);

    assertThat(link.readings).hasSize(2);
    MeterValuesRequest reading = link.readings.getFirst();
    assertThat(reading.connectorId()).isEqualTo(1);
    assertThat(reading.transactionId()).isEqualTo(1);
    MeterValue meterValue = reading.meterValue().getFirst();
    assertThat(meterValue.timestamp()).isEqualTo(NOW);
    assertThat(meterValue.sampledValue())
        .containsExactly(
            new SampledValue(
                "1833",
                "Sample.Periodic",
                null,
                "Energy.Active.Import.Register",
                null,
                null,
                "Wh"));
    long meterStop = link.stops.getFirst().meterStop();
    assertThat(link.readings)
        .extracting(r -> Long.parseLong(energy(r)))
        .isSorted()
        .allSatisfy(wh -> assertThat(wh).isLessThan(meterStop));
  }

  @Test
  void sendsNoReadingInASessionShorterThanTheInterval() throws InterruptedException {
    Charging shortSessions =
        new Charging(
            "CP-1",
            new SessionPlan(
                "SIM-TAG",
                Duration.ofMinutes(5),
                Duration.ofMinutes(1),
                11_000,
                Duration.ofMinutes(10)),
            connectors,
            CLOCK);

    List<Duration> waits = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      waits.add(shortSessions.step(link));
    }

    assertThat(link.sent).doesNotContain("MeterValues").endsWith("StopTransaction", "AVAILABLE");
    assertThat(waits).contains(Duration.ofMinutes(5));
  }

  @Test
  void sendsAReadingAgainAsItWasTaken() throws InterruptedException {
    MovingClock clock = new MovingClock();
    Charging charging = new Charging("CP-1", PLAN, connectors, clock);
    for (int i = 0; i < 4; i++) {
      charging.step(link);
    }
    link.failNextCall = true;

    assertThatThrownBy(() -> charging.step(link)).isInstanceOf(SessionFailedException.class);
    clock.now = NOW.plus(Duration.ofMinutes(5));
    charging.step(link);

    assertThat(link.readings).extracting(ChargingTest::energy).containsExactly("1833");
    assertThat(link.readings.getFirst().meterValue().getFirst().timestamp()).isEqualTo(NOW);
    assertThat(connectors.register(1)).isEqualTo(1833);
  }

  @Test
  void aRejectedTagEndsTheAttemptWithoutATransaction() throws InterruptedException {
    link.authorizeAnswer = AuthorizationStatus.INVALID;

    assertThat(steps(2)).containsExactly(Duration.ofMinutes(1), Duration.ofMinutes(1));
    assertThat(link.sent).containsExactly("Authorize", "Authorize");
    assertThat(connectors.free()).hasValue(1);
  }

  @Test
  void stopsATransactionTheCentralSystemDidNotAccept() throws InterruptedException {
    link.startAnswer = AuthorizationStatus.CONCURRENT_TX;

    steps(5);

    assertThat(link.sent)
        .containsExactly(
            "Authorize", "PREPARING", "StartTransaction", "StopTransaction", "AVAILABLE");
    StopTransactionRequest stop = link.stops.getFirst();
    assertThat(stop.reason()).isEqualTo(Reason.DE_AUTHORIZED);
    assertThat(stop.meterStop()).isZero();
  }

  @Test
  void waitsWhenNoConnectorIsFree() throws InterruptedException {
    connectors.use(1, ChargePointStatus.CHARGING, NOW);
    connectors.use(2, ChargePointStatus.CHARGING, NOW);

    assertThat(charging.step(link)).isEqualTo(Duration.ofMinutes(1));
    assertThat(link.sent).isEmpty();
  }

  @Test
  void sendsATransactionMessageAgainUntilItIsAnswered() throws InterruptedException {
    steps(2);
    link.failNextCall = true;

    assertThatThrownBy(() -> charging.step(link)).isInstanceOf(SessionFailedException.class);
    charging.step(link);

    assertThat(link.sent).containsExactly("Authorize", "PREPARING", "StartTransaction");
    assertThat(link.starts).hasSize(1);
  }

  @Test
  void movesTheConnectorOnEvenWhenItsReportIsLost() throws InterruptedException {
    steps(1);
    link.failNextReport = true;

    assertThatThrownBy(() -> charging.step(link)).isInstanceOf(SessionFailedException.class);

    assertThat(connectors.reports(NOW).getFirst().status()).isEqualTo(ChargePointStatus.PREPARING);
    charging.step(link);
    assertThat(link.sent).containsExactly("Authorize", "StartTransaction");
  }

  @Test
  void refusesAPlanThatCannotCharge() {
    assertThatThrownBy(() ->
            new SessionPlan(
                "", Duration.ofMinutes(1), Duration.ofMinutes(1), 1, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new SessionPlan(
                "X".repeat(21),
                Duration.ofMinutes(1),
                Duration.ofMinutes(1),
                1,
                Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new SessionPlan("TAG", Duration.ZERO, Duration.ofMinutes(1), 1, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new SessionPlan("TAG", Duration.ofMinutes(1), Duration.ZERO, 1, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new SessionPlan(
                "TAG", Duration.ofMinutes(1), Duration.ofMinutes(1), 0, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
            new SessionPlan("TAG", Duration.ofMinutes(1), Duration.ofMinutes(1), 1, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static String energy(MeterValuesRequest reading) {
    return reading.meterValue().getFirst().sampledValue().getFirst().value();
  }

  private List<Duration> steps(int count) throws InterruptedException {
    List<Duration> waits = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      waits.add(charging.step(link));
    }
    return waits;
  }

  /** Answers like a central system that numbers transactions from 1, and records what it got. */
  private static final class FakeLink implements Charging.Link {

    private final List<String> sent = new ArrayList<>();
    private final List<StartTransactionRequest> starts = new ArrayList<>();
    private final List<StopTransactionRequest> stops = new ArrayList<>();
    private final List<MeterValuesRequest> readings = new ArrayList<>();
    private AuthorizationStatus authorizeAnswer = AuthorizationStatus.ACCEPTED;
    private AuthorizationStatus startAnswer = AuthorizationStatus.ACCEPTED;
    private boolean failNextCall;
    private boolean failNextReport;

    @Override
    public <T> T call(Action action, Object request, Class<T> responseType) {
      if (failNextCall) {
        failNextCall = false;
        throw new SessionFailedException(action + " failed", null);
      }
      sent.add(action.wireName());
      Object answer =
          switch (request) {
            case StartTransactionRequest start -> {
              starts.add(start);
              yield new StartTransactionResponse(info(startAnswer), starts.size());
            }
            case StopTransactionRequest stop -> {
              stops.add(stop);
              yield new StopTransactionResponse(info(AuthorizationStatus.ACCEPTED));
            }
            case MeterValuesRequest reading -> {
              readings.add(reading);
              yield JsonNodeFactory.instance.objectNode();
            }
            default -> new AuthorizeResponse(info(authorizeAnswer));
          };
      return responseType.cast(answer);
    }

    @Override
    public void report(StatusNotificationRequest report) {
      if (failNextReport) {
        failNextReport = false;
        throw new SessionFailedException("StatusNotification failed", null);
      }
      sent.add(report.status().name());
    }

    private static IdTagInfo info(AuthorizationStatus status) {
      return new IdTagInfo(status, null, null);
    }
  }

  /** A clock the test moves by hand. */
  private static final class MovingClock extends Clock {

    private Instant now = NOW;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
