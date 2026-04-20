---
name: brainstorm-deep
description: >
  Use BEFORE any creative work — creating features, building components, adding
  functionality. Socratic one-question-at-a-time dialogue with parallel research,
  active challenging of assumptions, and incremental design validation. Triggers:
  new feature, brainstorm, explore idea, "let's build", "what should we build",
  plan a feature, design something.
---

# Brainstorm Deep — Socratic Design with Active Challenge

Help turn ideas into bullet-proof designs through disciplined one-question-at-a-time
dialogue, parallel research, and active stress-testing of every assumption.

## Anti-Pattern: "This Is Too Simple To Need A Design"

Every project goes through this process. A todo list, a single-function utility, a config
change — all of them. "Simple" projects are where unexamined assumptions cause the most
wasted work. The design can be short (a few sentences for truly simple projects), but you
MUST present it and get approval.

## Iron Law

Ask exactly ONE question, then STOP and wait. Do NOT provide solutions until you have
asked at least 5 clarifying questions. Do NOT write code, scaffold, or take any
implementation action until the design is approved.

## Checklist

Create a task for each item and complete in order:

1. **Explore project context** — check files, docs, recent commits
2. **Dispatch research agents** — parallel background research on the topic
3. **Socratic discovery** — minimum 5 questions, one at a time, using the questioning ladder
4. **Identify functional areas** — present list, get confirmation
5. **Deep-dive each area** — function + UX + BDU scenario + challenge + summary per area
6. **Propose approaches** — 2-3 with trade-offs and your recommendation
7. **Present design** — one section at a time, get approval per section
8. **Self-review** — check for gaps, contradictions, placeholders, vague language
9. **Write design doc** — save to `docs/plans/YYYY-MM-DD-<topic>-design.md`, commit
10. **Transition** — invoke writing-plans skill

## Phase 1: Context & Research

**Project context first:** Check files, docs, recent commits to understand what exists.

**Visual Companion:** When you anticipate that upcoming questions will involve visual
content (mockups, layouts, diagrams, UI comparisons), offer the Visual Companion once:

> "Some of what we're working on might be easier to explain if I can show it to you
> in a web browser. I can put together mockups, diagrams, comparisons, and other
> visuals as we go. This feature is still new and can be token-intensive. Want to
> try it? (Requires opening a local URL)"

This offer MUST be its own message — do not combine with other content.
If accepted: read `skills/brainstorming/visual-companion.md` for the detailed guide.
If declined or unavailable: proceed with text-only brainstorming.

Per-question decision (even after accepting): use the browser only when the user would
understand better by SEEING it than reading it. Mockups, wireframes, layout comparisons,
architecture diagrams → browser. Requirements, scope, tradeoff lists, conceptual
choices → terminal. A question about a UI topic is not automatically a visual question.

**Scope check:** If the request describes multiple independent subsystems, flag immediately.
Help decompose into sub-projects before deep-diving. Each sub-project gets its own
spec → plan → implementation cycle. Brainstorm the first sub-project through the normal flow.

**Dispatch parallel research** when the topic is known. Use a subagent if available —
this keeps research results out of the main context until needed. If subagents are
unavailable, do inline research but follow the context budget rules below.

Research goals (subagent preferred, inline as fallback):
- Existing solutions, libraries, prior art for this problem space
- Architecture patterns used by established projects for similar features
- Known pitfalls, common mistakes, security gotchas
- UX patterns from established products (Nielsen Norman Group, Baymard)
- What components/patterns already exist in THIS project's codebase

### Context Budget Rules

Long brainstorming sessions can exhaust the context window. Follow these rules:

1. **Research results: summarize, don't dump.** Whether from a subagent or inline search,
   distill findings into 3-5 bullet points per topic. Never paste full articles, READMEs,
   or documentation pages into the conversation. Extract only what's relevant to THIS feature.

