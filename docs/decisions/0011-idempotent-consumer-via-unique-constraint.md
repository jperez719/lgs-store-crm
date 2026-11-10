# 0011: Make the notification consumer idempotent with a database unique constraint

## Status
Accepted

## Context
RabbitMQ provides at-least-once delivery. A message can reach the
consumer more than once: the consumer crashes after processing but
before acknowledging, the connection drops, or the message is requeued
after an error (ADR 0009 documented a poison message redelivering with
`amqp_redelivered=true`). Before this change, notification-service
inserted a new `notifications` row for every message it received, so a
redelivered `credit-transaction.applied` event created a duplicate row
and, with a real channel, a duplicate email or SMS.

The pessimistic locking from ADR 0001 does not help here. It protects
the balance from concurrent writers inside store-core-service. It says
nothing about the same event being delivered twice to a different
service.

## Decision
Treat `transaction_id` as the idempotency key for the consumer, and
enforce "one notification per transaction" in the database:

1. Flyway migration V2 adds `UNIQUE (transaction_id)` on
   `notifications`. It first deletes pre-existing duplicate rows
   (keeping the earliest), because the constraint would otherwise fail
   to apply to data created during earlier redelivery testing.
2. `NotificationService.recordAndSend` checks
   `existsByTransactionId` first and returns early on a duplicate. This
   is a fast path only, not the guarantee.
3. `CreditTransactionNotificationListener` catches
   `DataIntegrityViolationException` around the service call and treats
   it as "already processed". This handles the race where two copies
   both pass the `exists` check.

A duplicate is treated as success, not failure. Throwing would make
RabbitMQ requeue the message and redeliver it forever.

## Alternatives considered
- **`exists` check only, no constraint:** rejected. Two copies
  processed at the same time can both pass the check and both insert.
  The check is a read-then-write race.
- **In-memory set of seen IDs:** rejected. Lost on restart, not shared
  across multiple consumer instances, and grows without bound.
- **Catch the violation inside the `@Transactional` service method:**
  rejected. With `GenerationType.UUID` the insert is deferred until
  commit, so the exception is raised after the method returns, outside
  any `try/catch` inside it. If it were flushed and caught inside, the
  Postgres transaction would already be aborted and later statements
  would fail.
- **Make the publisher exactly-once:** rejected as unrealistic. The
  broker and network can always cause redelivery, so the consumer must
  tolerate duplicates regardless.

## Consequences
- Verified by tests with a real Postgres (Testcontainers): the same
  payload sent twice yields one row; the listener does not throw; the
  database rejects a second row directly; two threads sending the same
  payload at once yield one row and no leaked exception. The test log
  shows the race path actually occurring: both threads passed the
  `exists` check, one insert failed with
  `uq_notifications_transaction_id`, and the listener absorbed it.
- Known limitation: in the race path, both threads execute the
  "send" step (currently a log line) before the loser's insert fails.
  With a real channel this could send twice. The fix when a real
  channel is added is to insert the row first and send only if the
  insert succeeded (or use `INSERT ... ON CONFLICT DO NOTHING` and check
  the affected row count).
- The constraint is the guarantee, the `exists` check is only an
  optimization, and the listener `catch` is what keeps a race from
  becoming a redelivery loop. All three are needed.
- The same pattern is the model for the producer-facing API side,
  where retries by clients can duplicate a transaction. That needs an
  `Idempotency-Key` and is a separate decision.
