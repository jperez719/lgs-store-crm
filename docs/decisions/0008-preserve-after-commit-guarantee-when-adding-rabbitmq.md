# 0008: Preserve the AFTER_COMMIT guarantee when introducing RabbitMQ

## Status
Accepted

## Context
ADR 0005 established that notification handling must only occur after
the originating database transaction has genuinely committed, using
`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.
When introducing RabbitMQ as the transport for `notification-service`
to eventually consume, the first draft called
`rabbitTemplate.convertAndSend(...)` directly inside
`CustomerCreditService.applyTransaction`, still wrapped in the method's
own `@Transactional` boundary. This would have published a message to
RabbitMQ immediately, regardless of whether the surrounding transaction
later committed or rolled back — a regression of the exact guarantee
ADR 0005 established, since a rolled-back transaction (e.g., an
`InsufficientCreditException`) could still result in a real,
externally-visible message being published.

## Decision
Keep the existing in-process `ApplicationEventPublisher.publishEvent(...)`
call and `@TransactionalEventListener(AFTER_COMMIT)` listener exactly as
designed in ADR 0005. The listener's body is what changed: instead of
only logging, it now calls `rabbitTemplate.convertAndSend(...)` to
actually publish to RabbitMQ. The in-process event remains the trigger
that guarantees "this only happens after a real commit"; RabbitMQ
publishing is simply what the already-safe callback now does.

## Alternatives considered
- **Publish to RabbitMQ directly inside `applyTransaction`:** rejected
  — identified during design review, before being implemented, as
  breaking the AFTER_COMMIT guarantee. Spring's `RabbitTemplate` has no
  transaction-awareness by default; a plain `convertAndSend()` call
  publishes synchronously and immediately, independent of the
  surrounding `@Transactional` outcome.
- **Configure RabbitMQ's own transactional/publisher-confirm support
  directly on the connection:** a real alternative, but adds real
  complexity (channel transactions or publisher confirms) to solve a
  problem the existing in-process event mechanism already solves for
  free. Rejected as unnecessary given the simpler option was available.

## Consequences
- The two concerns stay cleanly separated: "did something real happen"
  (in-process event, transactionally guaranteed) versus "tell the
  outside world" (the listener's implementation detail, now RabbitMQ,
  previously a log line).
- Verified directly: `CustomerCreditServiceConcurrencyTest`'s existing
  two-thread concurrency test now also exercises real RabbitMQ
  publishing (via a `RabbitMQContainer` Testcontainers instance), and
  logs confirm each thread's publish only occurs after its own
  transaction commits, on a separate thread, exactly as ADR 0005
  described.
- This pattern generalizes: any future transport (Kafka, a webhook call,
  etc.) added later should plug into the same `AFTER_COMMIT` listener
  rather than being called directly from within a business transaction.