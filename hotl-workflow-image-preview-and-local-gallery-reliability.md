---
intent: Improve remote/local folder image preview reliability and confirm Local Photos navigation remains populated after recent media-preview changes.
success_criteria: Folder image previews load reliably during root/subfolder browsing, Local Photos opens into populated folders/media on device, and local build/tests still pass.
risk_level: low
auto_approve: true
dirty_worktree: allow
---

## Steps

- [x] **Step 1: Tighten the bug hypothesis and document the target change**
action: Save the HOTL design context for this intermittent image-preview/local-gallery regression so execution stays scoped to reliability, not unrelated navigation refactors.
loop: false
verify:
  type: artifact
  path: docs/plans/2026-04-10-image-preview-and-local-gallery-reliability-design.md
  assert:
    kind: exists

- [x] **Step 2: Improve folder-preview image request reliability**
action: Update WebDavImageLoader folder-preview request options so folder image thumbnails are less likely to fail under concurrent loading, without changing video-thumbnail behavior.
loop: false
verify: Select-String -Path app/src/main/java/erl/webdavtoon/WebDavImageLoader.kt -Pattern 'isFolderPreview|timeout|priority'

- [x] **Step 3: Run local verification and build export**
action: Re-run Rust/Kotlin/build verification after the reliability tweak and ensure the root debug APK export still exists.
loop: false
verify:
  - type: shell
    command: cargo test --manifest-path rust-core/Cargo.toml
  - type: shell
    command: .\gradlew.bat :app:compileDebugKotlin
  - type: shell
    command: .\gradlew.bat :app:assembleDebug
  - type: artifact
    path: webdavtoon-debug.apk
    assert:
      kind: exists

- [x] **Step 4: ADB verify remote image folders and Local Photos**
action: Install the latest debug build and verify on device that WebDAV image-folder previews render on the root and a nested image-heavy folder, and that Local Photos plus a local child folder/media screen are populated.
loop: false
verify:
  type: artifact
  path: .artifacts/adb/2026-04-10
  assert:
    kind: exists

## Verification Notes

- Root WebDAV image previews:
  - `.artifacts/adb/2026-04-10/verify_images_root_after_fix.png`
- Nested WebDAV image-folder previews:
  - `.artifacts/adb/2026-04-10/verify_images_eh_after_fix.png`
- Local Photos root and subfolder screens:
  - `.artifacts/adb/2026-04-10/final_local_root_after_permission.png`
  - `.artifacts/adb/2026-04-10/final_local_subfolders_after_permission.png`
- Local child media page no longer empty after granting the missing permission:
  - `.artifacts/adb/2026-04-10/final_local_media_after_permission.png`
  - `.artifacts/adb/2026-04-10/final_local_media_after_permission.xml`
