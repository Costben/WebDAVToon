---
intent: Remove in-app video playback from normal user flows so all videos open through an external player, and add a setting that chooses between the system default external player and the chooser dialog.
success_criteria: Tapping any video no longer launches the in-app player, settings exposes default external vs ask-every-time launch mode, and real-device ADB evidence confirms routing to an external player instead of VideoPlayerActivity.
risk_level: low
auto_approve: true
dirty_worktree: allow
---

## Steps

- [x] **Step 1: Add external-player launch mode setting**
action: Extend settings persistence, strings, layout, and SettingsActivity so video settings expose an external-player launch mode with system default and chooser options.
loop: false
verify: Select-String -Path app/src/main/java/erl/webdavtoon/AppSettingsStore.kt,app/src/main/java/erl/webdavtoon/SettingsManager.kt,app/src/main/java/erl/webdavtoon/SettingsActivity.kt,app/src/main/res/layout/activity_settings_md3.xml,app/src/main/res/values/strings.xml -Pattern 'external player|video_external|chooser|system_default'

- [x] **Step 2: Route all video launches to ExternalVideoOpener**
action: Update MainActivity, ExternalVideoOpener, and the legacy VideoPlayerActivity entry path so every video opens externally and the launch mode setting controls chooser vs system default behavior.
loop: false
verify: Select-String -Path app/src/main/java/erl/webdavtoon/MainActivity.kt,app/src/main/java/erl/webdavtoon/ExternalVideoOpener.kt,app/src/main/java/erl/webdavtoon/VideoPlayerActivity.kt -Pattern 'ExternalVideoOpener.open|createChooser|system default|VideoPlayerActivity'

- [x] **Step 3: Compile and export debug APK**
action: Compile the Android app, assemble the debug APK, and verify the root-level export exists for device validation.
loop: until debug build succeeds and APK is exported
max_iterations: 3
verify:
  - type: shell
    command: .\gradlew.bat :app:compileDebugKotlin
  - type: shell
    command: .\gradlew.bat :app:assembleDebug
  - type: artifact
    path: webdavtoon-debug.apk
    assert:
      kind: exists

- [x] **Step 4: Validate external-player routing on the real device**
action: Install the debug build, verify settings shows the new external-player mode option, tap a video on the preferred device, and capture ADB screenshot/UI/activity evidence showing routing to an external player/chooser instead of VideoPlayerActivity.
loop: false
verify:
  - type: artifact
    path: .artifacts/adb/2026-04-11
    assert:
      kind: exists
gate: auto

## Verification notes

- `.\gradlew.bat :app:compileDebugKotlin` passed.
- `.\gradlew.bat :app:assembleDebug` passed and exported `webdavtoon-debug.apk`.
- Real-device ADB verification ran on `192.168.31.216:5555`.
- Settings verification:
  - `ext_settings_video.xml` shows only the new `External player mode` row in the Video section.
  - The first settings capture shows the default summary as `Default external player`.
  - `ext_mode_dialog.xml` shows both choices: `Default external player` and `Ask every time`.
  - `ext_settings_after_chooser.xml` confirms the setting updates to `Ask every time`.
- Launch verification:
  - Initial external-only implementation exposed a `FileUriExposedException` for `file://` URIs during ADB validation.
  - Added `FileProvider` support via `app/src/main/AndroidManifest.xml` and `app/src/main/res/xml/file_paths.xml`.
  - After the fix, launching `VideoPlayerActivity` with a local sample video no longer stayed inside the app.
  - `external_launch_log.txt` records `ExternalVideoOpener: open mode=chooser`.
  - `activity_dumpsys_after_external_launch.txt` shows Android routed to `com.android.intentresolver/.ChooserActivity` with `clip={video/mp4 {U(content)}}`, proving the launch now uses a shareable content URI rather than `file://`.
