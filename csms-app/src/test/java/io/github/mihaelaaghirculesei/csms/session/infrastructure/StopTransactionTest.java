package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.ocpp.infrastructure.TestChargePoint;
import io.github.mihaelaaghirculesei.csms.session.SessionCompleted;
import io.github.mihaelaaghirculesei.csms.session.StopReason;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** StopTransaction from the WebSocket frame to the database and back. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, StopTransactionTest.RecordedStops.class})
class StopTransactionTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final String STARTED_AT = "2026-11-24T10:00:00Z";
  private static final String STOPPED_AT = "2026-11-24T11:00:00Z";

  @LocalServerPort private int port;
  @Autowired private JdbcClient jdbc;
  @Autowired private RecordedStops stops;
  @Autowired private StopTransactionHandler handler;

  @Test
  void closesTheSessionAndPublishesItsCompletion() throws Exception {
    insertTag("STOP-OK", "ACCEPTED");

    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-STOP-OK")) {
      int transactionId = start(chargePoint, "STOP-OK");
      JsonNode answer =
          call(
              chargePoint,
              "2",
              stop(transactionId, ",\"idTag\":\"stop-ok\",\"reason\":\"Remote\""));

      assertThat(answer.get("idTagInfo").get("status").stringValue()).isEqualTo("Accepted");
      assertThat(stopOf(transactionId))
          .containsEntry("meter_stop", 9200L)
          .containsEntry("stop_reason", "REMOTE")
          .containsEntry("stop_id_tag", "STOP-OK");
      assertThat(stops.of("CP-STOP-OK"))
          .containsExactly(
              new SessionCompleted(
                  transactionId,
                  "CP-STOP-OK",
                  1,
                  "STOP-OK",
                  true,
                  1200,
                  9200,
                  Instant.parse(STARTED_AT),
                  Instant.parse(STOPPED_AT),
                  StopReason.REMOTE));
    }
  }

  @Test
  void storesAMissingReasonAsLocalAndAnswersWithoutTagInfo() throws Exception {
    insertTag("STOP-LOCAL", "ACCEPTED");

    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-STOP-LOCAL")) {
      int transactionId = start(chargePoint, "STOP-LOCAL");
      JsonNode answer = call(chargePoint, "2", stop(transactionId, ""));

      assertThat(answer.propertyNames()).isEmpty();
      assertThat(stopOf(transactionId))
          .containsEntry("stop_reason", "LOCAL")
          .containsEntry("stop_id_tag", null);
    }
  }

  @Test
  void aStoppedTagMayStartAgain() throws Exception {
    insertTag("STOP-AGAIN", "ACCEPTED");

    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-STOP-AGAIN")) {
      call(chargePoint, "2", stop(start(chargePoint, "STOP-AGAIN"), ""));

      JsonNode again = call(chargePoint, "3", startFrame("STOP-AGAIN"));
      assertThat(again.get("idTagInfo").get("status").stringValue()).isEqualTo("Accepted");
    }
  }

  @Test
  void answersAStopForAnUnknownTransactionWithoutError() throws Exception {
    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-STOP-UNKNOWN")) {
      JsonNode answer = call(chargePoint, "1", stop(-1, ",\"idTag\":\"STOP-NOBODY\""));

      assertThat(answer.get("idTagInfo").get("status").stringValue()).isEqualTo("Invalid");
    }
  }

  @Test
  void neverStopsTheSessionOfAnotherChargePoint() throws Exception {
    insertTag("STOP-OWNER", "ACCEPTED");

    try (TestChargePoint owner = new TestChargePoint(port, "CP-STOP-OWNER");
        TestChargePoint other = new TestChargePoint(port, "CP-STOP-OTHER")) {
      int transactionId = start(owner, "STOP-OWNER");
      call(other, "1", stop(transactionId, ""));

      assertThat(stopOf(transactionId)).containsEntry("stopped_at", null);
      assertThat(stops.of("CP-STOP-OWNER")).isEmpty();
    }
  }

  @Test
  void keepsTheFirstOfTwoStops() throws Exception {
    insertTag("STOP-TWICE", "ACCEPTED");

    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-STOP-TWICE")) {
      int transactionId = start(chargePoint, "STOP-TWICE");
      call(chargePoint, "2", stop(transactionId, ",\"reason\":\"EVDisconnected\""));
      call(chargePoint, "3", stop(transactionId, ",\"reason\":\"Other\""));

      assertThat(stopOf(transactionId)).containsEntry("stop_reason", "EV_DISCONNECTED");
      assertThat(stops.of("CP-STOP-TWICE")).hasSize(1);
    }
  }

  @Test
  void storesTheTransactionDataOfTheFirstStopOnly() throws Exception {
    insertTag("STOP-DATA", "ACCEPTED");
    String transactionData =
        ",\"transactionData\":[{\"timestamp\":\"2026-11-24T10:30:00Z\","
            + "\"sampledValue\":[{\"value\":\"5000\"},{\"value\":\"7.4\","
            + "\"measurand\":\"Power.Active.Import\",\"unit\":\"kW\"}]}]";

    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-STOP-DATA")) {
      int transactionId = start(chargePoint, "STOP-DATA");
      call(chargePoint, "2", stop(transactionId, transactionData));
      call(chargePoint, "3", stop(transactionId, transactionData));

      assertThat(
              jdbc.sql(
                      "select value || ' ' || unit from meter_value where transaction_id = ?"
                          + " order by id")
                  .param(transactionId)
                  .query(String.class)
                  .list())
          .containsExactly("5000 Wh", "7.4 kW");
    }
  }

  /**
   * Two stops handled at the same moment. The version of the session lets one commit; the other
   * either fails, and its charge point resends it, or sees the session already stopped.
   */
  @Test
  void storesOneStopForTwoStopsHandledAtTheSameMoment() throws Exception {
    insertTag("STOP-RACE", "ACCEPTED");
    int transactionId;
    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-STOP-RACE")) {
      transactionId = start(chargePoint, "STOP-RACE");
    }
    CountDownLatch ready = new CountDownLatch(2);
    List<Callable<ObjectNode>> stopsAtOnce = new ArrayList<>();
    for (String reason : List.of("Remote", "EVDisconnected")) {
      ObjectNode request =
          (ObjectNode)
              JsonMapper.shared()
                  .readTree(
                      stop(transactionId, ",\"reason\":\"" + reason + "\"")
                          .substring("\"StopTransaction\",".length()));
      stopsAtOnce.add(() -> {
        ready.countDown();
        ready.await();
        return handler.handle("CP-STOP-RACE", request);
      });
    }

    int answered = 0;
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      List<Future<ObjectNode>> results = new ArrayList<>();
      stopsAtOnce.forEach(stopAtOnce -> results.add(pool.submit(stopAtOnce)));
      for (Future<ObjectNode> result : results) {
        try {
          result.get();
          answered++;
        } catch (ExecutionException lostTheRace) {
          // Answered with an error by the dispatcher; the charge point resends it.
        }
      }
    }

    assertThat(answered).isPositive();
    assertThat(stops.of("CP-STOP-RACE")).hasSize(1);
    assertThat(stopOf(transactionId).get("stop_reason"))
        .isEqualTo(stops.of("CP-STOP-RACE").getFirst().reason().name());
  }

  private void insertTag(String idTag, String status) {
    jdbc.sql("insert into id_tag (id_tag, status) values (?, ?)").params(idTag, status).update();
  }

  private Map<String, Object> stopOf(int transactionId) {
    return jdbc.sql(
            "select meter_stop, stopped_at, stop_reason, stop_id_tag from charging_session"
                + " where transaction_id = ?")
        .param(transactionId)
        .query()
        .singleRow();
  }

  private static int start(TestChargePoint chargePoint, String idTag) throws Exception {
    return call(chargePoint, "1", startFrame(idTag)).get("transactionId").intValue();
  }

  private static String startFrame(String idTag) {
    return "\"StartTransaction\",{\"connectorId\":1,\"idTag\":\""
        + idTag
        + "\",\"meterStart\":1200,\"timestamp\":\""
        + STARTED_AT
        + "\"}";
  }

  private static String stop(int transactionId, String optionalFields) {
    return "\"StopTransaction\",{\"transactionId\":"
        + transactionId
        + ",\"meterStop\":9200,\"timestamp\":\""
        + STOPPED_AT
        + "\""
        + optionalFields
        + "}";
  }

  /** Sends a call and returns the payload of the CALLRESULT. */
  private static JsonNode call(
      TestChargePoint chargePoint, String messageId, String actionAndPayload) throws Exception {
    chargePoint.send("[2,\"" + messageId + "\"," + actionAndPayload + "]");
    JsonNode frame = JsonMapper.shared().readTree(String.valueOf(chargePoint.nextFrame(WAIT)));
    assertThat(frame.get(0).intValue()).as("a CALLRESULT: %s", frame).isEqualTo(3);
    return frame.get(2);
  }

  /**
   * Handlers run on the WebSocket threads, which {@code @RecordApplicationEvents} does not see, so
   * a listener collects the events instead. It listens after the commit, as real consumers do, so a
   * stop that rolls back is not counted. The context is shared, so each test reads its own station.
   */
  @TestComponent
  static class RecordedStops {

    private final List<SessionCompleted> all = new CopyOnWriteArrayList<>();

    @TransactionalEventListener
    void on(SessionCompleted completed) {
      all.add(completed);
    }

    List<SessionCompleted> of(String stationId) {
      return all.stream().filter(completed -> completed.stationId().equals(stationId)).toList();
    }
  }
}
