---
intent: Extend VideoPlayerActivity so the top/bottom chrome stays visible longer after a tap, double-tap gestures control left/right seek and center play-pause with 37.5/25/37.5 regions, and the seek interval is configurable in settings as 5s/10s/15s.
success_criteria: Video player controls remain visible longer than before, left/right/center double taps work in the requested screen regions, settings exposes a selectable double-tap seek interval, and real-device ADB evidence confirms the updated behavior.
risk_level: low
auto_approve: true
dirty_worktree: allow
---

## Steps

- [x] **Step 1: Add configurable double-tap seek settings**
action: Extend settings storage, strings, and SettingsActivity so users can choose a video double-tap seek interval of 5, 10, or 15 seconds.
loop: false
verify: Select-String -Path app/src/main/java/erl/webdavtoon/AppSettingsStore.kt,app/src/main/java/erl/webdavtoon/SettingsManager.kt,app/src/main/java/erl/webdavtoon/SettingsActivity.kt,app/src/main/res/layout/activity_settings_md3.xml,app/src/main/res/values/strings.xml -Pattern 'double tap|double_tap|seek interval|video_double'

- [x] **Step 2: Implement longer controller timeout and double-tap playback gestures**
action: Update VideoPlayerActivity and the player layout so tap-shown chrome stays visible longer and hidden-state double taps use 37.5% left, 25% center, and 37.5% right regions for rewind, play/pause, and fast-forward actions.
loop: false
verify: Select-String -Path app/src/main/java/erl/webdavtoon/VideoPlayerActivity.kt,app/src/main/res/layout/activity_video_player.xml -Pattern 'controllerShowTimeout|doubleTap|DOUBLE_TAP|show_timeout|seekBy|37.5|25'

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

- [x] **Step 4: Validate timeout and double-tap gestures on the real device**
action: Install the debug build, open VideoPlayerActivity on a real video, verify longer control visibility plus left/right/center double taps, and capture screenshot/UI/activity/log evidence under .artifacts/adb/2026-04-11.
loop: false
verify:
  - type: artifact
    path: .artifacts/adb/2026-04-11
    assert:
      kind: exists
gate: auto

## Verification notes

- `.\gradlew.bat :app:assembleDebug` passed and exported `webdavtoon-debug.apk` at the repo root.
- Real-device ADB validation ran on `192.168.31.216:5555`.
- Settings verification:
  - `settings_after_swipe1.xml` shows the new `Double-tap seek interval` row.
  - `settings_seek_dialog.xml` shows the 5 / 10 / 15 second picker.
  - `settings_after_15.xml` confirms the setting was changed to `15 seconds`.
- Video verification:
  - Local sample video pushed to `/sdcard/Android/data/erl.webdavtoon/files/sample-10s.mp4` for stable playback.
  - `controls_t2p7.png` shows player chrome still visible about 2.7s after a tap.
  - `controls_t2.png` shows the chrome hidden again later, consistent with a longer timeout than the old 1800ms.
  - `video_gesture_log.txt` records:
    - left double tap rewind with `intervalSeconds=15`
    - center double tap pause/play
    - right double tap fast-forward with `intervalSeconds=15`
  - `video_activity_dumpsys.txt` confirms `VideoPlayerActivity` was the focused/resumed activity during validation.
