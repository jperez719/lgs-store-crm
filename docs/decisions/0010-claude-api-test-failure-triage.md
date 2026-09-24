# 0010: Use the Claude API for LLM-assisted test/failure triage

## Status
Accepted

## Context
Per the roadmap goal of incorporating AI-assisted tooling into the
project, a triage tool was needed to help interpret test/application
failures faster than manually re-reading a full stack trace each time.
Per ADR 0006's established principle — AI should inform, never silently
gate — this tool is explicitly advisory: it produces a plain-English
analysis for a human to read and verify, never an automated decision
that skips or blocks anything.

## Decision
Build a small, standalone script (`scripts/triage-failure.py` +
`scripts/triage-failure.sh`) that sends a test/application failure's
stack trace, together with the relevant `git diff` against a base
branch, to the Claude API (`claude-sonnet-4-6`), and prints a structured
analysis: likely root cause, which diff lines (if any) are implicated,
a classification (bug vs. environmental vs. config mismatch), and one
concrete next step.

## Alternatives considered
- **Building a custom-trained/fine-tuned model instead of using a
  commercial API:** considered, and not rejected outright — deliberately
  deferred as a separate, later phase. Fine-tuning an open-source model
  on this project's own accumulated failure examples is a distinct,
  worthwhile exercise, but starting with a capable general-purpose
  model establishes a baseline to compare against, and is a much
  smaller first step.
- **Feeding only the stack trace, no diff:** rejected — a diff is
  necessary context to distinguish "this failure was caused by a recent
  code change" from "this is unrelated to anything currently in
  progress," which materially changes the correct triage classification.

## Consequences — validated through real, deliberate testing
The tool was tested against a real historical failure from this
project (a `java.net.UnknownHostException: store-postgres` caused by an
incompletely-propagated hostname rename — see the cross-service
lessons-learned entries), with the actual root cause already known,
specifically so the tool's output could be judged against ground truth
rather than assumed correct.

Two real, distinct problems were found and fixed during this process,
not by inspection but by observing incorrect output:

1. **Initial prompt caused the model to under-prioritize the deepest
   `Caused by` line in a multi-layered exception chain**, instead
   reasoning from a shallower, more generic wrapper exception
   (`PSQLException`) and producing a plausible but non-specific
   "check if the database is running" diagnosis. Fixed by explicitly
   instructing the prompt to treat the deepest `Caused by` line as the
   most diagnostically significant, and to explicitly consider
   "configuration/naming mismatch between two places" as its own
   failure category rather than defaulting to generic environmental
   causes.
2. **A manually pasted stack trace was silently incomplete** (collapsed
   formatting lost the final, most important `Caused by:
   UnknownHostException` frames on an earlier attempt), causing the
   model to correctly analyze what it was given, but reach a materially
   wrong conclusion because the most important line was missing from
   its input — not a model reasoning failure, an input-completeness
   failure. This motivated adding an explicit pre-flight summary
   (line count, last line printed) before every API call, so an
   incomplete input is visible before it produces a misleading result.

After both fixes, the tool correctly identified the true root cause
(DNS/hostname resolution failure implying a custom service name not
resolvable in the current network context) from the stack trace alone,
matching the actual, previously-diagnosed root cause exactly.

A known, accepted limitation: without explicit information about the
actual deployment topology (Docker Compose vs. Kubernetes vs. local),
the model can correctly identify the *class* of problem (a
non-standard, likely custom-defined hostname not resolving) but may
guess incorrectly at the *specific* environment it belongs to. This is
a reasonable target for a future iteration — feeding relevant
docs/manifests as additional context, rather than the stack trace and
diff alone.