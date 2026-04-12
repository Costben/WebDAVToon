---
intent: Fix the reported WebDAV AVI thumbnail regressions under Videos/JAV/饭冈かなこ by making AVI thumbnails use first-frame-first behavior instead of late content-frame hunting so they still load reliably on devices that only expose a short partial-cache duration.
success_criteria: The reported AVI thumbnails in the targeted Videos/JAV/饭冈かなこ scene load from a stable first-frame strategy on device, the placeholder white-line failure is removed, bandwidth-sensitive AVI fallback stays smaller than the generic large cached-video path, and local tests remain green.
risk_level: medium
auto_approve: true
dirty_worktree: allow
---

## Steps

- [x] **Step 1: Reproduce the two reported AVI thumbnail failures**
action: Use the connected device to open `Videos/JAV/饭冈かなこ`, capture the current thumbnail behavior for `112312_478.avi` and `011013_511.avi`, and save screenshot/UI dump/logcat evidence under the dated adb artifacts directory.
loop: false
verify:
  type: artifact
  path: .artifacts/adb/2026-04-11/jav-avi-thumbnails
  assert:
    kind: exists

- [x] **Step 2: Add focused thumbnail regression checks**
action: Add or update narrow JVM tests around the AVI thumbnail heuristics/selection logic so the reproduced failure mode is covered before implementation changes.
loop: until focused thumbnail tests pass
max_iterations: 3
verify:
  type: shell
  command: .\gradlew.bat :app:testDebugUnitTest --tests erl.webdavtoon.VideoThumbnailHeuristicsTest --console=plain

- [x] **Step 3: Patch AVI thumbnail extraction and cache handling**
action: Implement the minimal fix in the thumbnail loading path for the two reported AVI regressions, keeping the change scoped to thumbnail extraction/validation/cache behavior.
loop: until compile succeeds
max_iterations: 3
verify:
  type: shell
  command: .\gradlew.bat :app:compileDebugKotlin --console=plain

- [x] **Step 4: Run targeted regression validation**
action: Re-run the Android unit tests and Rust tests to confirm the AVI thumbnail fix does not regress the current codebase.
loop: false
verify:
  - type: shell
    command: .\gradlew.bat :app:testDebugUnitTest --console=plain
  - type: shell
    command: cargo test --manifest-path rust-core/Cargo.toml

- [x] **Step 5: Rebuild and verify on the real device**
action: Assemble/install the latest debug APK, re-open `Videos/JAV/饭冈かなこ`, confirm both reported AVI thumbnails are fixed on device, and capture screenshot/UI dump/logcat evidence for the exact scene.
loop: false
verify:
  - type: shell
    command: .\gradlew.bat :app:assembleDebug --console=plain
  - type: artifact
    path: webdavtoon-debug.apk
    assert:
      kind: exists
  - type: artifact
    path: .artifacts/adb/2026-04-11/jav-avi-thumbnails
    assert:
      kind: exists
