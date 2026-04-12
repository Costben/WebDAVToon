---
intent: Update the Major PR Gate policy so the current external-player product direction is treated as valid video behavior, instead of requiring the legacy in-app VideoPlayerActivity route.
success_criteria: The gate design doc, checklist, and AGENTS memory consistently accept external-player handoff or chooser routing as valid video behavior and no longer require in-app player-only immersive/control checks.
risk_level: low
auto_approve: true
dirty_worktree: allow
---

## Steps

- [ ] **Step 1: Align policy wording with current product direction**
action: Update the Major PR Gate design/checklist/agent memory so video taps are valid when they hand off to an external player or Android chooser, while still forbidding accidental `PhotoViewActivity` routing.
loop: false
verify:
  - type: shell
    command: rg -n "VideoPlayerActivity|external player|ResolverActivity|PhotoViewActivity" AGENTS.md docs/plans/2026-04-10-major-pr-test-gate-design.md docs/plans/2026-04-10-major-pr-test-gate-checklist.md

- [ ] **Step 2: Recheck consistency**
action: Re-read the modified sections and confirm the gate no longer mixes legacy internal-player-only requirements with the current external-player policy.
loop: false
verify:
  type: shell
  command: Get-Content docs/plans/2026-04-10-major-pr-test-gate-design.md,docs/plans/2026-04-10-major-pr-test-gate-checklist.md,AGENTS.md
