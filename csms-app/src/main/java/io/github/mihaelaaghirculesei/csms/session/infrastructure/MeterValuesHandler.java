package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.ocpp.ActionHandler;
import io.github.mihaelaaghirculesei.csms.session.domain.MeterReadingRepository;
import io.github.mihaelaaghirculesei.csms.session.domain.SessionRepository;
import io.github.mihaelaaghirculesei.ocpp.message.MeterValuesRequest;
import io.github.mihaelaaghirculesei.ocpp.message.OcppMessages;
import io.github.mihaelaaghirculesei.ocpp.payload.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stores the readings a charge point sends during a session. The answer is always an empty
 * confirmation: OCPP 1.6 cannot refuse readings, and a CALLERROR would only make the charge point
 * send them again.
 */
@Component
class MeterValuesHandler implements ActionHandler {

  private static final Logger log = LoggerFactory.getLogger(MeterValuesHandler.class);

  private final SessionRepository sessions;
  private final MeterReadingRepository readings;

  MeterValuesHandler(SessionRepository sessions, MeterReadingRepository readings) {
    this.sessions = sessions;
    this.readings = readings;
  }

  @Override
  public Action action() {
    return Action.METER_VALUES;
  }

  // A stopped session still takes readings: a charge point that was offline sends its queue late.
  @Override
  @Transactional
  public ObjectNode handle(String chargePointId, ObjectNode request) {
    MeterValuesRequest values = OcppMessages.read(request, MeterValuesRequest.class);
    Integer transactionId = values.transactionId();
    if (transactionId == null) {
      log.atDebug()
          .addKeyValue("chargePointId", chargePointId)
          .log(
              "Not storing readings of {} connector {} taken outside a transaction",
              chargePointId,
              values.connectorId());
    } else if (sessions
        .findById(transactionId)
        .filter(session -> session.startedBy(chargePointId))
        .isEmpty()) {
      log.atWarn()
          .addKeyValue("chargePointId", chargePointId)
          .addKeyValue("transactionId", transactionId)
          .log(
              "Ignoring readings from {} for unknown transaction {}", chargePointId, transactionId);
    } else {
      readings.addAll(transactionId, MeterReadings.from(values.meterValue()));
    }
    return JsonNodeFactory.instance.objectNode();
  }
}
