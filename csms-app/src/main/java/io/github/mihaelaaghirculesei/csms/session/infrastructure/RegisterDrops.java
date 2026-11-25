package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.session.domain.RegisterDrop;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Logs energy register readings that go backwards, when the message that causes it arrives. */
final class RegisterDrops {

  private static final Logger log = LoggerFactory.getLogger(RegisterDrops.class);

  private RegisterDrops() {}

  static void report(List<RegisterDrop> drops, String chargePointId, int transactionId) {
    for (RegisterDrop drop : drops) {
      log.atWarn()
          .addKeyValue("chargePointId", chargePointId)
          .addKeyValue("transactionId", transactionId)
          .log(
              "The energy register of transaction {} on {} went back at {}{}: {} Wh after {} Wh",
              transactionId,
              chargePointId,
              drop.sampledAt(),
              drop.phase() == null ? "" : " on phase " + drop.phase(),
              plain(drop.energyWh()),
              plain(drop.previousWh()));
    }
  }

  // A reading in kWh becomes 1100.0 Wh, which reads like a different number than 1100 Wh.
  private static String plain(BigDecimal wattHours) {
    return wattHours.stripTrailingZeros().toPlainString();
  }
}