2. **Codebase reads: targeted, not broad.** Don't `Read` entire files to "understand the
   project". Use `Grep` and `Glob` to find specific patterns, then `Read` only the
   relevant sections. Example: don't read all of `SecurityConfig.java` — grep for
   `@PreAuthorize` patterns across controllers.

3. **Monitor context usage.** At ~70% context capacity: finish the current area deep-dive,
   write the design spec sections completed so far to disk, and inform the user:
   "I've written the first N sections to the design doc. Starting a fresh context to
   continue with the remaining areas." The written spec file IS the state — no separate
   state tracking needed.

4. **Weave, don't report.** Research findings enter the dialogue as part of your next
   question ("Research shows X is a common pitfall — how should we handle that?"),
   not as a separate dump of everything you found.

## Phase 2: Socratic Discovery

Progress through these levels — do NOT jump ahead:

1. **Clarify** — "What exactly do you mean by...?" / "Can you give an example?"
2. **Probe assumptions** — "Why do you assume...?" / "What if that's wrong?"
3. **Probe evidence** — "What evidence supports...?" / "How do you know?"
4. **Explore perspectives** — "How would [user type X] see this?" / "What would a competitor do?"
5. **Consequence analysis** — "What happens if...?" / "What are the implications?"
6. **Meta-questions** — "Why is this question important?" / "What are we not asking?"

### Discovery Rules
- ONE question per message, then STOP
- Challenge assumptions — ask "Why?" at least twice before accepting a premise
- Do NOT offer solutions during discovery — only questions
- When research findings arrive, weave them into your next question naturally
  (e.g., "Research shows X is a common pitfall here — how should we handle that?")
- Minimum 5 discovery questions before moving forward
- Multiple choice preferred when possible — easier to answer than open-ended

## Phase 3: Functional Areas

After discovery, synthesize into functional areas:

```markdown
## Functional Areas Identified
1. **<Area>** — <One-line description>
2. **<Area>** — ...
```

Present and ask: "Are these the right areas? Anything missing or misplaced?"
Wait for confirmation.

## Phase 4: Deep-Dive Each Area

For EACH area, go through this sequence. One question at a time throughout.

### 4a: Functional Definition
- What exactly does this area do?
- Inputs, outputs, state changes?
- Edge cases?

### 4b: UX Concept
- How does the user interact with this?
- Reference existing project patterns: "The project already has [component X] — reuse?"
- Consider: power user (daily, wants shortcuts), casual user (occasional, needs clarity),
  BDU / brain-dead user (first-time, reads nothing, needs onboarding cues)

### 4c: BDU Test Scenario
For every area that has UI, define a concrete BDU test scenario:

```markdown
**BDU Test: <Area Name>**
- **Entry point:** Where does the BDU start? (main nav, dashboard, link from other feature?)
- **Steps:** Numbered click-by-click steps a brain-dead user would take
- **Success:** What does the BDU see when it works?
- **Failure modes:** What happens when the BDU does something stupid?
  (double-click, back button, garbage input, mobile, two tabs)
- **Playwright E2E:** YES — this scenario MUST become an automated Playwright test
- **Verify in browser:** YES — this scenario MUST also be visually verified during verify-e2e
```

These scenarios serve TWO purposes:
1. **Automated E2E tests** — writing-plans MUST create Playwright test tasks for every
   scenario marked `Playwright E2E: YES`. These tests run in CI and catch regressions.
2. **Visual browser verification** — verify-e2e runs these scenarios manually in a real
   browser to catch what automated tests miss (visual glitches, confusing flows, mobile).

If you can't describe the BDU path clearly now, the design is too vague.

### 4d: Active Challenge
This is where you push back. Do NOT accept "it should be fine."

- "What happens if the user does [unexpected thing]?"
- "What if [assumption] is wrong?"
- "What if there are 10,000 of these?"
- "What happens on a slow mobile connection?"
- "What if two users do this simultaneously?"
- Push back on weak answers with concrete scenarios

### 4e: Area Summary
Write a brief summary. Present for confirmation before moving to the next area.

