---
name: challenge-plan
description: >
  Use after writing-plans to validate an implementation plan against the real
  codebase. Checks architecture alignment, completeness, maintainability, and
  risk from 4 perspectives — by reading actual code, not guessing. Triggers:
  plan complete, validate plan, review implementation plan, challenge the plan,
  "will this plan work", before starting development.
---

# Challenge Plan — Codebase-Grounded Plan Validation

Validate an implementation plan against the **real codebase** before development
starts. Every claim must be backed by reading actual files — findings based on
assumptions about the codebase are invalid.

## When To Use

- After writing-plans produces an implementation plan
- Before executing-plans or subagent-driven-development
- When you want to validate a technical plan against reality
- Can be used on any structured implementation plan, not just Superpowers output

## Difference from brainstorm-deep's Active Challenge

| brainstorm-deep (built-in challenge) | challenge-plan |
|---|---|
| "What can go wrong with the *design*?" | "Will this *plan* actually work in *this codebase*?" |
| Reviews the spec through dialogue | Reads real code, checks real file paths |
| Functional, UX, edge cases per area | Architecture, Completeness, Maintainability, Risk |
| Fixes go into the spec | Fixes go into the plan |

## Iron Law

**Read actual code before making any claim.** If you say "this file path doesn't exist"
— you must have checked with Glob/Read. If you say "this conflicts with existing
patterns" — you must show the existing pattern with a file reference. Findings without
codebase evidence are invalid.

## Input

1. Read the implementation plan
2. Read the design spec it was based on
3. Read CLAUDE.md for project conventions
4. Scan the codebase structure to understand current patterns

### Context Budget

The plan challenge reads a lot of code. Keep the context lean:

- **Grep first, Read second.** Don't `Read` entire files. Use `Grep` to find the specific
  pattern, function, or class you need, then `Read` only those lines.
- **Verify file paths with Glob, not Read.** Checking if `src/main/java/.../UserController.java`
  exists needs `Glob`, not reading the whole file.
- **Summarize findings inline.** When you find an existing pattern, note it as
  "existing pattern: `@PreAuthorize` on all controllers in `src/.../controller/`" —
  don't paste 50 lines of code.
- **One perspective at a time.** Complete a perspective, write its findings, then mentally
  release that context before starting the next. The findings document is your memory.

## Process

Review the plan from all 4 perspectives **sequentially, yourself**. Do not dispatch
subagents — you keep full context across perspectives and can spot cross-cutting issues.

Complete each perspective fully before moving to the next.

---

### Perspective 1: Architecture Alignment

Does this plan fit the codebase that actually exists?

For every file path in the plan:
- **Verify it exists** (for MODIFY) or the parent directory exists (for CREATE)
- If paths are wrong: BLOCKER — agents will fail on the first task

For every code pattern in the pseudocode:
- **Read actual code** in the same layer/directory
- Does the pseudocode match existing patterns? (naming, structure, error handling,
  dependency injection, API response format)
- Is the plan building something that already exists? Search for similar code.
  Duplication of existing utilities/services/components is a HIGH finding.
- **Layer violations** — does the plan respect existing architectural boundaries?
  (UI importing from DB layer, business logic depending on framework types, etc.)
- **Dependency direction** — would the plan create circular dependencies?
- **Reuse opportunities** — existing utilities, services, components the plan missed?

### Perspective 2: Completeness

Does the plan cover everything the spec requires?

Build a **traceability matrix** — this is mandatory:

```markdown
| # | Spec Requirement | Plan Slice | Micro-Tasks | Status |
|---|---|---|---|---|
| 1 | "User can create X" | SLICE-2 | 2.1, 2.2, 2.3 | COVERED |
| 2 | "Error on duplicate" | — | — | MISSING |
| 3 | "Admin can delete" | SLICE-4 | 4.2 | PARTIAL — no confirmation dialog |
| 4 | "BDU Test: Login flow" | — | — | MISSING — no Playwright E2E task |
```

Check for:
- Spec requirements with no corresponding slice
- **BDU Test Script scenarios** — every scenario marked `Playwright E2E: YES` MUST have
  a corresponding task in the plan. Missing E2E tests are a HIGH finding.
- Edge cases from the challenge log that aren't addressed
- Missing slices: migrations, config changes, i18n keys, permissions/roles,
  feature flags, seed data
- Error handling paths — does the plan only cover happy paths?
- All CRUD operations covered (not just Create)
- Integration points with existing features

### Perspective 3: Maintainability

