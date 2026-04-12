---
intent: Fix dark-mode contrast issues for the settings-page back icon and folder-card text so both day mode and dark mode render readable UI.
success_criteria: In dark mode the settings-page back icon is readable, folder card text uses adaptive colors instead of fixed hex values, day mode does not regress, and ADB evidence confirms the affected screens on the preferred device.
risk_level: low
auto_approve: true
dirty_worktree: allow
---

## Steps

- [ ] **Step 1: Apply theme-aware colors to the affected UI**
action: Update the settings toolbar/back icon and folder text resources so they use theme-aware colors, and make the settings-page navigation-bar icon appearance follow the current night-mode state.
loop: false
verify: Select-String -Path app/src/main/res/layout/activity_settings_md3.xml,app/src/main/res/layout/item_folder.xml,app/src/main/res/drawable/ic_arrow_back.xml,app/src/main/java/erl/webdavtoon/SettingsActivity.kt -Pattern 'navigationIconTint|titleTextColor|colorOnSurface|colorOnSurfaceVariant|textColorPrimary|isAppearanceLightNavigationBars'

- [ ] **Step 2: Run local verification and export APK**
action: Compile and assemble the Android app after the UI fixes, then confirm the root-level debug APK export exists for download.
loop: until the debug compile and assemble succeed
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

- [ ] **Step 3: Verify dark-mode UI on the preferred device**
action: Install the latest debug build on `192.168.31.216:5555`, switch to the affected dark-mode views, and collect screenshot/UI/activity/log evidence for the settings page and folder list.
loop: false
verify:
  - type: artifact
    path: .artifacts/adb/2026-04-12/dark-mode-toolbar-folder-text-fix
    assert:
      kind: exists
gate: auto