```markdown
### Area: <n>
**Function:** <What it does>
**UX:** <How the user interacts>
**BDU Test:** <Click-by-click scenario, or "No UI" if backend-only>
**Edge Cases:** <Agreed handling>
**Open Questions:** <None, or list>
```

## Phase 5: Architecture Proposals

Only AFTER all areas are fully explored:

1. Propose 2-3 approaches with trade-offs and your recommendation
2. Lead with your recommended option and explain why
3. For each: name, one-line summary, pros, cons, alignment with existing project patterns
4. Include a Mermaid diagram if the architecture warrants it
5. **YAGNI ruthlessly** — remove unnecessary features from all proposals

Wait for user to choose before proceeding.

## Phase 6: Design Presentation

Present ONE section at a time. Scale each section to its complexity: a few sentences
if straightforward, up to 200-300 words if nuanced. Get approval after each section.

Sections (in order):
1. **Overview** — What, who, why
2. **Functional Areas** — One subsection per area (function + UX + edge cases)
3. **Architecture** — Chosen approach with diagrams
4. **Data Model** — Entities, relationships, key fields (if applicable)
5. **API Surface** — Endpoints or interfaces (if applicable)
6. **UX Flows** — Step-by-step user journeys for key scenarios
7. **BDU Test Script** — All BDU scenarios from Phase 4c collected as a test script.
   Each scenario: entry point, steps, success criteria, failure modes.
   Scenarios marked `Playwright E2E: YES` MUST become automated tests during implementation.
   Scenarios marked `Verify in browser: YES` MUST be visually checked during verify-e2e.
   **This section is input for both writing-plans (creates E2E test tasks) and
   verify-e2e (manual browser verification).**
8. **Non-Functional Requirements** — Performance, security, accessibility targets
9. **Scope Boundaries** — What is explicitly IN and OUT

## Phase 7: Self-Review

Before declaring complete, verify:
- [ ] Every functional area has detail — no gaps
- [ ] No contradictions between sections
- [ ] Zero placeholders: no "TBD", "TODO", "to be determined"
- [ ] No vague language: no "should probably", "might need", "could potentially"
- [ ] Edge cases covered: empty data, max data, concurrent access per area
- [ ] Every UI area has a concrete BDU test scenario with click-by-click steps
- [ ] BDU scenarios cover: entry point, happy path, failure modes, mobile
- [ ] Architecture aligns with the project's existing patterns

If issues found: present with proposed fixes.

## After the Design

1. Write to `docs/plans/YYYY-MM-DD-<topic>-design.md`
2. Commit the design document
3. Invoke the **writing-plans** skill to create the implementation plan

**The terminal state is invoking writing-plans.** Do NOT invoke any implementation skill.

## Key Principles

- **One question at a time** — non-negotiable
- **Multiple choice preferred** — easier to answer
- **Challenge assumptions** — ask "why?" before accepting
- **YAGNI ruthlessly** — remove unnecessary features
- **Evidence over opinion** — weave research into the dialogue
- **Incremental validation** — present, get approval, move on
- **Reject "it should be fine"** — require concrete answers for edge cases
- **Context budget** — summarize research, targeted codebase reads, checkpoint to disk

## Red Flags — You Are Violating This Skill If:

| What You Are Doing | What To Do Instead |
|---|---|
| Asking multiple questions in one message | One question, then STOP |
| Proposing solutions during discovery | Only questions until Phase 5 |
| Skipping to architecture before areas are done | You don't understand the problem yet |
| Accepting "it should be fine" | That's a hope, not a spec |
| Writing the full spec at once | One section at a time |
| Moving to next area without summary approval | Get confirmation first |
| Ignoring research findings | Weave them into your questions |
| Not challenging weak answers | Push back with concrete scenarios |
| Pasting full articles or files into context | Summarize into 3-5 bullets |
| Reading entire files to "understand" | Grep first, read only relevant lines |
| No BDU test scenario for a UI area | Design is too vague — define the click path |