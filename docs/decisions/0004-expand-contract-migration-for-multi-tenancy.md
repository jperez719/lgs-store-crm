# 0004: Expand/contract migration pattern for adding multi-tenancy

## Status
Accepted

## Context
Adding tenant scoping to an existing schema with existing data is a
backward-incompatible-looking change: a `NOT NULL tenant_id` column
cannot simply be added to a table that already has rows, and the app
needs to keep working throughout the transition since it is deployed
across multiple replicas via a Kubernetes rolling update (old and new
pod versions briefly coexist during any deploy).

## Decision
Use a staged "expand, then contract" sequence of migrations:
1. `V3` — create the `tenants` table.
2. `V4` — add `tenant_id` as a **nullable** foreign key to `customers`,
   `employees`, and `credit_transactions`. Nullable specifically so it
   is invisible/harmless to app code that doesn't yet reference it.
3. `V5` — backfill all existing rows to a default tenant.
4. Update application code to become tenant-aware, while `tenant_id`
   remains nullable, so old and new code can coexist safely during a
   rolling deploy.
5. `V6` — only once all code paths are confirmed to require and set a
   tenant, alter the column to `NOT NULL`.

## Alternatives considered
- **A single migration adding `tenant_id NOT NULL` directly:** rejected
  outright — this would fail immediately against existing data with no
  default, and would create a hard incompatibility the moment it was
  deployed, with no safe rollout path across replicas.

## Consequences
- The database schema and the running application code were never
  required to be perfectly synchronized at every single instant, which
  is what makes a zero-downtime rolling update possible for a schema
  change of this kind — this is a real, general-purpose pattern beyond
  just this one project (commonly known as "expand/contract" migration).
- This surfaced a real bug in the process, not just in theory: a
  `CreditTransaction` was briefly being persisted without its `tenant`
  field ever set (a missed spot during the incremental Java-side
  update), which only failed loudly once `V6`'s `NOT NULL` constraint
  was applied — an example of the database constraint catching a defect
  the compiler could not, because a `null` field is a valid (if wrong)
  value in Java.