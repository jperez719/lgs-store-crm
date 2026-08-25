# 0001: Use pessimistic locking for concurrent credit transactions

## Status
Accepted

## Context
Multiple employees may attempt to credit/debit the same customer's store
credit balance simultaneously (e.g., two terminals at the same store). A
naive read-then-write pattern risks a lost update: two concurrent requests
both read the same starting balance, both compute a new balance
independently, and the second write silently overwrites the first — one
transaction's effect is lost with no error and no trace.

## Decision
Use a database-level pessimistic lock (`SELECT ... FOR UPDATE`, via
`@Lock(LockModeType.PESSIMISTIC_WRITE)` on a custom repository query) on
the customer row for the duration of the balance update + transaction
insert, wrapped in a single `@Transactional` service method
(`CustomerCreditService.applyTransaction`).

## Alternatives considered
- **Optimistic locking (`@Version` field):** rejected for this project.
  It requires retry logic on conflict, and the "losing" request's work
  has to be redone. Correctness matters more than raw throughput for
  money movement, and lock contention is expected to be low (a small
  number of employees, brief lock duration per transaction).
- **Application-level locking** (e.g., a Java `synchronized` block or an
  in-memory lock map): rejected because it would not coordinate correctly
  once the app runs as multiple replicas — an in-memory lock only
  synchronizes within a single JVM instance, and this app is deployed
  with `replicas: 2` in Kubernetes.

## Consequences
- Every write path to `Customer.storeCredit` must go through
  `CustomerCreditService.applyTransaction` — never a direct entity save
  elsewhere in the codebase.
- The lock's correctness is proven, not just assumed, via a
  Testcontainers-based integration test that fires two concurrent
  transactions against a real Postgres instance and asserts the final
  balance reflects both (100.00 from two $50 credits, not 50.00).
- This scales correctly across multiple app pod replicas specifically
  because the lock lives in Postgres, not in application memory — this
  was later confirmed in practice once the app was deployed with
  `replicas: 2` on Kubernetes and continued to behave correctly.