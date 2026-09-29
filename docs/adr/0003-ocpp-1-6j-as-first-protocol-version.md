# 0003. OCPP 1.6J as the first protocol version

- Status: Accepted
- Date: 2026-09-29

## Context

Charge points talk to the backend with OCPP (Open Charge Point Protocol). Two versions matter
today:

- **OCPP 1.6**, published in 2015. The JSON-over-WebSocket variant is called 1.6J. It is the
  version most installed charge points speak, including many recent models.
- **OCPP 2.0.1**, published in 2020. It has a better device model, built-in security
  profiles and smart charging improvements. Support in the field is growing, but many
  chargers still only speak 1.6J.

The two versions are not compatible: message names, fields and flows differ. Supporting both
at once roughly doubles the protocol work.

## Considered options

1. **OCPP 1.6J only, with the protocol kept at the edge.** Works with the chargers that
   exist today. The core of the system does not know OCPP, so a second version can be added
   later as another adapter.
2. **OCPP 2.0.1 only.** More modern, but it excludes a large part of the installed chargers,
   and simulators and test tools for it are less mature.
3. **Both versions from the start.** Maximum coverage, but twice the protocol work before
   the first charging session works end to end.
4. **OCPP 1.6 over SOAP.** The older transport of the same version. New installations use
   the JSON variant; nobody would choose SOAP for a new backend.

## Decision

We implement OCPP 1.6J (option 1), limited to the Core profile plus the remote commands we
need (`RemoteStartTransaction`, `RemoteStopTransaction`, `TriggerMessage`).

The protocol stays at the edge of the system:

- Message types, the OCPP-J frame codec and JSON Schema validation live in the
  `ocpp-protocol` module.
- The `ocpp` module in the application translates OCPP messages into domain commands and
  domain results back into OCPP responses.
- Domain modules (`station`, `session`, `tariff`, ...) never import OCPP types. An
  architecture test enforces this.

Charge points authenticate with Security Profile 1 (HTTP Basic over the WebSocket upgrade).
TLS (Profile 2) is documented for production.

## Consequences

- Good: works with the chargers most operators actually have.
- Good: adding OCPP 2.0.1 later means writing a second adapter, not changing the domain.
- Bad: we do not use 2.0.1 features such as the richer device model or its built-in security
  profiles.
- Bad: the translation layer is extra code. It is the price for keeping the domain free of a
  protocol that will change.
