package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.ocpp.infrastructure.TestChargePoint;
import io.github.mihaelaaghirculesei.csms.session.SessionStarted;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** StartTransaction from the WebSocket frame to the database and back. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, StartTransactionTest.RecordedStarts.class})
class StartTransactionTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final String STARTED_AT = "2026-11-24T10:00:00Z";

  @LocalServerPort private int port;
  @Autowired private JdbcClient jdbc;
  @Autowired private RecordedStarts starts;

  @Test
  void storesTheSessionAndAnswersWithItsTransactionId() throws Exception {
    insertTag("START-OK", "ACCEPTED");

    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-START-OK")) {
      JsonNode answer = start(chargePoint, "1", 1, "start-ok");

      assertThat(answer.get("idTagInfo").get("status").stringValue()).isEqualTo("Accepted");
      int transactionId = answer.get("transactionId").intValue();
      assertThat(tagStatusOf(transactionId)).isEqualTo("ACCEPTED");
      assertThat(starts.of("CP-START-OK"))
          .containsExactly(
              new SessionStarted(
                  transactionId, "CP-START-OK", 1, "START-OK", true, Instant.parse(STARTED_AT)));
    }
  }

  @Test
  void answersConcurrentTxForATagThatIsAlreadyCharging() throws Exception {
    insertTag("START-BUSY", "ACCEPTED");

    try (TestChargePoint first = new TestChargePoint(port, "CP-START-BUSY-1");
        TestChargePoint second = new TestChargePoint(port, "CP-START-BUSY-2")) {
      int charging = start(first, "1", 1, "START-BUSY").get("transactionId").intValue();
      JsonNode answer = start(second, "1", 1, "START-BUSY");

      assertThat(answer.get("idTagInfo").get("status").stringValue()).isEqualTo("ConcurrentTx");
      int refused = answer.get("transactionId").intValue();
      assertThat(refused).isNotEqualTo(charging);
      assertThat(tagStatusOf(refused)).isEqualTo("CONCURRENT_TX");
      assertThat(starts.of("CP-START-BUSY-2"))
          .singleElement()
          .extracting(SessionStarted::authorized)
          .isEqualTo(false);
    }
  }

  @Test
  void storesARefusedStartAndAnswersWithTheTagStatus() throws Exception {
    insertTag("START-BLOCKED", "BLOCKED");

    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-START-REFUSED")) {
      JsonNode blocked = start(chargePoint, "1", 1, "START-BLOCKED");
      JsonNode unknown = start(chargePoint, "2", 2, "START-UNKNOWN");

      assertThat(blocked.get("idTagInfo").get("status").stringValue()).isEqualTo("Blocked");
      assertThat(tagStatusOf(blocked.get("transactionId").intValue())).isEqualTo("BLOCKED");
      assertThat(unknown.get("idTagInfo").get("status").stringValue()).isEqualTo("Invalid");
      assertThat(tagStatusOf(unknown.get("transactionId").intValue())).isEqualTo("INVALID");
    }
  }

  private void insertTag(String idTag, String status) {
    jdbc.sql("insert into id_tag (id_tag, status) values (?, ?)").params(idTag, status).update();
  }

  private String tagStatusOf(int transactionId) {
    return jdbc.sql("select tag_status from charging_session where transaction_id = ?")
        .param(transactionId)
        .query(String.class)
        .single();
  }

  /** Sends a start and returns the payload of the CALLRESULT. */
  private static JsonNode start(
      TestChargePoint chargePoint, String messageId, int connectorId, String idTag)
      throws Exception {
    chargePoint.send(
        "[2,\""
            + messageId
            + "\",\"StartTransaction\",{\"connectorId\":"
            + connectorId
            + ",\"idTag\":\""
            + idTag
            + "\",\"meterStart\":1200,\"timestamp\":\""
            + STARTED_AT
            + "\"}]");
    JsonNode frame = JsonMapper.shared().readTree(String.valueOf(chargePoint.nextFrame(WAIT)));
    assertThat(frame.get(0).intValue()).as("a CALLRESULT: %s", frame).isEqualTo(3);
    return frame.get(2);
  }

  /**
   * Handlers run on the WebSocket threads, which {@code @RecordApplicationEvents} does not see, so
   * a listener collects the events instead. The context is shared, so each test reads its own
   * station.
   */
  @TestComponent
  static class RecordedStarts {

    private final List<SessionStarted> all = new CopyOnWriteArrayList<>();

    @EventListener
    void on(SessionStarted started) {
      all.add(started);
    }

    List<SessionStarted> of(String stationId) {
      return all.stream().filter(started -> started.stationId().equals(stationId)).toList();
    }
  }
}
