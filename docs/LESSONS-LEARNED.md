# Lessons Learned

A running log of practical problems hit while building this project, and
what they taught — the "war stories" that don't rise to the level of a
formal architecture decision but are genuinely useful to remember and to
explain in an interview.

---

## Environment & tooling

### JDK version mismatch is a shell PATH problem, not just a JAVA_HOME problem
Setting `JAVA_HOME` alone does not change what `java`/`javac` resolve to
on the command line — `PATH` is what the shell actually uses to find the
executable. A `.bash_profile` had a hardcoded
`export PATH="/usr/local/opt/openjdk@17/bin:$PATH"` line, which silently
overrode a correctly-set `JAVA_HOME` pointing at JDK 25. The fix was
deriving `PATH` from `$JAVA_HOME` directly
(`export PATH="$JAVA_HOME/bin:$PATH"`) so the two can never drift apart
again.

### Homebrew doesn't always have a prebuilt binary for a given OS/version
`brew install openjdk@25` attempted to compile the JDK from source on an
older macOS version, which required a full Xcode install (not just
Command Line Tools) and failed. The practical fix was skipping Homebrew
entirely for this and installing a prebuilt JDK directly from a vendor
(Eclipse Temurin). Lesson: when a package manager tries to build from
source unexpectedly, check whether a prebuilt binary from the vendor
avoids the problem entirely rather than fighting the build.

### Docker Desktop's "User" CLI install mode doesn't touch system PATH
With Docker Desktop configured for a per-user CLI install, its tools land
in `$HOME/.docker/bin` — not `/usr/local/bin` — and nothing adds that
folder to `PATH` automatically. `docker: command not found` persisted
until this was added manually. Worth checking Docker Desktop's own
Settings → Advanced screen for this exact detail before assuming a
reinstall is needed.

### `docker compose`'s image auto-naming can silently diverge from expectations
A `docker-compose.yml` service defined with `build: .` and no explicit
`image:` name gets auto-named `<project-folder-name>-<service-name>` by
Compose. This meant Compose was quietly building and running
`lgs-store-crm-app:latest` — a completely different, stale image — while
manual `docker build -t lgs-store-crm:...` commands were creating and
updating separate, differently-tagged images the whole time. The fix was
switching Compose to reference an explicit `image:` tag instead of
letting it build its own.

### `sed -i` behaves differently on macOS vs. Linux
macOS's BSD `sed` requires a backup-suffix argument for in-place edits
(`sed -i.bak ...`); GNU `sed` on Linux does not. A deploy script written
and tested on macOS would break if run as-is on a Linux CI runner without
accounting for this. Worth remembering as soon as any shell script that
edits files in place is expected to run in both environments.

### Docker Desktop's Kubernetes can silently fail to start with no error
Enabling Kubernetes got stuck at "starting" indefinitely, with
`kubectl get nodes` refusing connections and zero Kubernetes containers
ever appearing in `docker ps -a`. Plain Docker itself was fully healthy
the entire time (image pulls and container runs worked normally),
isolating the failure to the Kubernetes subsystem specifically. A full
quit-and-reopen of Docker Desktop (not just toggling the Kubernetes
checkbox) resolved it. Lesson: when a subsystem hangs with literally zero
progress for several minutes, that's a signal to stop waiting and reset,
rather than assuming it's just slow.

---

## Kubernetes-specific

### Scaling a Deployment to zero doesn't always release a LoadBalancer's host port binding
After `kubectl scale deployment store-crm-app --replicas=0`, port 8080
was still held by a Docker Desktop networking process (`com.docker`),
blocking a local `mvn spring-boot:run`. Deleting the Service entirely
(`kubectl delete svc store-crm-app`) released the binding; scaling pods
to zero alone did not.

### `:latest` as an image tag is a real anti-pattern, not just a style preference
Re-running `kubectl apply` with an unchanged manifest (still saying
`image: lgs-store-crm:latest`) does nothing, even after rebuilding the
image locally — Kubernetes only reacts to a genuine change in the
manifest text, and `:latest` never changes as text even when the image
behind it does. Explicit, unique tags per build (ideally a git commit SHA
in a real pipeline) make every deploy a real, traceable, guaranteed-to-
trigger-a-rollout change — and make rollback to a known-good version
possible, which a shared floating tag does not.

