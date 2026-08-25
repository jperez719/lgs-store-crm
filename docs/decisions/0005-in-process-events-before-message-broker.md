# 0005: Start event-driven architecture with in-process events, not a broker

## Status
Accepted

## Context
Adding event-driven architecture is a goal for this project, ultimately
in support of a future microservices split. Introducing a real message
broker (Kafka, RabbitMQ) immediately would require learning the
publish/subscribe pattern and operating new infrastructure (broker
setup, serialization format, network reliability, consumer group
semantics) all at once.

## Decision
Implement the pattern first using Spring's built-in
`ApplicationEventPublisher` and `@TransactionalEventListener`, entirely
in-process, before introducing any real broker or separate service.

## Alternatives considered
- **Introduce Kafka/RabbitMQ immediately:** rejected for now. Doing so
  before the microservices split exists would mean learning the event
  pattern itself and distributed broker infrastructure simultaneously,
  with no independently-deployed consumer to justify the infrastructure
  yet.

## Consequences
- `CustomerCreditService.applyTransaction` publishes a
  `CreditTransactionAppliedEvent` — an immutable, flattened record (not
  a live JPA entity) — after both the balance update and transaction
  insert are saved.
- The listener uses `@TransactionalEventListener(phase =
  TransactionPhase.AFTER_COMMIT)` specifically so a failure in a
  listener (e.g., a future real notification call failing) can never
  roll back the original, already-valid financial transaction. This was
  confirmed directly in logs: the listener fires on a separate thread,
  strictly after the original transaction's SQL statements complete.
- This design is intended to generalize later: swapping
  `ApplicationEventPublisher` for a real broker client should mean
  changing the publish/subscribe mechanism, not redesigning the event
  shape or the "publish after commit" guarantee.
- Verified with `@RecordApplicationEvents` in a Testcontainers-backed
  test, proving the event is actually published with the correct data
  — not just that a log line appears.