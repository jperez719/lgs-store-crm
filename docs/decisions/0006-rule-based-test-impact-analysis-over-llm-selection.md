# 0006: Rule-based test impact analysis, with AI as an advisory layer only

## Status
Accepted

## Context
The goal was a system that selects which tests to run based on where
code changes occurred, to speed up feedback without sacrificing
coverage. Two fundamentally different approaches were available: a
deterministic mapping from changed file paths to test classes, or an
LLM that reads a diff and reasons about which tests are likely affected.

## Decision
Implement test selection as a deterministic, path-based mapping
(`scripts/test-impact-map.yaml` + `scripts/select-tests.sh`), with an
explicit, hard-coded fail-safe: any changed file that does not match a
mapping rule — or that matches a rule deliberately marked
`run_all: true` for known cross-cutting paths (e.g. `pom.xml`, shared
exception handling, database migrations) — causes the full test suite
to run rather than a narrowed selection. An LLM-based layer, if added
later, will only ever produce advisory output (e.g., flagging a
selection that might be incomplete, or suggesting new mapping rules) —
it will never be the sole mechanism deciding whether a test runs.

## Alternatives considered
- **LLM reads the diff and directly decides which tests to run:**
  rejected as the primary mechanism. An LLM's reasoning about test
  impact is inherently non-deterministic — the same diff could
  plausibly receive different selections on different runs — and any
  wrong call to skip a test fails silently: the pipeline reports green
  even though a relevant test never executed. A deterministic mapping
  can only fail toward being *incomplete* (an unmapped file forces
  `run_all` rather than guessing), which is a fundamentally safer
  failure mode than a confidently wrong AI judgment. LLM calls also add
  latency and real API cost per run, and require network access, none
  of which are necessary for what is fundamentally a lookup problem.

## Consequences
- Test selection is fully reproducible: the same diff against the same
  mapping file always produces the same decision, which can be
  reviewed, versioned, and debugged like any other piece of config —
  confirmed directly during development, when an initial `yq` syntax
  bug caused every rule to silently fail to match, and the fail-safe
  correctly forced `run_all` rather than returning an empty, false
  "all clear" selection.
- The mapping file requires manual maintenance as new packages/modules
  are added — an LLM-assisted layer is a reasonable, deliberately
  deferred future addition specifically to reduce this maintenance
  burden (e.g., suggesting new mapping entries when an unmapped file is
  detected), without ever becoming the component that decides whether
  a test is skipped.
- This mirrors ADR 0006's underlying principle in a different domain:
  favor a boundary/mechanism that fails safe and predictably over one
  that is more flexible but can fail silently in a way that looks like
  success.