### Kubernetes Deployments have no native "wait for dependency" mechanism
Unlike Docker Compose's `depends_on: condition: service_healthy`,
applying an app Deployment before its database Deployment exists results
in a real, visible `CrashLoopBackOff` — deliberately reproduced once to
see it firsthand. An init container
(`initContainers: [...]` polling `pg_isready`) fixed this properly: the
main container never starts until the dependency is confirmed reachable,
rather than crashing and relying on Kubernetes' restart backoff to
eventually succeed. Confirmed the difference directly by comparing
`RESTARTS` count between the two approaches (climbing vs. staying at 0).

---

## Testing

### A test that "passes" can still be hiding a real bug — silently swallowed exceptions
A concurrency test's worker threads only caught `InterruptedException`,
so a real `DataIntegrityViolationException` (a `CreditTransaction` being
persisted with a null `tenant_id`) was silently dropped inside the
thread, and the test's `finally` block still let it report as "finished."
The test then failed on the wrong assertion (`expected 100.00, was 0.00`)
without ever revealing *why*. Capturing every `Throwable` from worker
threads into a shared list and asserting that list is empty surfaced the
real underlying `DataIntegrityViolationException` immediately. Lesson: in
any test spawning real threads, uncaught exceptions do not fail the test
automatically — they must be explicitly captured and asserted on.

### A missing field can compile cleanly and still be wrong
`CreditTransaction.tenant` was added as a field but never wired into the
constructor call inside `CustomerCreditService.applyTransaction` during
an incremental refactor. This compiled without error (`null` is a valid
value for an object reference in Java) and only failed once the database
schema's `NOT NULL` constraint was applied — a concrete example of a
database-level constraint catching something the compiler structurally
cannot.

### `BigDecimal.equals()` is not the same as numeric equality
`new BigDecimal("50.00").equals(new BigDecimal("50.0"))` returns `false`,
since `equals()` also compares scale. Using AssertJ's
`isEqualByComparingTo(...)` instead of `isEqualTo(...)` for all monetary
assertions avoids tests failing purely on trailing-zero formatting
differences rather than genuine value mismatches.

### `.env` is a Docker Compose convention, not a universal one
Maven, JUnit, and plain `mvn spring-boot:run` have no built-in awareness
of a `.env` file — nothing reads it automatically just because it
exists. A test using `@SpringBootTest` without a `@DynamicPropertySource`
override failed with a literal `"${DB_USERNAME}"` used as a password,
because Spring's placeholder resolution only checks real environment
variables/system properties by default. The practical fix was exporting
the variables in the shell before running Maven locally (a
`spring-dotenv` dependency was considered as a smoother alternative but
deliberately deferred).

### An aggressive liveness probe timeout can kill a healthy pod
Both `store-crm-app` pods showed a restart count of 9, discovered while
investigating an unrelated port conflict rather than through active
monitoring — worth noting as its own small lesson (restart counts are
easy to overlook if nothing is actively watching them). `kubectl describe
pod` showed `Exit Code: 137` (SIGKILL) with `Reason: Error`, not
`OOMKilled`, narrowing the cause to something other than memory pressure.

The actual cause was in the probe configuration itself:
`livenessProbe` had `timeoutSeconds` left at its default of 1 second,
with `failureThreshold: 3` — meaning any 3 consecutive health checks
slower than 1 second each (45 seconds of sustained slowness) caused
Kubernetes to conclude the app was hung and force-kill it, even though
the app was actually fine, just briefly slow to respond. This is
plausible under real conditions: Actuator's `/actuator/health` touches
the database connection pool, and the local cluster was also running
Testcontainers-based tests concurrently, adding real resource
contention on the same machine.

The fix was setting `timeoutSeconds: 5` (and the same for the readiness
probe) — generous enough to absorb realistic transient slowness without
disabling the liveness check's actual purpose of catching a genuinely
hung process.

