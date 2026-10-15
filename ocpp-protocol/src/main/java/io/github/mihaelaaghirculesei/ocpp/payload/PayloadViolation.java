package io.github.mihaelaaghirculesei.ocpp.payload;

import io.github.mihaelaaghirculesei.ocpp.frame.ErrorCode;

/** Why a payload was refused, ready to be sent back as a CALLERROR. */
public record PayloadViolation(ErrorCode errorCode, String description) {}
