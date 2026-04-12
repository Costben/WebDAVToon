---
intent: Fix external-player launching for remote WebDAV videos whose paths contain spaces, Chinese characters, or symbols so VLC receives a correctly encoded and authenticated URL.
success_criteria: Remote WebDAV videos with special characters in the path launch successfully to VLC or the configured external player, targeted tests cover the encoded URL case, and ADB evidence confirms the handoff works on the preferred device.
risk_level: low
auto_approve: true
dirty_worktree: allow
---

## Steps

- [ ] **Step 1: Fix remote external-player URL construction**
action: Update `ExternalVideoOpener` so remote WebDAV URLs are encoded before credential injection, produce an ASCII-safe external-player URL, and add a focused unit test that covers a path with Chinese characters, spaces, and `❤`.
loop: false
verify: Select-String -Path app/src/main/java/erl/webdavtoon/ExternalVideoOpener.kt,app/src/test/java/erl/webdavtoon/ExternalVideoOpenerTest.kt -Pattern 'buildRemoteLaunchUrl|toASCIIString|encodeWebDavUrl|XXR|%E2%9D%A4'

- [ ] **Step 2: Run local verification and export APK**
action: Run the targeted unit test, compile the Android app, assemble the debug APK, and confirm the root-level APK export exists.
loop: until the targeted test and debug build succeed
max_iterations: 3
verify:
  - type: shell
    command: .\gradlew.bat :app:testDebugUnitTest --tests erl.webdavtoon.ExternalVideoOpenerTest
  - type: shell
    command: .\gradlew.bat :app:compileDebugKotlin
  - type: shell
    command: .\gradlew.bat :app:assembleDebug
  - type: artifact
    path: webdavtoon-debug.apk
    assert:
      kind: exists

- [ ] **Step 3: Validate VLC handoff on device**
action: Install the latest debug build on `192.168.31.216:5555`, reproduce external playback for the special-character WebDAV path under `Videos/PMV/b站同衣服91v/`, and collect screenshot/activity/log evidence showing the external handoff succeeds.
loop: false
verify:
  - type: artifact
    path: .artifacts/adb/2026-04-11/external-player-webdav-special-char-fix
    assert:
      kind: exists
gate: auto
