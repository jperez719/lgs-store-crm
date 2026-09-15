# 0009: Use JSON (JacksonJsonMessageConverter) for RabbitMQ message payloads, not Java native serialization

## Status
Accepted

## Context
Spring AMQP's default message converter (`SimpleMessageConverter`) uses
Java native serialization for non-String payloads. This was discovered
only after notification-service failed to deserialize an incoming
message with `SecurityException: Attempt to deserialize unauthorized
class java.util.HashMap` — Spring AMQP's allow-list correctly blocking
untrusted Java deserialization by default. Java native serialization is
also fundamentally incompatible with the project's stated goal of
potentially reimplementing notification-service in a different language
later (see the microservices split discussion, ADR 0007) — a Java
`ObjectOutputStream` payload cannot be meaningfully read by a consumer
written in another language.

## Decision
Explicitly configure a JSON-based message converter on both the
publisher (store-core-service) and the consumer (notification-service),
rather than relying on the AMQP default. The initial implementation
used `Jackson2JsonMessageConverter`, but this class is deprecated for
removal as of Spring AMQP 4.0 in favor of `JacksonJsonMessageConverter`
(built on Jackson 3) — the final implementation uses
`JacksonJsonMessageConverter` directly, configured as the
`MessageConverter` on both the publisher's `RabbitTemplate` and the
consumer's `SimpleRabbitListenerContainerFactory`.

## Alternatives considered
- **Enable `spring.amqp.deserialization.trust.all=true`:** rejected —
  this disables a real security protection rather than fixing the
  underlying design problem, and does nothing to make the contract
  language-agnostic.
- **Keep Java native serialization, accept Java-only consumers:**
  rejected — directly conflicts with the project's goal of eventually
  reimplementing notification-service in a different language to
  demonstrate genuine service decoupling; Java serialization is
  fundamentally not a cross-language format.
- **Use the now-deprecated `Jackson2JsonMessageConverter`:** rejected
  as a permanent choice once the deprecation (for removal) was
  discovered during implementation; corrected to
  `JacksonJsonMessageConverter` before considering this decision
  final.

## Consequences
- The RabbitMQ message body is now genuine, inspectable JSON — visible
  as such directly in RabbitMQ's management UI (`contentType:
  application/json`), and parseable by a consumer written in any
  language, not just Java.
- Both services must independently configure the same converter; there
  is no shared configuration to keep them in sync, consistent with the
  "no shared code between services" principle established when the
  repos were split (ADR 0007).
- Discovered as a direct consequence of this change: switching the
  converter does not retroactively fix messages already sitting in a
  durable queue under the old format. A message published before this
  fix remained in the queue, was continually redelivered
  (`amqp_redelivered=true`), and failed every consumption attempt with
  a `MessageConversionException` until it was manually purged via the
  RabbitMQ management UI. Any future change to message format or
  schema on a running system needs an explicit plan for existing queued
  messages — a code deploy alone does not repair them.
- Verified end-to-end: after purging the stale message and publishing a
  fresh transaction, notification-service correctly received, parsed,
  logged, and persisted the event to its own independent database,
  confirming the full JSON-based contract works correctly between two
  separate services with no shared code.