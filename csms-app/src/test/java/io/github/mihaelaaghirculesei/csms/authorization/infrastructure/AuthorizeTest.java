package io.github.mihaelaaghirculesei.csms.authorization.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mihaelaaghirculesei.csms.TestcontainersConfiguration;
import io.github.mihaelaaghirculesei.csms.ocpp.infrastructure.TestChargePoint;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Authorize from the WebSocket frame to the registry and back. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthorizeTest {

  private static final Duration WAIT = Duration.ofSeconds(5);

  @LocalServerPort private int port;
  @Autowired private JdbcClient jdbc;

  @Test
  void answersFromTheRegistry() throws Exception {
    // Expiry dates far from today, so the test needs no fixed clock.
    jdbc.sql(
            "insert into id_tag (id_tag, status, expires_at, parent_id_tag) values"
                + " ('E2E-ACCEPTED', 'ACCEPTED', '2099-01-01T00:00:00Z', 'E2E-FLEET'),"
                + " ('E2E-BLOCKED', 'BLOCKED', null, null),"
                + " ('E2E-EXPIRED', 'ACCEPTED', '2000-01-01T00:00:00Z', null)")
        .update();

    try (TestChargePoint chargePoint = new TestChargePoint(port, "CP-AUTHORIZE")) {
      assertThat(authorize(chargePoint, "1", "e2e-accepted"))
          .isEqualTo(
              "[3,\"1\",{\"idTagInfo\":{\"status\":\"Accepted\","
                  + "\"expiryDate\":\"2099-01-01T00:00:00Z\",\"parentIdTag\":\"E2E-FLEET\"}}]");
      assertThat(authorize(chargePoint, "2", "E2E-BLOCKED"))
          .isEqualTo("[3,\"2\",{\"idTagInfo\":{\"status\":\"Blocked\"}}]");
      assertThat(authorize(chargePoint, "3", "E2E-EXPIRED"))
          .isEqualTo(
              "[3,\"3\",{\"idTagInfo\":{\"status\":\"Expired\","
                  + "\"expiryDate\":\"2000-01-01T00:00:00Z\"}}]");
      assertThat(authorize(chargePoint, "4", "E2E-UNKNOWN"))
          .isEqualTo("[3,\"4\",{\"idTagInfo\":{\"status\":\"Invalid\"}}]");
    }
  }

  private static String authorize(TestChargePoint chargePoint, String messageId, String idTag)
      throws Exception {
    chargePoint.send("[2,\"" + messageId + "\",\"Authorize\",{\"idTag\":\"" + idTag + "\"}]");
    return String.valueOf(chargePoint.nextFrame(WAIT));
  }
}
