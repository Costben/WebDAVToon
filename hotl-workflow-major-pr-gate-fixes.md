---
intent: Fix the blocking Major PR gate regressions in folder video thumbnails and VideoPlayer immersive system-bar handling.
success_criteria: WebDAV/local folder video previews no longer settle on blank-black frames for the tested scenes, VideoPlayer hides both status and navigation bars during playback, and the targeted gate scenes pass local compile/tests plus ADB spot-checks.
risk_level: medium
auto_approve: true
dirty_worktree: allow
---

## Steps

- [x] **Step 1: Write focused thumbnail heuristic tests**
action: Add a small JVM test suite for video-thumbnail frame heuristics so blank/near-monochrome frames can be rejected before they land in folder previews.
loop: false
verify:
  type: artifact
  path: app/src/test/java/erl/webdavtoon/VideoThumbnailHeuristicsTest.kt
  assert:
    kind: exists

- [x] **Step 2: Drive thumbnail selection from RED to GREEN**
action: Run the new thumbnail heuristic tests to get a RED failure, then implement the minimal WebDavImageLoader/helper changes to skip blank frames and sample later timestamps until the tests pass.
loop: until thumbnail heuristic tests pass
max_iterations: 3
verify:
  type: shell
  command: .\gradlew.bat :app:testDebugUnitTest --tests erl.webdavtoon.VideoThumbnailHeuristicsTest --console=plain

- [x] **Step 3: Fix VideoPlayer immersive system-bar policy**
action: Patch VideoPlayerActivity so playback hides full system bars (status + navigation), reapplies immersive mode after controller/lifecycle transitions, and keeps the custom chrome usable without letting the system bars stick around.
loop: until compile succeeds
max_iterations: 3
verify:
  type: shell
  command: .\gradlew.bat :app:compileDebugKotlin --console=plain

- [x] **Step 4: Run targeted regression checks**
action: Run the Android unit-test suite and Rust tests to confirm the focused fixes did not regress the current codebase.
loop: false
verify:
  - type: shell
    command: .\gradlew.bat :app:testDebugUnitTest --console=plain
  - type: shell
    command: cargo test --manifest-path rust-core/Cargo.toml

- [x] **Step 5: Rebuild debug APK for device validation**
action: Assemble a fresh debug APK and confirm the root export copy still exists for device installation.
loop: false
verify:
  - type: shell
    command: .\gradlew.bat :app:assembleDebug --console=plain
  - type: artifact
    path: webdavtoon-debug.apk
    assert:
      kind: exists

- [x] **Step 6: Perform targeted ADB gate spot-checks**
action: Install the rebuilt APK on `192.168.31.216:5555`, re-check the failing WebDAV/local folder thumbnail scenes and the remote/local video player immersive scenes, then save screenshots/UI dumps/logcat/gfxinfo evidence under the dated artifacts directory.
loop: false
verify:
  type: artifact
  path: .artifacts/adb/2026-04-10/major-pr-release-preflight
  assert:
    kind: exists
