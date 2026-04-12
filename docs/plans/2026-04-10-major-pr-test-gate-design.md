---
intent: Establish a mandatory pre-PR real-device gate for major user-visible changes so thumbnail, performance, navigation, and video regressions are blocked before large merges.
constraints:
  - Use ADB real-device verification on `192.168.31.216:5555`
  - Apply only to user-perceptible changes
  - Use absolute thresholds instead of relative baselines
  - Persist all evidence under `.artifacts/adb/<date>/major-pr-<slug>/`
success_criteria:
  - Every major PR run produces a PASS/FAIL/INVALID report with stored evidence
  - Thumbnail hierarchy, load time, frame-time variance, external-video handoff, and error budget all have explicit gates
  - Any failing gate blocks the PR
risk_level: medium

verification_contract:
  verify_steps:
    - run build: .\gradlew.bat :app:assembleDebug --console=plain
    - run tests: .\gradlew.bat :app:testDebugUnitTest --console=plain
    - run tests: cargo test --manifest-path rust-core/Cargo.toml
    - install apk: adb -s 192.168.31.216:5555 install -r .\webdavtoon-debug.apk
    - check: execute the fixed major-PR scene matrix with two runs per core scene
    - check: capture screenshots, UI dumps, activity dumps, logcat, framestats, and video-specific window/audio evidence
    - confirm: every gate item reports PASS

governance_contract:
  approval_gates:
    - Any threshold change requires user approval
    - Any scenario-matrix change requires user approval
  rollback: Revert this policy document and AGENTS memory update if the gate definition is replaced by a newer approved version
  ownership: Codex executes the gate and reports results; user approves policy changes
---

# 2026-04-10 Major PR Test Gate Design

## Summary

This document defines **Major PR Gate v1** for WebDAVToon. Before any major version PR / large PR containing user-perceptible changes, Codex must run a fixed real-device gate and produce a PASS/FAIL/INVALID report. Any FAIL blocks the PR.

## Trigger Conditions

Run this gate before a major PR when the change affects any user-visible behavior, including:

- UI
- folder/media thumbnails
- media loading
- video playback
- navigation
- caching
- WebDAV behavior
- visible performance characteristics

Do not require this gate for purely internal changes such as docs-only edits, comments, or non-user-visible refactors.

## Result Types

- `PASS`: every required gate passed
- `FAIL`: any required gate failed; PR is blocked
- `INVALID`: environment/test-run invalid; rerun required, cannot be treated as pass

## Fixed Environment

- Device: `192.168.31.216:5555`
- APK: latest root-level `webdavtoon-debug.apk`
- Verification mode: ADB real-device only
- Artifact directory: `.artifacts/adb/<YYYY-MM-DD>/major-pr-<slug>/`

## Path Strategy

### Root policy

- Root entry points are fixed
- Subdirectory selection is **semi-fixed**

### Thumbnail-path sampling

- Thumbnail checks prioritize **image-dense folders**
- WebDAV thumbnail chain must cover:
  - root
  - level 1 child
  - level 2 child
  - level 3 child
- Local thumbnail chain must cover:
  - local root
  - level 1 child
  - level 2 child

### Video-path sampling

- Video checks use fixed video-directory entry paths
- Remote video path must validate list page -> external handoff route
- Local video path must validate list page -> external handoff route

## Two-Run Execution Rule

Every core scene is executed twice:

1. **Run 1: first entry**
2. **Run 2: back/re-enter verification**

### Additional hard rule

Even if absolute thresholds still pass, **Run 2 must not be slower than Run 1 by more than `1.0s`**. If it is, the scene fails.

## Scene Matrix

The gate must cover all of the following:

1. WebDAV root folder page
2. WebDAV nested folder chain through 3 child levels
3. Local Photos root
4. Local nested folder chain through 2 child levels
5. Remote video list
6. Remote video playback
7. Local video playback

## Timing Endpoints

### Folder / thumbnail page

Stop timing when:

- the target visible folder-card thumbnails have appeared
- there is no obvious continuing patch-in / flicker / jump
- the page remains visually stable for at least `0.5s`

### Media list page

Stop timing when:

- target visible media cards are present
- video card thumbnail / video badge is visible
- the page scrolls normally without obvious blocking

### Video handoff page

Track:

- **handoff time**: until either a valid external player activity or Android chooser is foreground

The gate uses **handoff time** as the primary video-routing performance criterion.

## Gate Thresholds

### WebDAV thumbnail load thresholds

| Scene | Run 1 | Run 2 |
|---|---:|---:|
| WebDAV root thumbnail stability | <= 5.0s | <= 2.5s |
| WebDAV level 1 thumbnail stability | <= 5.5s | <= 3.0s |
| WebDAV level 2 thumbnail stability | <= 6.0s | <= 3.0s |
| WebDAV level 3 thumbnail stability | <= 6.5s | <= 3.5s |

