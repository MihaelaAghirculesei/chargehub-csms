package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.ocpp.infrastructure.TestChargePoint;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A charge point that was offline sends a whole session at once, hours after it happened. The clock
 * of the central system is fixed, so the receive time is known.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LateTransactionTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final Instant RECEIVED_AT = Instant.parse("2026-11-24T12:00:00Z");

  @TestBean private Clock clock;

  @LocalServerPort private int port;
  @Autowired private JdbcClient jdbc;

  static Clock clock() {
    return Clock.fixed(RECEIVED_AT, ZoneOffset.UTC);
  }

  @Test
  void acceptsAQueuedSessionAndKeepsBothTimes() throws Exception {
    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-LATE")) {
      int transactionId =
          call(
                  chargePoint,
                  "1",
                  "\"StartTransaction\",{\"connectorId\":1,\"idTag\":\"LATE-ANY\","
                      + "\"meterStart\":1200,\"timestamp\":\"2026-11-24T08:00:00Z\"}")
              .get("transactionId")
              .intValue();
      call(
          chargePoint,
          "2",
          "\"MeterValues\",{\"connectorId\":1,\"transactionId\":"
              + transactionId
              + ",\"meterValue\":[{\"timestamp\":\"2026-11-24T08:30:00Z\","
              + "\"sampledValue\":[{\"value\":\"5000\"}]}]}");
      call(
          chargePoint,
          "3",
          "\"StopTransaction\",{\"transactionId\":"
              + transactionId
              + ",\"meterStop\":9200,\"timestamp\":\"2026-11-24T09:00:00Z\"}");

      assertThat(timesOf(transactionId))
          .containsEntry("started_at", "2026-11-24T08:00:00Z")
          .containsEntry("start_received_at", clock.instant().toString())
          .containsEntry("stopped_at", "2026-11-24T09:00:00Z")
          .containsEntry("stop_received_at", clock.instant().toString())
          .containsEntry("sampled_at", "2026-11-24T08:30:00Z")
          .containsEntry("received_at", clock.instant().toString());
      assertThat(clockGapFlagged(transactionId)).isTrue();
    }
  }

  @Test
  void flagsASessionStartedByAClockThatRunsAhead() throws Exception {
    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-AHEAD")) {
      int transactionId = start(chargePoint, "2026-11-24T13:00:00Z");

      assertThat(clockGapFlagged(transactionId)).isTrue();
    }
  }

  @Test
  void flagsASessionWhoseStopComesWithAWrongClock() throws Exception {
    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-STOP-AHEAD")) {
      int transactionId = start(chargePoint, "2026-11-24T11:59:00Z");
      assertThat(clockGapFlagged(transactionId)).isFalse();

      call(
          chargePoint,
          "2",
          "\"StopTransaction\",{\"transactionId\":"
              + transactionId
              + ",\"meterStop\":9200,\"timestamp\":\"2026-11-25T12:00:00Z\"}");

      assertThat(clockGapFlagged(transactionId)).isTrue();
    }
  }

  @Test
  void leavesASessionWithinTheThresholdUnflagged() throws Exception {
    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-ON-TIME")) {
      int transactionId = start(chargePoint, "2026-11-24T11:56:00Z");
      call(
          chargePoint,
          "2",
          "\"StopTransaction\",{\"transactionId\":"
              + transactionId
              + ",\"meterStop\":9200,\"timestamp\":\"2026-11-24T12:04:00Z\"}");

      assertThat(clockGapFlagged(transactionId)).isFalse();
    }
  }

  private static int start(TestChargePoint chargePoint, String timestamp) throws Exception {
    return call(
            chargePoint,
            "1",
            "\"StartTransaction\",{\"connectorId\":1,\"idTag\":\"LATE-ANY\","
                + "\"meterStart\":1200,\"timestamp\":\""
                + timestamp
                + "\"}")
        .get("transactionId")
        .intValue();
  }

  private boolean clockGapFlagged(int transactionId) {
    return jdbc.sql("select clock_gap_flagged from charging_session where transaction_id = ?")
        .param(transactionId)
        .query(Boolean.class)
        .single();
  }

  private Map<String, Object> timesOf(int transactionId) {
    return jdbc.sql(
            "select "
                + utc("s.started_at")
                + ", "
                + utc("s.start_received_at")
                + ", "
                + utc("s.stopped_at")
                + ", "
                + utc("s.stop_received_at")
                + ", "
                + utc("m.sampled_at")
                + ", "
                + utc("m.received_at")
                + " from charging_session s join meter_value m using (transaction_id)"
                + " where s.transaction_id = ?")
        .param(transactionId)
        .query()
        .singleRow();
  }

  /** A time column as UTC text, named after the column. */
  private static String utc(String column) {
    return "to_char("
        + column
        + " at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') as "
        + column.substring(column.indexOf('.') + 1);
  }

  /** Sends a call and returns the payload of the CALLRESULT. */
  private static JsonNode call(
      TestChargePoint chargePoint, String messageId, String actionAndPayload) throws Exception {
    chargePoint.send("[2,\"" + messageId + "\"," + actionAndPayload + "]");
    JsonNode frame = JsonMapper.shared().readTree(String.valueOf(chargePoint.nextFrame(WAIT)));
    assertThat(frame.get(0).intValue()).as("a CALLRESULT: %s", frame).isEqualTo(3);
    return frame.get(2);
  }
}
