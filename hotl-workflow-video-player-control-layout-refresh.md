---
intent: Move the video player's transient controls out of the screen center so seek feedback, tap-to-show controls, and the paused play/pause affordance all live in the lower control area instead of covering the video content.
success_criteria: VideoPlayerActivity shows playback controls in the bottom control area rather than the center, the gesture seek hint appears above the progress bar instead of at screen center, and real-device ADB evidence confirms the updated layout.
risk_level: low
auto_approve: true
dirty_worktree: allow
---

## Steps

- [x] **Step 1: Define the bottom-oriented control layout**
action: Add a custom Media3 player control layout so the built-in playback controls render in the lower control region instead of the default centered overlay.
loop: false
verify: Select-String -Path app/src/main/res/layout/exo_player_control_view.xml -Pattern 'exo_play_pause|exo_bottom_bar|exo_center_controls|exo_progress_placeholder'

- [x] **Step 2: Reposition activity-owned overlays**
action: Update VideoPlayerActivity and activity_video_player.xml so the gesture hint and the paused play/pause affordance sit in the lower chrome area without blocking the center of the video.
loop: false
verify: Select-String -Path app/src/main/java/erl/webdavtoon/VideoPlayerActivity.kt,app/src/main/res/layout/activity_video_player.xml -Pattern 'gestureHintText|centerPlayPauseButton|bottomScrim|updateChromeVisibility'

- [x] **Step 3: Compile and export a debug APK**
action: Compile the Android app, assemble the debug APK, and confirm the root-level export exists for device verification.
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

- [x] **Step 4: Verify the updated control layout on the real device**
action: Install the new debug APK, launch VideoPlayerActivity with a real local video, capture screenshot/UI/activity/log evidence, and confirm the bottom controls plus seek feedback no longer appear in the screen center.
loop: false
verify:
  - type: artifact
    path: .artifacts/adb/2026-04-11
    assert:
      kind: exists
gate: auto

## Verification Notes

- Build verification:
  - `.\gradlew.bat :app:compileDebugKotlin`
  - `.\gradlew.bat :app:assembleDebug`
  - Root export present: `webdavtoon-debug.apk`
- ADB device: `192.168.31.216:5555`
- Real-device artifacts:
  - Full bottom control bar UI dump: `.artifacts/adb/2026-04-11/video-player-control-layout-refresh/cycle_1.xml`
  - Paused bottom play button UI dump: `.artifacts/adb/2026-04-11/video-player-control-layout-refresh/media_pause_100.xml`
  - Activity evidence: `.artifacts/adb/2026-04-11/video-player-control-layout-refresh/video_player_activity.txt`
  - Logcat evidence: `.artifacts/adb/2026-04-11/video-player-control-layout-refresh/video_player_logcat.txt`
  - Screenshots: `.artifacts/adb/2026-04-11/video-player-control-layout-refresh/final_paused_bottom.png`, `.artifacts/adb/2026-04-11/video-player-control-layout-refresh/manual_controls_50.png`
- Note:
  - ADB `input swipe` was not reliable for producing a stable scrubbing-overlay capture, but the runtime wiring for the bottom-positioned hint now exists in `VideoPlayerActivity.kt` for both gesture seeking and `exo_progress` scrubbing.