### Local thumbnail load thresholds

| Scene | Run 1 | Run 2 |
|---|---:|---:|
| Local root thumbnail stability | <= 2.0s | <= 1.5s |
| Local level 1 thumbnail stability | <= 2.0s | <= 1.5s |
| Local level 2 thumbnail stability | <= 2.5s | <= 1.5s |

### Video-list thresholds

| Scene | Run 1 | Run 2 |
|---|---:|---:|
| Remote video list usable | <= 4.0s | <= 2.5s |
| Local video list usable | <= 2.0s | <= 1.5s |

### Video handoff thresholds

| Scene | Run 1 | Run 2 |
|---|---:|---:|
| Remote video handoff | <= 3.0s | <= 2.0s |
| Local video handoff | <= 1.5s | <= 1.0s |

## Thumbnail Gates

For every required thumbnail level:

- inspect the first 4 visible folder cards
- if fewer than 4 are visible, inspect all visible folder cards
- if a level has insufficient usable samples, choose the next compliant folder path; otherwise mark the scene invalid

### Pass criteria

- no obvious blank thumbnails
- no permanently missing thumbnails
- no wrong-thumbnail / stale-thumbnail / swapped-thumbnail behavior
- no unacceptable load-time failure

### Re-entry consistency

At least one deep required folder must also pass:

- enter
- back out
- re-enter the same folder

and show no white cards, wrong thumbnails, or re-entry instability.

## Frame-Time / Jank Gates

### Folder pages and media-list pages

- 95th percentile frame time `<= 40ms`
- frames above `100ms` `<= 1` per scene
- no visible freeze longer than `0.5s`

### Video handoff scenes

- 95th percentile frame time `<= 50ms`
- no visible freeze longer than `0.5s`

## Video Experience Gates

### Routing

- tapping a video card must never open `PhotoViewActivity`
- tapping a video card may validly result in either:
  - an external player activity outside `erl.webdavtoon`
  - `android/com.android.internal.app.ResolverActivity` when chooser mode is expected

### Valid handoff targets

- If launch mode is **system default external player**:
  - a non-`erl.webdavtoon` player activity must take foreground within threshold
  - the external activity must remain stable for at least `0.5s`
- If launch mode is **chooser**:
  - `ResolverActivity` must appear within threshold
  - at least one capable external handler must be listed
- In both modes:
  - `PhotoViewActivity` is always a failure
  - bouncing back to the app without any chooser/player handoff is a failure

### External-player policy note

Because the current product direction delegates playback to external handlers, the gate does **not** require in-app immersive UI, in-app controls, in-app gestures, or in-app seek recovery for major-PR acceptance.

### Return-path integrity

- After external handoff, returning to the app later must not crash the app
- If the app is resumed, the originating list should still be reachable without obvious corruption

### Error budget

Must all be zero:

- crashes
- ANRs
- fatal handoff errors
- repeated authentication failure loops during remote playback

Recommended logs to inspect:

- `ExternalVideoOpener`
- `ResolverActivity`
- `PhotoViewActivity`
- `RustWebDavPhotoRepo`
- `WebDAVToon`

## INVALID Conditions

Mark the run invalid and rerun if any of the following contaminate results:

- ADB disconnect
- obvious WebDAV service outage
- system popup materially interferes with the test
- device thermal/system instability
- insufficient usable sample path after applying path-selection rules

### Rerun rule

- A single scene may be rerun once after INVALID
- If it remains invalid, the whole gate result is `INVALID`

## Required Evidence

Each major-PR gate must save evidence in `.artifacts/adb/<date>/major-pr-<slug>/`.

Minimum required evidence:

- screenshot(s)
- UI hierarchy dump(s)
- `dumpsys activity activities`
- `logcat`
- `dumpsys gfxinfo ... framestats`

Additional required evidence for video scenes:

- `dumpsys window windows`
- `dumpsys audio`

Suggested filenames:

- `webdav-root.png`
- `webdav-level1.png`
- `webdav-level2.png`
- `webdav-level3.png`
- `local-root.png`
- `local-level1.png`
- `local-level2.png`
- `remote-video-list.png`
- `local-video-list.png`
- `remote-player.png`
- `local-player.png`
- `framestats-webdav-root.txt`
- `framestats-remote-player.txt`
- `player-window.txt`
- `player-audio.txt`

## Report Template

Every major-PR gate report should include:

- Overall: `PASS` / `FAIL` / `INVALID`
- WebDAV thumbnail chain: `PASS` / `FAIL`
- Local thumbnail chain: `PASS` / `FAIL`
- Load time: `PASS` / `FAIL`
- Frame-time variance: `PASS` / `FAIL`
- Video routing: `PASS` / `FAIL`
- Video handoff: `PASS` / `FAIL`
- Return path/state retention: `PASS` / `FAIL`
- Error budget: `PASS` / `FAIL`

Any single `FAIL` blocks the PR.
