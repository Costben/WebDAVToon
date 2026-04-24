# WebDAVToon Agent Memory

Last updated: 2026-04-10

## Project basics
- Android package: `erl.webdavtoon`
- Stack: Kotlin Android app + Rust core via UniFFI

## Mandatory verification policy
- For this project, **ADB real-device verification is required after code changes**.
- Do not claim a fix is complete with build/test results alone when the change affects runtime behavior, UI, media loading, navigation, caching, or WebDAV behavior.

## Required post-change checklist
1. Run the relevant local verification first.
   - Rust changes: `cargo test --manifest-path rust-core/Cargo.toml`
   - Android changes: at minimum `.\gradlew.bat :app:compileDebugKotlin`
2. Perform ADB real-device verification.
   - Preferred connected device when available: `192.168.31.216:5555`
   - This device can be connected directly over ADB without extra pairing flow in normal project work
   - This device has no lock-screen password; if the screen is off or keyguard is showing, wake/unlock directly via ADB
   - Preferred wake/unlock sequence:
     - `adb -s 192.168.31.216:5555 shell input keyevent 224`
     - `adb -s 192.168.31.216:5555 shell input keyevent 82`
   - Install latest debug build to device
   - Launch `erl.webdavtoon/.FolderViewActivity`
   - Capture evidence from `logcat`
3. For UI or interaction changes, also collect at least one of:
   - screenshot
   - UI hierarchy dump
   - activity state / dumpsys evidence

## Recommended log tags
- `FolderAdapter`
- `RustWebDavPhotoRepo`
- `FolderViewActivity`
- `SubFolderActivity`
- `WebDavImageLoader`
- `WebDAVToon`

## Completion rule
- Before reporting success, include what was verified on the real device.
- If the device is locked or visual validation is blocked, say so explicitly and continue with whatever ADB evidence is still available.

## Artifact storage rule
- Do not place ADB screenshots, UI hierarchy dumps, pulled temp DB files, or other one-off debug artifacts in the repository root.
- Store them under `.artifacts/adb/` (prefer a dated subdirectory such as `.artifacts/adb/2026-04-10/`).

## APK export rule
- After producing a new debug APK for verification or delivery, also place a copy in the project root as `webdavtoon-debug.apk` so it is easy for the user to download directly.
- After producing a new release APK for verification or delivery, also place a copy in the project root as `webdavtoon-release.apk` so it is easy for the user to download directly.
- Keep generated root-level APK exports ignored by git.

## HOTL workflow rule for incoming requests
- For any new **feature request, behavior change request, or bug-fix request** that is likely to change code, use the HOTL method first.
- Default rule: **write or update a `hotl-workflow-*.md` workflow before implementing**.
- For larger work (new features, significant refactors, architectural changes), use the normal HOTL flow: design -> workflow -> execution.
- For simpler work (small feature requests, straightforward bug fixes, narrow regressions), a **lightweight HOTL workflow** is still required, but it can be short and focused on the exact user-reported scope.
- Do not skip the workflow step for code-changing bug/feature requests unless the user explicitly asks for direct ad-hoc handling and the change is truly trivial.

## Verification scope rule for simple requests / bugs
- Simple feature requests and simple bug fixes **do not require the full Major PR Gate** by default.
- For those smaller items, verification should be **targeted to the user's reported requirement/bug scope**:
  - verify the exact behavior the user asked about
  - run the relevant local build/test command(s)
  - if runtime/UI/media/navigation/WebDAV behavior changed, still perform ADB real-device verification, but only for the affected scenario(s)
- The full **Major PR preflight gate** is reserved for **major version PRs / large PRs** with user-perceptible changes, or when the user explicitly asks to run it.

## Major PR preflight gate
- Before any **major version PR / large PR** that includes **user-perceptible changes**, run the **Major PR Gate v1** in addition to the normal post-change verification above.
- User-perceptible changes include UI, thumbnails, media loading, video playback, navigation, caching, WebDAV behavior, and visible performance changes.
- Gate result is strict: **any FAIL blocks the PR**. Only `PASS`, `FAIL`, or `INVALID` are allowed.
- Fixed device/environment for the gate:
  - Preferred device: `192.168.31.216:5555`
  - Install the latest `webdavtoon-debug.apk`
  - Store artifacts under `.artifacts/adb/<date>/major-pr-<slug>/`
- Required scene coverage:
  - WebDAV root folder page
  - WebDAV nested folder chain through 3 child levels
  - Local Photos root
  - Local nested folder chain through 2 child levels
  - Remote video list
  - Remote video playback
  - Local video playback
- Path strategy:
  - Root entry points are fixed
  - Thumbnail tests use image-dense folders
  - Video tests use video directories
  - Each core scene must be executed twice: first-entry run + back/re-enter run
- Core pass criteria:
  - Folder thumbnail completeness/correctness at every required level
  - Load time thresholds pass for both runs
  - Second run must not be slower than the first by more than `1.0s`
  - Jank thresholds pass
  - Video taps must never route to `PhotoViewActivity`
  - Video taps may validly hand off to an external player activity or Android chooser, per the current product direction
  - For current external-player flows, video handoff timing, valid target detection, return-path sanity, and error budget must all pass
- Required evidence:
  - screenshot
  - UI hierarchy dump
  - `dumpsys activity activities`
  - `logcat`
  - `dumpsys gfxinfo ... framestats`
  - for video scenes also capture `dumpsys window windows` and `dumpsys audio`
- Use `docs/plans/2026-04-10-major-pr-test-gate-design.md` as the authoritative detailed threshold/spec document when executing this gate.
- Use `docs/plans/2026-04-10-major-pr-test-gate-checklist.md` as the operator checklist when actually running the gate.
