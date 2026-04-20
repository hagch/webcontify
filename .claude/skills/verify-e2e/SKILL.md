---
name: verify-e2e
description: >
  Use after implementation to verify the feature works end-to-end as a real
  user would experience it. Runs the app, opens a real browser, navigates as
  a BDU (Brain-Dead User), checks auth flows, tests error states, and validates
  on mobile viewport. Catches the bugs that unit tests miss. Triggers: feature
  done, verify it works, test end-to-end, "does it actually work", pre-merge
  check, after executing-plans or subagent-driven-development completes.
---

# Verify E2E — Does It Actually Work?

Run the application and verify the feature works end-to-end **in a real browser**.
This catches what unit tests miss: broken auth, wrong routes, missing permissions,
state management bugs, mobile breakage. Think like a BDU (Brain-Dead User) —
non-technical, impatient, reads nothing.

## When To Use

- After implementation is complete (executing-plans or subagent-driven-development done)
- Before finishing-a-development-branch
- When you suspect "it passes tests but doesn't actually work"
- As a manual verification step at any point

## Iron Law

**No verification without a real browser.** Reading code is not verification.
Passing unit tests is not verification. You must open a browser, navigate to the
feature, and interact with it visually. Use browser automation tools (Playwright,
`--chrome` flag, or equivalent) to actually click through the UI.

## Input: BDU Test Script

If the design spec contains a **BDU Test Script** section (produced by brainstorm-deep),
read it first. Those scenarios are your primary test script — they define exactly what
to click, what to expect, and what failure modes to try. Run every scenario from that
script in the browser.

If no BDU Test Script exists, construct your own scenarios from the spec and plan.

## Pre-Flight

Before starting verification:

1. **Run the full test suite** — paste the output. If tests fail, STOP. Fix tests first.
2. **Check Playwright E2E tests exist** — the design spec's BDU Test Script defines
   scenarios marked `Playwright E2E: YES`. Verify that automated tests were actually
   written for each one. If any are missing: BLOCKER finding before you even open the
   browser. The implementation is incomplete.
3. **Run the E2E test suite specifically** — `npx playwright test` or project equivalent.
   Paste the output. All BDU scenarios must have corresponding passing tests.
4. **Start the application** — use the project's dev server command
5. **Open a browser** — use Playwright, `--chrome`, or the project's browser automation
6. **Verify the app is running** — navigate to the main page, confirm it loads
7. **Identify the test user** — what credentials/role/tenant? Check seed data or fixtures.
   If multi-tenant: log in as a tenant user, NOT as admin (admin hides auth bugs)

If you cannot start the app or open a browser: document WHY (missing env vars,
broken config, no `--chrome` flag, dependency issues) and escalate. Do not
pretend to verify.

## Verification Passes

Run all 6 passes **in the browser**. Take note of what you see on screen at each step.
Document findings as you go.

### Pass 1: Can You Get There?

**The #1 cause of "it doesn't work" bugs.**

1. Log in as the appropriate user/role/tenant
2. Navigate to the feature using ONLY the UI (not by typing the URL)
3. Can you find it? Is there a menu entry, link, or button?
4. Click through to the feature
5. **If you get a 403/401/404**: this is a BLOCKER. Document:
    - The URL you tried to reach
    - The user/role you're logged in as
    - The exact error response
    - What permission/route/guard is likely missing

### Pass 2: Happy Path (BDU Test Script)

If a BDU Test Script exists in the design spec, **run every scenario from it now**.
For each scenario: follow the exact steps, verify the expected outcome, note deviations.

If no BDU Test Script exists, construct the happy path yourself:

1. Fill in forms with realistic data (not "test" and "123")
2. Submit and verify the success feedback
3. Verify data persistence — refresh the page, is it still there?
4. Verify data appears correctly in list views, detail views, related views
5. Check that all UI states work: loading → loaded → empty → populated → error

For each step: document what you did, what you expected, what happened.

### Pass 3: Error States

Deliberately break things:

