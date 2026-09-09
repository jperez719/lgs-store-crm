# 0007: Draw microservice boundaries around transactional consistency, not package structure

## Status
Accepted

## Context
The project's existing packages (`customer`, `employee`, `transaction`,
`tenant`) are cleanly separated by feature, which makes splitting along
those same lines look like the obvious, most granular choice for a
microservices migration. However, `CustomerCreditService.applyTransaction`
depends on all of `Customer`, `Employee`, and `CreditTransaction` being
readable and writable within a single ACID database transaction — this
is the exact mechanism ADR 0001 relies on: a pessimistic row lock
(`SELECT ... FOR UPDATE`) held for the duration of the balance update
and transaction insert, guaranteeing no lost updates under concurrent
requests.

## Decision
Split into two services along a consistency boundary, not a feature
boundary:
- `store-core-service` — owns `Tenant`, `Customer`, `Employee`, and
  `CreditTransaction` together, in one database, preserving the existing
  single-transaction locking guarantee unchanged.
- `notification-service` — owns notification delivery/logging, in its
  own separate database, consuming events published after a core
  transaction commits.

## Alternatives considered
- **Splitting along existing package boundaries** (a `customer-service`,
  `employee-service`, `transaction-service`, `tenant-service`, each with
  its own database): rejected. This would make the pessimistic lock
  unenforceable across a network boundary, forcing either a two-phase
  commit (poor availability/performance characteristics, largely
  avoided in modern systems) or a saga pattern with compensating
  transactions. A saga is a legitimate pattern, but represents a
  significant increase in complexity that should be adopted
  deliberately, not as an incidental consequence of over-granular
  service boundaries chosen before that complexity was actually needed.

## Consequences
- `store-core-service` requires no data migration — it inherits the
  existing `store_crm_db` database and all existing tables unchanged.
- `notification-service` gets a new, independent database with no
  foreign keys back into the core database — a hard rule for this
  project's microservices going forward: cross-service joins are not
  permitted; a service that needs data it doesn't own gets it from an
  event payload or an explicit API call.
- The event-driven design from ADR 0005 is what made this split viable
  with minimal rework: because the notification listener was already
  built to run `AFTER_COMMIT`, on a separate thread, tolerant of its own
  failure without affecting the original transaction, pulling it into a
  separate service is a transport swap (`ApplicationEventPublisher` →
  a real message broker) rather than a redesign of its consistency
  model.
- This decision will need to be revisited if a future feature genuinely
  requires strong consistency across `store-core-service` and another
  service — at that point, a saga (or another explicit distributed-
  transaction pattern) should be adopted deliberately, with its own ADR.