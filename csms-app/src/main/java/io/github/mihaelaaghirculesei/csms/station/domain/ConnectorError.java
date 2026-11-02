package io.github.mihaelaaghirculesei.csms.station.domain;

import org.jspecify.annotations.Nullable;

/**
 * What a charge point says is wrong with a connector, so an operator can see why it is faulted.
 *
 * @param info free text from the charge point, if any
 * @param vendorErrorCode the error in the vendor's own terms, if any
 */
public record ConnectorError(
    ConnectorErrorCode code, @Nullable String info, @Nullable String vendorErrorCode) {

  public static final ConnectorError NONE =
      new ConnectorError(ConnectorErrorCode.NO_ERROR, null, null);
}
