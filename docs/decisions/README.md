# Architecture Decision Records
This repo is the architectural source of truth for the overall
lgs-store-crm system, including decisions that affect other services
(e.g., notification-service). Service-specific implementation notes
live in each service's own repo.

| #                                                                          | Decision | Status |
|----------------------------------------------------------------------------|----------|--------|
| [0001](0001-pessimistic-locking-for-concurrent-credit-updates.md)          | Pessimistic locking for concurrent credit updates | Accepted |
| [0002](0002-flyway-over-hibernate-ddl-auto.md)                             | Flyway over Hibernate ddl-auto | Accepted |
| [0003](0003-explicit-tenant-scoping-over-hibernate-filters.md)             | Explicit tenant scoping over Hibernate filters | Accepted |
| [0004](0004-expand-contract-migration-for-multi-tenancy.md)                | Expand/contract migration for multi-tenancy | Accepted |
| [0005](0005-in-process-events-before-message-broker.md)                    | In-process events before message broker | Accepted |
| [0006](0006-rule-based-test-impact-analysis-over-llm-selection.md)         | Rule-based test impact analysis, AI as advisory only | Accepted |
| [0007](0007-microservice-boundaries-preserve-transactional-consistency.md) | Microservice boundaries preserve transactional consistency | Accepted |
| [0008](0008-preserve-after-commit-guarantee-when-adding-rabbitmq.md) | Preserve AFTER_COMMIT guarantee when introducing RabbitMQ | Accepted |
| [0009](0009-json-message-converter-for-rabbitmq.md) | JSON message converter for RabbitMQ (JacksonJsonMessageConverter) | Accepted |
| [0010](0010-claude-api-test-failure-triage.md) | Claude API for test/failure triage | Accepted |