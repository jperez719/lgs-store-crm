# 0003: Explicit tenant-scoped repository methods over implicit filtering

## Status
Accepted

## Context
Adding multi-tenancy requires that every query against `Customer`,
`Employee`, and `CreditTransaction` be scoped to the correct tenant.
Hibernate supports automatic, implicit scoping via `@Filter`/`@FilterDef`,
which would apply tenant filtering globally without every repository
method needing to declare it.

## Decision
Require every repository query that touches tenant-owned data to take an
explicit `tenantId` parameter (e.g., `findByIdAndTenantId`,
`findByTenantId`), rather than relying on an implicit, globally-applied
Hibernate filter.

## Alternatives considered
- **Hibernate `@Filter`/`@FilterDef` (implicit tenant scoping):**
  rejected. While it reduces repetition, it makes tenant scoping
  invisible at the call site — a reader cannot tell from a repository
  method signature alone whether tenant isolation is actually being
  enforced. Given that a cross-tenant data leak is one of the most
  severe possible bugs in a multi-tenant system, explicitness was
  judged more valuable than convenience.

## Consequences
- Every method signature makes tenant scoping visible and auditable —
  `findByIdAndTenantIdForUpdate(customerId, tenantId)` cannot be called
  without a tenant, by construction.
- A request for a real customer ID under the wrong tenant ID returns
  "not found," identical to a genuinely nonexistent ID — this is
  deliberate: it avoids leaking "this ID exists, just not for you" as
  information disclosure.
- This behavior is proven with dedicated tests at two levels: a
  mock-level unit test documenting the contract (wrong tenant = not
  found), and a Testcontainers-backed test against a real database
  proving the actual SQL query correctly excludes another tenant's row.
- More verbose than implicit filtering — every repository method,
  service method, and controller endpoint had to be threaded with a
  `tenantId` parameter through several layers, done incrementally with
  the compiler flagging every remaining caller.