Lesson: a liveness probe's default 1-second timeout is often too
aggressive for a real Spring Boot app whose health check has any real
dependency behind it (a database, in this case). Worth deliberately
choosing `timeoutSeconds` based on observed real startup/response times
rather than leaving it at the Kubernetes default, and worth periodically
checking `RESTARTS` counts directly (`kubectl get pods`) rather than
only checking pod status is `Running`, since a pod can restart
repeatedly and still show healthy at any given moment you happen to
look.

## Testing — API automation

### Allure's default results directory doesn't live under `target/`
The Allure JUnit5 adapter writes to `./allure-results` relative to the
JVM's working directory by default — for a Maven module, that resolves
to the module root (e.g., `api-tests/allure-results`), not
`api-tests/target/allure-results` as most other build output would
suggest. This meant `allure serve target/allure-results` showed an
empty report even though tests had genuinely run and passed. Fixed by
explicitly setting `allure.results.directory` as a Surefire system
property to point into `target/`, which also means `mvn clean`
correctly removes stale results — a bare `./allure-results` folder at
the project root is invisible to `clean` and would otherwise silently
accumulate results across unrelated runs.

### A black-box API test suite is a genuinely different, complementary check from white-box integration tests
The existing Testcontainers-based tests (e.g., proving tenant isolation
at the repository/query level) and the new REST-Assured-based API tests
(proving tenant isolation over real HTTP, through the full controller
stack) sound like they'd be redundant, but they are not: the API-level
version is the only one that would catch a bug introduced purely in the
controller layer — an incorrect `@PathVariable` mapping, a missing
tenant check added at the wrong layer, an exception handler returning
the wrong status code — none of which a repository-level test could
ever see, since it never goes through HTTP or the controller at all.

### A test framework supporting multiple run modes needs one clear seam, not scattered conditionals
Supporting "run against an already-running instance" and "spin up a
self-contained instance" in the same test suite could easily have
turned into `if` checks scattered across every test class. Centralizing
the decision in one place (`BaseApiTest.configureBaseUri()`, resolving
the base URL once via a `selfContained` flag) meant every actual test
class stays completely unaware of which mode it's running under — the
same test code is genuinely reusable across local development and CI
with zero duplication or branching logic inside the tests themselves.

### Homebrew's source-build fallback is a recurring, predictable failure pattern on unsupported macOS versions
This project hit the same underlying failure three separate times —
installing `openjdk@25`, upgrading `allure`, and installing `yq` — each
time because Homebrew had no prebuilt bottle for macOS 13 and silently
fell back to compiling from source, which then failed for
version-specific reasons (missing Xcode, complex build toolchains).
The reliable fix each time was the same: stop fighting the Homebrew
build and download a prebuilt binary directly from the project's own
GitHub releases instead. Worth checking for this option first whenever
a `brew install`/`brew upgrade` unexpectedly starts compiling
dependencies rather than downloading a bottle.

### Uncaught exceptions inside spawned threads fail silently unless explicitly captured
(See also: the concurrency test bug described in the Testing section
above.) This applies generally, not just to that one test: any code
that manually

### Renaming a hostname/alias requires updating every environment that references it, not just the primary one
When store-postgres's Docker Compose service key was renamed from
`postgres` to `store-postgres` to resolve a network alias collision
(see the earlier entry on this), four separate places referenced the
old hostname and needed updating: store-core-service's own
docker-compose.yml, its application-docker.properties, and — missed
initially — api-tests' SelfContainedEnvironment.java, which
independently provisions its own Testcontainers-based Postgres and
RabbitMQ for black-box API testing. The mismatch was invisible for a
long time because Testcontainers' generic timeout error ("Connection
refused" waiting for /actuator/health) gave no indication of the real
cause — the app container was crashing immediately on startup with a
real, specific exception (UnknownHostException), but that detail was
inaccessible without deliberately capturing the container's own logs.

