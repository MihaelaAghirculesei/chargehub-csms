package io.github.mihaelaaghirculesei.ocpp.message;

/**
 * StartTransaction.conf.
 *
 * @param transactionId chosen by the central system; the charge point sends it back in every later
 *     message about the transaction
 */
public record StartTransactionResponse(IdTagInfo idTagInfo, int transactionId) {}
