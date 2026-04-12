---
intent: Make waterfall-mode video thumbnails use the extracted frame's real aspect ratio instead of a forced uniform square ratio.
success_criteria: Waterfall video cards render with variable heights matching the thumbnail frame aspect ratio, folder preview video tiles remain square, and ADB evidence confirms the updated layout on the preferred device.
risk_level: low
auto_approve: true
dirty_worktree: allow
---

## Steps

- [ ] **Step 1: Preserve normal video thumbnail aspect ratio**
action: Update `WebDavImageLoader` so only folder preview video thumbnails are square-cropped, scale normal video thumbnails with preserved aspect ratio, invalidate stale cached square thumbnails, and update `PhotoAdapter` so normal video cards no longer force crop mode.
loop: false
verify: Select-String -Path app/src/main/java/erl/webdavtoon/WebDavImageLoader.kt,app/src/main/java/erl/webdavtoon/PhotoAdapter.kt -Pattern 'VIDEO_BITMAP_CACHE_VERSION|scaleBitmapPreservingAspectRatio|cropCenterSquare|FIT_CENTER|adjustViewBounds = true'

- [ ] **Step 2: Compile and export debug APK**
action: Compile the Android app, assemble the debug APK, and verify the root-level APK export exists for device validation.
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

- [ ] **Step 3: Validate waterfall video thumbnail layout on the real device**
action: Install the latest debug build on `192.168.31.216:5555`, open a waterfall media page with portrait videos, and collect screenshot/log/activity evidence showing video card heights now follow the thumbnail aspect ratio instead of a forced uniform crop.
loop: false
verify:
  - type: artifact
    path: .artifacts/adb/2026-04-11/waterfall-video-thumbnail-aspect-ratio
    assert:
      kind: exists
gate: auto