Will this plan produce code that fits the existing codebase?

Read 2-3 existing files in the same layers the plan targets. Then check:

- **Pattern consistency** — does the plan follow the SAME patterns? (naming, structure,
  error handling, test structure)
- **Coupling** — are planned components appropriately decoupled?
- **Cohesion** — does each planned file have a single responsibility?
- **Testability** — can each component be tested in isolation as planned?
- **Naming** — do planned names match naming conventions in the codebase?
- **Existing test infrastructure** — factories, fixtures, helpers the plan should use
  but doesn't reference?

For each finding: show the existing pattern (with file:line) alongside what the plan proposes.

### Perspective 4: Risk & Ordering

Will the execution sequence work, and where will it likely fail?

- **DAG correctness** — are dependencies right? Does SLICE-3 truly depend on SLICE-2?
  Could some sequential tasks actually be parallel?
- **Parallel conflicts** — do parallel slices edit the same files? (BLOCKER if yes)
- **Risk ordering** — is the riskiest/hardest slice first? If the plan starts with
  easy stuff, the hard part might invalidate earlier work.
- **Spike needed?** — is there significant uncertainty that should be proved first?
- **Task sizing** — any micro-task that seems larger than 5 minutes of agent work?
  Flag for splitting.
- **Single point of failure** — is there one task that blocks everything downstream?
  Can it be decomposed?
- **The biggest risk** — identify the single most likely point of failure in the
  entire plan and call it out explicitly.

---

## Finding Format

```markdown
### [BLOCKER|HIGH|MEDIUM|LOW] <Title>
- **Perspective:** Architecture | Completeness | Maintainability | Risk
- **Affected:** SLICE-<N> (or "Plan-wide")
- **Evidence:** <Actual file path and what you found when reading it>
- **Issue:** <What is wrong — specific and concrete>
- **Fix:** <Exact modification to the plan>
```

## Severity Guide

| Severity | Criteria |
|----------|----------|
| BLOCKER | Plan will fail during execution — wrong file paths, duplicate code, missing critical slice, broken DAG, parallel file conflicts |
| HIGH | Plan will produce poor results — wrong patterns, missing edge cases, missing E2E tests, suboptimal ordering |
| MEDIUM | Plan works but could be better — reuse opportunity missed, naming inconsistency |
| LOW | Minor improvements — slightly better ordering, cosmetic naming |

## After All 4 Perspectives

### Auto-fix BLOCKER and HIGH findings

1. Propose specific changes to the plan for each BLOCKER/HIGH finding
2. Present to user: the finding, the proposed plan change, and why
3. Apply changes the user approves
4. If a BLOCKER can't be resolved with a plan change: escalate with options

### Document MEDIUM and LOW

Add a "Known Trade-offs" section to the plan. No changes needed — these are informational.

### Present summary

```markdown
## Plan Challenge Results

| Perspective | BLOCKER | HIGH | MEDIUM | LOW |
|-------------|---------|------|--------|-----|
| Architecture |   |   |   |   |
| Completeness |   |   |   |   |
| Maintainability |   |   |   |   |
| Risk |   |   |   |   |

### Traceability Matrix
<from Perspective 2>

### Biggest Risk
<from Perspective 4>

### Plan Changes Applied
<list of changes made>

### Known Trade-offs
<MEDIUM/LOW findings documented>
```

Commit the updated plan.

## Terminal State

When all BLOCKERs are resolved and the user approves:

> Proceed to **executing-plans** or **subagent-driven-development** skill.

## Zero Findings Is Valid

"Architecture alignment: all file paths verified, patterns match existing codebase,
no reuse opportunities missed." That is a perfectly valid result. Do not fabricate
findings.

## Red Flags

| What You Are Doing | Why It Is Wrong |
|---|---|
| Making claims without reading actual code | This phase is about codebase reality |
| "The plan looks correct" without evidence | Show the file you read, the pattern you compared |
| Skipping the traceability matrix | Can't prove completeness without it |
| Allowing parallel slices that edit the same file | Will cause merge conflicts during development |
| Not checking if file paths actually exist | Agents will fail on the first task |
| Changing the design spec | This phase modifies the PLAN, not the spec |
| Reporting LOW findings as BLOCKER | Severity inflation wastes time and erodes trust |
| Not identifying the single biggest risk | The team needs to know where it will likely fail |
| Pasting 50 lines of code as "evidence" | Summarize the pattern, reference file:line |
| Missing Playwright E2E tasks in traceability | BDU scenarios marked E2E: YES need test tasks |