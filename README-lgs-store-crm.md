# LGS Store CRM (store-core-service)

A multi-tenant **store credit** backend. Stores keep customers and employees, and apply credit/debit transactions against a customer's balance. Every transaction is recorded with its resulting balance, so history is auditable, and balances stay correct under concurrent edits. After each committed transaction the service publishes an event to RabbitMQ, which the separate [`lgs-notification-service`](https://github.com/jperez719/lgs-notification-service) consumes.

This is a learning project. It was built step by step to practice real-world backend engineering, and every major choice is written down so it can be explained in an interview.

## What it does

- Tenant-scoped customers, employees and credit/debit transactions with full history
- Safe concurrent updates (no lost updates, no negative balances)
- Tenant management endpoints (create/get tenants)
- Publishes `credit-transaction.applied` events to RabbitMQ after commit
- Swagger UI for exploring the API

## Tech stack

Java 25, Spring Boot 4.1, Maven, Spring Data JPA / Hibernate 7, PostgreSQL 16, Flyway, RabbitMQ (Spring AMQP), Docker / Docker Compose, Kubernetes, JUnit 5, Mockito, Testcontainers, REST Assured, Allure, GitHub Actions, Claude API (failure triage).

## What this project was intended to teach

| Area | What I practiced |
|---|---|
| Concurrency | Pessimistic locking (`SELECT ... FOR UPDATE`) and proving it with a concurrent test (ADR 0001) |
| Schema evolution | Flyway migrations and the expand/contract pattern for adding `tenant_id` safely (ADR 0002, 0004) |
| Multi-tenancy | Explicit tenant-scoped repository methods, with isolation proven at unit, DB and API level (ADR 0003) |
| Event-driven design | In-process events, then RabbitMQ; `AFTER_COMMIT` so events never describe rolled-back work (ADR 0005, 0008) |
| Service boundaries | Splitting along transactional consistency, one database per service, no shared code, JSON message contract (ADR 0007, 0009) |
| Containers | Multi-stage Docker builds, non-root user, Compose with a shared network |
| Kubernetes | Deployments, Services, Secrets, ConfigMaps, PVCs, init containers, probes, rolling updates, versioned image tags |
| Testing | Unit tests, Testcontainers integration tests, black-box API test framework with REST Assured + Allure |
| CI/CD | GitHub Actions: build and test, build image, run API tests against it |
| Test impact analysis | Rule-based mapping from changed files to the tests to run, with a run-all fail-safe (ADR 0006) |
| AI-assisted tooling | Using the Claude API to triage test failures (ADR 0010) |
| Engineering habits | ADRs, a lessons-learned log, feature branches, secrets kept out of git |

## Repository layout

```
src/                     Spring Boot application (customer, employee, transaction, tenant, notification, common)
api-tests/               Standalone API test module (REST Assured, Allure, Testcontainers)
k8s/                     Kubernetes manifests (real secrets are gitignored; *.example.yaml committed)
scripts/                 deploy.sh, test impact analysis, AI failure triage
docs/decisions/          Architecture decision records (0001-0010)
docs/LESSONS-LEARNED.md  Running log of problems hit and what they taught (covers both services)
docker-compose.yml       Postgres, RabbitMQ and the app on the shared network
```

## Run locally

```bash
# 1. Create a .env with your DB and RabbitMQ credentials (not committed)
# 2. Create the shared network used by both services
docker network create store-crm-shared

# 3. Start dependencies
docker compose up -d store-postgres rabbitmq

# 4. Export the same variables in your shell (Maven does not read .env,
#    and shell variables override .env in Compose)
# 5. Run the app
mvn spring-boot:run
```

Swagger UI: http://localhost:8080/swagger-ui.html

## Tests

```bash
mvn clean test                 # unit + Testcontainers integration tests (needs Docker)
cd api-tests && mvn test       # black-box API tests (self-contained mode)
```

## More

- Design decisions: `docs/decisions/`
- Problems and lessons: `docs/LESSONS-LEARNED.md`
- Consumer service: https://github.com/jperez719/lgs-notification-service
