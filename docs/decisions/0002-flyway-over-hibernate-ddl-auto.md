# 0002: Use Flyway for schema management instead of Hibernate ddl-auto

## Status
Accepted

## Context
Hibernate can infer and apply schema changes automatically
(`ddl-auto=update`) based on `@Entity` classes. This is convenient for
early prototyping but has real limitations once a project has actual
data and a longer lifespan: it cannot express renames (a renamed field
becomes an orphaned column plus a new one), gives no history of what
changed and when, and is explicitly discouraged for anything beyond
local development by Hibernate's own documentation.

## Decision
Use Flyway with versioned, hand-written SQL migration files
(`V1__init.sql`, `V2__add_employees.sql`, etc.), and set
`spring.jpa.hibernate.ddl-auto=validate` so Hibernate only checks that
entity mappings match the schema Flyway created — it never changes the
schema itself.

## Alternatives considered
- **Hibernate `ddl-auto=update`:** rejected for the reasons above —
  unreliable for real schema evolution, no migration history, no safe
  path for destructive or ambiguous changes like renames.
- **Liquibase:** a legitimate alternative with cross-database
  abstraction (XML/YAML/JSON changesets translated per database
  vendor). Rejected because this project is committed to PostgreSQL
  specifically, so cross-database abstraction is a feature that would
  never be used, and Flyway's plain-SQL migrations better reinforce
  understanding of the actual SQL being run.

## Consequences
- Every schema change is an explicit, reviewable, version-controlled
  SQL file. Once a migration has been applied anywhere, it is never
  edited — further changes become a new, higher-numbered migration
  file (this pattern was used directly for the multi-tenancy rollout;
  see ADR 0004).
- Flyway safely handles multiple app replicas starting simultaneously
  and racing to apply migrations — confirmed in practice when two
  Kubernetes pods started at once during a deploy: one pod applied the
  pending migrations, the other correctly detected the schema was
  already current and skipped straight to validation, with no conflict
  or corruption.
- Required an explicit `flyway-database-postgresql` dependency in
  addition to `flyway-core`, since Postgres-specific support was split
  into its own module starting with Flyway 10 — a real, easy-to-miss
  gap that surfaced as an "Unsupported Database" error at first.