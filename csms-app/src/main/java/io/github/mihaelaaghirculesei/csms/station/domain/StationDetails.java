package io.github.mihaelaaghirculesei.csms.station.domain;

import org.jspecify.annotations.Nullable;

/** What a charging station says about itself when it boots. */
public record StationDetails(
    String vendor, String model, @Nullable String serialNumber, @Nullable String firmwareVersion) {

  public StationDetails {
    if (vendor.isBlank() || model.isBlank()) {
      throw new IllegalArgumentException("Vendor and model must not be blank");
    }
  }
}
