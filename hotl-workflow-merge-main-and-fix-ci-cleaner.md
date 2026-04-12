---
intent: Merge the latest mainline into the current release branch, then fix the CI failure caused by API 33-only `java.lang.ref.Cleaner#create` usage so Android builds pass again.
success_criteria: Current branch contains main, the Cleaner API 33 compatibility issue is removed, and local Kotlin/build verification passes.
risk_level: medium
auto_approve: true
dirty_worktree: allow
---

## Steps

- [x] **Step 1: Merge mainline**
action: Merge the latest local main branch into the current working branch to reduce divergence before fixing CI.
loop: false
verify:
  type: shell
  command: git log --oneline -1

- [x] **Step 2: Fix Cleaner API compatibility**
action: Replace direct API-33-only `java.lang.ref.Cleaner` calls in generated UniFFI Kotlin with reflection-safe access that still works on minSdk 24.
loop: until compile succeeds
max_iterations: 3
verify:
  type: shell
  command: .\gradlew.bat :app:compileDebugKotlin --console=plain

- [x] **Step 3: Re-run targeted release verification**
action: Confirm the release build path still works after the compatibility fix.
loop: false
verify:
  - type: shell
    command: .\gradlew.bat :app:assembleDebug --console=plain
