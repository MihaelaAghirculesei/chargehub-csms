package io.github.mihaelaaghirculesei.ocpp.message;

import org.jspecify.annotations.Nullable;

/**
 * StopTransaction.conf.
 *
 * @param idTagInfo present only when the request carried an id tag
 */
public record StopTransactionResponse(@Nullable IdTagInfo idTagInfo) {}
