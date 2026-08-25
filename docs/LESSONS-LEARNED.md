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