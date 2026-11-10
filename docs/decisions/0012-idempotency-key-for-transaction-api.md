# 0012: Make the transaction API idempotent with an Idempotency-Key header

## Status
Accepted

## Context
`POST /api/tenants/{tenantId}/customers/{customerId}/transactions`
applies a credit or debit to a customer's balance. Clients retry
requests: a network timeout, a dropped response, or a user
double-clicking can send the same request twice. Before this change,
every retry applied the transaction again, adding a second
`credit_transactions` row and moving the balance a second time.

Pessimistic locking (ADR 0001) does not address this. It makes
concurrent requests safe by serializing them on the customer row, but
two sequential or concurrent copies of the same logical request are
still two valid, separate transactions as far as the database is
concerned.

The consumer side already tolerates duplicate messages (ADR 0011). This
decision covers the producer-facing API, where the duplicate is a
client retry rather than a broker redelivery.

## Decision
Accept an optional `Idempotency-Key` request header (1-100 characters),
scoped per tenant.

1. Flyway migration V7 adds a nullable `idempotency_key` column to
   `credit_transactions` with `UNIQUE (tenant_id, idempotency_key)`.
   Existing rows and requests without a key are unaffected (Postgres
   allows many NULLs under a unique constraint). This is an expand-only
   change in the style of ADR 0004.
2. In `CustomerCreditService.applyTransaction`, after taking the
   `FOR UPDATE` lock on the customer, look up an existing transaction by
   `(tenant, key)`:
   - Not found: apply the transaction as before and store the key on it.
   - Found and the request matches (same customer, employee, type,
     amount, reason): treat as a replay. Do not insert a row, change the
     balance, or publish an event. Return the customer.
   - Found but the request differs: throw `IdempotencyKeyReuseException`,
     mapped to HTTP 422.
3. The unique constraint is a backstop for cases the lock does not
   serialize, for example the same key used on two different customers
   at the same time. That violation is mapped to HTTP 409.

The ordering in step 2 is the important part. The lookup happens after
the lock is acquired, so a concurrent retry blocks on the lock until the
first request commits and then sees its row.

## Alternatives considered
- **Check for the key before taking the lock:** rejected. Two concurrent
  retries could both see no existing row and both proceed, leaving only
  the unique constraint to stop one of them with an error instead of a
  clean replay.
- **Rely on the unique constraint alone, with no lookup:** rejected. The
  second request would fail with a constraint error instead of returning
  success, which defeats the point of a safe retry.
- **Make the header required immediately:** rejected for now. It would
  break existing clients and API tests. It can become required later,
  once clients send it.
- **Store and return the exact original response:** rejected for now.
  It needs response storage and a retention policy. A replay returns the
  customer's current state instead, which matches the original response
  unless other transactions have been applied since.
- **A separate idempotency-keys table with expiry:** rejected for now.
  Keeping the key on the transaction row is simpler and needs no
  cleanup job, at the cost of keys living as long as the transaction.

## Consequences
- A retried request with the same key and body applies exactly once and
  returns the same status code (201). A replay publishes no event, so
  downstream consumers do not see a duplicate.
- Same key with a different body is an explicit client error (422)
  instead of silently ignoring the second request.
- A replay returns the customer's current balance, not the balance at
  the time of the original request. Clients that need the original
  snapshot would need stored responses (future work).
- Keys are retained for the life of the transaction row. There is no
  expiry; revisit if key volume or storage becomes a concern.
- The header is optional, so protection depends on clients sending it.
- Verified by: unit tests (key stored, replay does nothing, mismatch
  rejected), a concurrency test (two threads, same key, applied once),
  and black-box API tests (same key twice applies once and history has
  one row; different amount returns 422; same key under two tenants
  both apply; no key applies every time). The API test log also showed
  that the replayed request published no event.
- Idempotency now exists at both ends of the flow: the API absorbs
  client retries, and the consumer absorbs broker redeliveries (ADR
  0011). Neither makes the other unnecessary.
