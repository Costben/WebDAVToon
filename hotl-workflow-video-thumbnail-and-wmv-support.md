---
intent: Support MKV/WMV thumbnail generation, route WMV playback through the external-player path, and export a root debug APK copy after builds.
success_criteria: n0780.mkv shows a thumbnail on device, WMV is recognized and opened through the external-player path, and a debug APK copy exists in the project root after build.
risk_level: medium
auto_approve: true
dirty_worktree: allow
---

## Steps

- [x] **Step 1: Update project memory and export contract**
action: Update AGENTS.md and git-ignore/build plumbing so future debug builds also export a root APK copy without polluting version control.
loop: false
verify: Select-String -Path AGENTS.md,.gitignore,app/build.gradle.kts -Pattern 'APK|apk|webdavtoon-debug.apk'

- [x] **Step 2: Extend format detection and playback routing**
action: Add WMV video detection/mime support and reuse the external-player path for containers that should not use the in-app player.
loop: false
verify: Select-String -Path app/src/main/java/erl/webdavtoon/MediaTypeUtils.kt,app/src/main/java/erl/webdavtoon/MainActivity.kt,app/src/main/java/erl/webdavtoon/VideoPlayerActivity.kt -Pattern 'wmv|external'

- [x] **Step 3: Harden thumbnail extraction for MKV and WMV**
action: Improve WebDavImageLoader fallback behavior so problematic containers can cache a local source copy with the original extension and extract thumbnails through FFmpeg when direct retrieval fails; add matching Rust-side media recognition for WMV.
loop: false
verify:
  - type: shell
    command: cargo test --manifest-path rust-core/Cargo.toml
  - type: shell
    command: .\gradlew.bat :app:compileDebugKotlin

- [x] **Step 4: Build and export debug APK**
action: Assemble the debug APK and confirm the root-level APK copy is produced.
loop: until exported debug APK exists
max_iterations: 3
verify:
  - type: shell
    command: .\gradlew.bat :app:assembleDebug
  - type: artifact
    path: webdavtoon-debug.apk
    assert:
      kind: exists

- [x] **Step 5: ADB real-device verification**
action: Install the latest debug build, verify the MKV thumbnail on device, and verify WMV follows the external-player route with logs/screenshots as evidence.
loop: false
verify:
  - type: artifact
    path: .artifacts/adb
    assert:
      kind: exists
gate: auto

## Verification Notes

- MKV thumbnail success evidence:
  - `.artifacts/adb/2026-04-10/iikoka_now.png`
  - `.artifacts/adb/2026-04-10/iikoka_now.xml`
- WMV external-player routing evidence:
  - `.artifacts/adb/2026-04-10/wmv_external_route.png`
  - `.artifacts/adb/2026-04-10/wmv_external_route.xml`
  - `.artifacts/adb/2026-04-10/wmv_external_route.log`
- Root APK export evidence:
  - `webdavtoon-debug.apk`
