package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.ocpp.infrastructure.TestChargePoint;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** MeterValues from the WebSocket frame to the database. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class MeterValuesTest {

  private static final Duration WAIT = Duration.ofSeconds(5);

  @LocalServerPort private int port;
  @Autowired private JdbcClient jdbc;

  @Test
  void storesEverySampleOfEveryMeterValue() throws Exception {
    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-METER-OK")) {
      int transactionId = start(chargePoint);

      JsonNode answer =
          call(
              chargePoint,
              "2",
              "\"MeterValues\",{\"connectorId\":1,\"transactionId\":"
                  + transactionId
                  + ",\"meterValue\":["
                  + "{\"timestamp\":\"2026-11-24T10:01:00Z\",\"sampledValue\":["
                  + "{\"value\":\"1300\"},{\"value\":\"16.2\",\"measurand\":\"Current.Import\","
                  + "\"unit\":\"A\",\"phase\":\"L1\"}]},"
                  + "{\"timestamp\":\"2026-11-24T10:02:00Z\",\"sampledValue\":[{\"value\":\"1400\"}]}"
                  + "]}");

      assertThat(answer.propertyNames()).isEmpty();
      assertThat(valuesOf(transactionId)).containsExactly("1300", "16.2", "1400");
    }
  }

  @Test
  void storesTheReadingsOfARepeatedMessageOnce() throws Exception {
    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-METER-AGAIN")) {
      int transactionId = start(chargePoint);

      call(chargePoint, "2", meterValues(transactionId));
      JsonNode repeated = call(chargePoint, "3", meterValues(transactionId));

      assertThat(repeated.propertyNames()).isEmpty();
      assertThat(valuesOf(transactionId)).containsExactly("1300");
    }
  }

  @Test
  void storesAndLogsAnEnergyRegisterThatGoesBack(CapturedOutput output) throws Exception {
    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-METER-BACK")) {
      int transactionId = start(chargePoint);

      call(
          chargePoint,
          "2",
          "\"MeterValues\",{\"connectorId\":1,\"transactionId\":"
              + transactionId
              + ",\"meterValue\":[{\"timestamp\":\"2026-11-24T10:01:00Z\","
              + "\"sampledValue\":[{\"value\":\"1.1\",\"unit\":\"kWh\"}]}]}");

      assertThat(valuesOf(transactionId)).containsExactly("1.1");
      assertThat(output.getOut())
          .contains(
              "The energy register of transaction "
                  + transactionId
                  + " on CP-METER-BACK went back at 2026-11-24T10:01:00Z: 1100 Wh after 1200 Wh");
    }
  }

  @Test
  void answersReadingsOutsideATransactionWithoutStoringThem() throws Exception {
    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-METER-IDLE")) {
      JsonNode answer =
          call(
              chargePoint,
              "1",
              "\"MeterValues\",{\"connectorId\":0,\"meterValue\":["
                  + "{\"timestamp\":\"2026-11-24T10:01:00Z\",\"sampledValue\":["
                  + "{\"value\":\"21\",\"measurand\":\"Temperature\",\"unit\":\"Celsius\"}]}]}");

      assertThat(answer.propertyNames()).isEmpty();
    }
  }

  @Test
  void neverStoresReadingsForTheSessionOfAnotherChargePoint() throws Exception {
    try (TestChargePoint owner = new TestChargePoint(port, "CP-METER-OWNER");
        TestChargePoint other = new TestChargePoint(port, "CP-METER-OTHER")) {
      int transactionId = start(owner);

      JsonNode answer = call(other, "1", meterValues(transactionId));

      assertThat(answer.propertyNames()).isEmpty();
      assertThat(valuesOf(transactionId)).isEmpty();
    }
  }

  @Test
  void answersReadingsForAnUnknownTransactionWithoutError() throws Exception {
    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-METER-UNKNOWN")) {
      assertThat(call(chargePoint, "1", meterValues(-1)).propertyNames()).isEmpty();
    }
  }

  private List<String> valuesOf(int transactionId) {
    return jdbc.sql("select value from meter_value where transaction_id = ? order by id")
        .param(transactionId)
        .query(String.class)
        .list();
  }

  /** The tag is unknown, which is enough: a refused start still opens a session. */
  private static int start(TestChargePoint chargePoint) throws Exception {
    return call(
            chargePoint,
            "1",
            "\"StartTransaction\",{\"connectorId\":1,\"idTag\":\"METER-ANY\",\"meterStart\":1200,"
                + "\"timestamp\":\"2026-11-24T10:00:00Z\"}")
        .get("transactionId")
        .intValue();
  }

  private static String meterValues(int transactionId) {
    return "\"MeterValues\",{\"connectorId\":1,\"transactionId\":"
        + transactionId
        + ",\"meterValue\":[{\"timestamp\":\"2026-11-24T10:01:00Z\","
        + "\"sampledValue\":[{\"value\":\"1300\"}]}]}";
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