1. Submit forms with missing required fields — are errors shown near the right fields?
2. Submit with invalid data (wrong format, too long, negative numbers)
3. Trigger server errors if possible (invalid IDs in URLs, concurrent modifications)
4. Check: are error messages human-readable? Can the user recover without starting over?
5. **Auto-save / optimistic UI**: If the feature uses auto-save or real-time updates:
    - Open in two tabs — does it handle concurrent edits correctly?
    - Lose network mid-save — does it recover?
    - Does it show "saved" / "saving" / "conflict" states correctly?

### Pass 4: Auth & Permissions

If the feature has role-based access:

1. Test with EACH relevant role — can they see what they should? Can't see what they shouldn't?
2. Test cross-tenant access (if multi-tenant) — can tenant A see tenant B's data?
3. Test unauthenticated access — navigate directly to the URL while logged out
4. Test with expired/invalid tokens if applicable

### Pass 5: Mobile & Responsive

1. Resize the browser viewport to 375px width (or use device emulation)
2. Navigate to the feature
3. Can you complete the full flow? All buttons/inputs reachable?
4. No horizontal scroll? No elements cut off?
5. Touch targets large enough (44px minimum)?
6. Modals/dropdowns usable on small screen?

### Pass 6: Edge Cases

1. **Long text** — paste 2000+ characters into text fields
2. **Special characters** — emoji, `<script>` tags, Unicode
3. **Empty state** — what does the feature look like with zero data?
4. **Rapid actions** — double-click submit buttons, rapidly navigate back/forward
5. **Browser back/forward** — does the state stay consistent?
6. **Refresh mid-flow** — does a multi-step process survive a page refresh?

## Finding Format

```markdown
### [BLOCKER|HIGH|MEDIUM|LOW] <Title>
- **Pass:** 1-6 (which pass found this)
- **Steps to reproduce:** <exact clicks/actions>
- **Expected:** <what should happen>
- **Actual:** <what happened — include error messages verbatim>
- **Screenshot/evidence:** <describe what the screen shows>
- **Fix hint:** <your best guess at the cause — wrong route? missing permission? state bug?>
```

## Severity Guide

| Severity | Criteria |
|----------|----------|
| BLOCKER | Cannot reach the feature, data loss, security breach, crash on happy path |
| HIGH | Major flow broken, confusing errors, mobile unusable, auth bypass |
| MEDIUM | Minor flow issues, cosmetic errors, edge case failures |
| LOW | Polish issues, minor UX friction, non-critical edge cases |

## Verification Report

```markdown
## E2E Verification Report

### Environment
- **App URL:** <local dev URL>
- **User/Role:** <who you logged in as>
- **Tenant:** <if multi-tenant>
- **Viewport:** Desktop (1280px) + Mobile (375px)

### Results
| Pass | Name | Verdict |
|------|------|---------|
| Pre | Playwright E2E tests exist | PASS / FAIL |
| Pre | E2E test suite green | PASS / FAIL |
| 1 | Can You Get There? | PASS / FAIL |
| 2 | Happy Path | PASS / FAIL |
| 3 | Error States | PASS / FAIL |
| 4 | Auth & Permissions | PASS / FAIL |
| 5 | Mobile & Responsive | PASS / FAIL |
| 6 | Edge Cases | PASS / FAIL |

### Test Suite
- **Command:** `<exact command>`
- **Result:** X passed, Y failed, Z skipped
- **Evidence:** <paste summary line>

### Findings
<all findings sorted by severity>

### Verdict: PASS | FAIL
```

## After Verification

- If PASS: invoke **finishing-a-development-branch** skill
- If FAIL: present findings. Developer fixes issues, then re-run ONLY the failed passes.
  Max 2 fix rounds, then escalate to user.

## Red Flags

| What You Are Doing | Why It Is Wrong |
|---|---|
| "Verifying" by reading code | You must open a BROWSER |
| No browser automation available and pretending anyway | Escalate — say you need `--chrome` or Playwright |
| Skipping Pass 1 (Can You Get There?) | This catches 50% of real bugs |
| Testing only the happy path | Edge cases and errors are where users suffer |
| Not testing as the right user/role/tenant | Auth bugs are invisible with admin access |
| Claiming PASS without describing what you SAW | No visual evidence = no verification |
| Skipping mobile viewport | Many users are on mobile |
| Testing with "test123" data | Use realistic data to catch length/format issues |
| Not running BDU Test Script scenarios | They were defined in the design for a reason |