The debugging path that actually worked: disable Testcontainers' Ryuk
cleanup (`TESTCONTAINERS_RYUK_DISABLED=true`) to prevent a crashed
container from disappearing before it could be inspected, and add a
log consumer that writes the app container's stdout directly to the
test's own console output in real time — critically, a raw
System.out-based consumer, not one routed through SLF4J, since
api-tests has no logging backend configured and SLF4J was silently
discarding every log line sent to it.

Lesson: a hostname or alias used across multiple independent
environments (local Docker Compose, CI, self-contained test
provisioning) needs a single source of truth or an explicit checklist
of every place it's referenced — renaming it in the "main" place is not
enough. Separately: when debugging a Testcontainers container that
fails to become healthy, stream its logs directly (a raw stdout
consumer) rather than relying on Testcontainers' own wrapper exception,
which only reports symptoms (timeout, connection refused) and never the
underlying application-level cause.

## Architecture

### The most "natural-looking" service boundary isn't always the right one
It's tempting to assume microservice boundaries should mirror existing
code package structure — it's the most visually obvious split. But the
real question is which operations need to be atomic together. Any
group of writes that must succeed or fail as a single unit (here: the
locked balance update + transaction insert) has to stay inside one
service and one database, regardless of how naturally separable the
code looks on the surface. Package structure is a hint about
organization, not a guarantee about consistency requirements — those
are two different concerns that happened to align in this project's
package layout, but won't always.

## Microservices / messaging

### Adding a new Spring Boot starter can silently break every @SpringBootTest
Adding `spring-boot-starter-amqp` didn't just add a dependency — it
caused Spring Boot to auto-configure a real `RabbitTemplate` bean any
time a full application context loads. This affected every
`@SpringBootTest`-annotated test (`LgsStoreCrmApplicationTests`,
`CustomerCreditServiceConcurrencyTest`), which failed to start their
context without a reachable RabbitMQ — even tests with no direct
relationship to messaging at all. Plain Mockito-based unit tests
(`CustomerCreditServiceTest`) were completely unaffected, since they
never boot a real Spring context. The fix followed the same pattern
already established for Postgres: add a `RabbitMQContainer` via
Testcontainers and point Spring's auto-configuration at it with
`@DynamicPropertySource`, rather than requiring a real, separately
running broker for tests to pass.

Lesson: adding any new Spring Boot starter with auto-configuration
(a database, a message broker, a cache, etc.) should prompt an explicit
check of every `@SpringBootTest` in the project, not just the class
being actively worked on — the blast radius of a new starter is the
entire application context, not just the feature it was added for.

### A new dependency's blast radius extends to every environment that boots the app, not just the ones you remember
Adding RabbitMQ required fixing the main test suite's Spring contexts
(see above), but a second, separate environment was missed on the first
pass: `api-tests`'s `SelfContainedEnvironment`, which spins up the
actual built Docker image via Testcontainers for black-box API testing.
This only surfaced once a real pull request ran CI — the app container
failed its `/actuator/health` check with a persistent 503, timing out
after two minutes, because `SelfContainedEnvironment` only provisioned
Postgres, and the app (configured with `SPRING_PROFILES_ACTIVE=docker`)
couldn't resolve the `rabbitmq` hostname it expected to find on the
network. The fix mirrored the one already applied elsewhere: add a
`RabbitMQContainer`, on the same Testcontainers network, with an
explicit `withNetworkAliases("rabbitmq")` so the app's hardcoded
hostname actually resolves.

Lesson, restated more specifically this time: every place that boots a
full instance of the app — each `@SpringBootTest`, and any Testcontainers-
based environment that runs the packaged image directly — needs to be
checked and updated together whenever a new infrastructure dependency
is added. A useful habit: search the whole codebase (not just the
module currently being edited) for `SPRING_PROFILES_ACTIVE=docker` or
equivalent "boots the real app" patterns whenever a new starter is
added, rather than relying on remembering every place it's used.

