package io.github.mihaelaaghirculesei.ocpp.message;

/** Authorize.req: a charge point asks whether an id tag may start or stop charging. */
public record AuthorizeRequest(String idTag) {}