### An invalid Docker tag can silently degrade into a different, confusing error
Passing a git branch name containing a slash (`feature/microservice-split`)
as a Docker image tag produced a malformed image reference
(`lgs-store-crm:feature/microservice-split:latest` — two colons) further
down the line, rather than a clear "invalid tag" error at the point of
the mistake. Docker tags cannot contain `/` (it's reserved for registry
namespace paths); worth remembering that a tag must be a single,
slash-free segment, and that branch names are not safe to use as tags
verbatim.

### Changing a message converter does not retroactively fix messages already sitting in a queue
After fixing the message converter mismatch (see ADR 0009), the
notification-service consumer still failed on the next message with a
`MessageConversionException`. The cause wasn't a new bug: RabbitMQ
queues are durable and persist their contents independently of producer
or consumer code changes. A message published earlier under the old,
incorrect Java-serialization format remained in the queue and was
repeatedly redelivered (`amqp_redelivered=true`), poisoning every
attempt to consume it — visible in the message headers
(`contentType=application/x-java-serialized-object`). The fix was
purging the queue via the RabbitMQ management UI (or
`rabbitmqctl purge_queue <queue-name>`) before testing again with a
freshly published message.

Lesson: when changing a message's serialization format or schema on a
running system, existing durable queues need to be explicitly purged,
migrated, or drained — a code fix alone does not retroactively repair
already-queued messages, and a "poisoned" message can silently loop via
redelivery, making a fix look like it isn't working.

## Cross-service / integration (spans store-core-service and notification-service)

### Docker Compose auto-registers service keys as network aliases — identical keys across separate compose projects collide on a shared network
Two independent docker-compose.yml files (store-core-service and
notification-service) both originally used the service key `postgres:`
for their respective database services. In isolation, each compose
project's default network scopes this cleanly with no conflict. Once
both projects joined the same external Docker network
(`store-crm-shared`) — needed so notification-service could reach
store-core-service's RabbitMQ — Docker's per-network DNS registered
*both* containers under the same alias, `postgres`, the automatic alias
Compose derives from the service key. This produced non-deterministic
cross-talk: queries meant for one service's database would occasionally
resolve to the other container, surfacing as
`FATAL: database "X" does not exist` errors naming the *wrong*
service's database — a genuinely confusing symptom, since each
container's own logs looked normal in isolation.

Adding an explicit `aliases:` entry did **not** fix this — Compose's
automatic service-key alias is not removed or overridden by
supplementary aliases; it persists alongside them. The actual fix was
renaming the service key itself (`postgres:` → `store-postgres:` /
`notification-postgres:`). Renaming a service key also orphans the
previously-running container under the old key — Compose refuses to
reuse its container name, requiring `docker compose down -v
--remove-orphans` to fully clear it before the renamed service can
start cleanly.

Lesson: when joining independent Docker Compose projects onto a shared
external network, every service key across all of them must be
globally unique on that network. Locally-reasonable, generic names
(`postgres`, `redis`, `app`) become real collision risks the moment
more than one compose project shares a network — this needs to be
decided deliberately up front, not discovered through a confusing
cross-service data error.

### A source fix doesn't take effect until the image is actually rebuilt — `docker compose up` alone does not guarantee a rebuild
After correcting a hardcoded hostname in `application-docker.properties`
(from the old `postgres` alias to the renamed `notification-postgres`),
`docker compose up -d` continued running the *old*, stale image —
Compose's default change detection did not reliably trigger a rebuild
from a properties-file change buried inside the build context. The
container kept failing with `UnknownHostException: postgres` even
though the source file on disk was already correct, because the
running container was still built from before the fix.

The fix was forcing an explicit rebuild: `docker compose up -d --build`
(or `docker compose build --no-cache <service>` for a fully clean
rebuild when even `--build` doesn't pick up the change). Confirmed by
checking the actual startup log inside the container
(`docker logs <container> | grep jdbc:postgresql`) rather than trusting
that "I changed the source" implies "the running container reflects
it."

Lesson: after any change to a file that lives inside a Docker build
context — application properties, Dockerfile, source code — verify the
running container's actual behavior directly (logs, `docker exec`)
rather than assuming a plain `docker compose up` picked up the change.
`--build` is not automatic.