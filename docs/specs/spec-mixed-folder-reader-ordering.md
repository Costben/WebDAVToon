## Objective

Make mixed folders expose recursive "Browse all", ensure every reader launch opens its intended image list and target without cross-list cache contamination, and allow recursive images to be arranged globally by modification time.

## Scope

- Add the mixed-folder FAB, with the established folder-page appearance and accessible label; it opens `MainActivity` for the current path with `EXTRA_RECURSIVE=true`.
- Create an explicit, source-identifiable reader-session snapshot used by `MainActivity` and `MixedFolderActivity` reader launches; restore index only within the same session after recreation.
- Prevent `PhotoViewActivity` and asynchronous `MediaManager` state from replacing a session-owned reader list.
- Add a persisted three-option recursive image arrangement setting, localized in English and Chinese.
- Preserve folder grouping by default; for recursive lists only, optionally sort all images globally by `Photo.dateModified` ascending or descending.
- Add JVM tests for global ordering, default grouping, and session target isolation.

## Out of Scope

- Changing non-recursive media/folder ordering, video playback behavior, or running the Major PR Gate.

## Constraints

- Preserve existing user work (worktree is clean before this change).
- Do not solve reader contamination by merely clearing global caches.
- A fresh reader Intent target takes precedence over stale recreation/global state.
- Complete targeted Android compilation/tests and requested ADB evidence under `.artifacts/adb/2026-07-27/mixed-folder-reader-ordering/`; export `webdavtoon-debug.apk` at repository root.

## Affected Files

- `MixedFolderActivity.kt`, `MainActivity.kt`, `PhotoViewActivity.kt`, `MediaManager.kt`
- New reader-session helper plus JVM tests
- `SettingsManager.kt`, `SettingsActivity.kt`, settings layout, and localized strings
- `activity_main.xml`, workflow, and this spec

## Acceptance Checks

- Mixed folder presents folders, images, and the Browse all FAB; all three click paths work.
- Mixed-folder and normal-reader taps open the intended image in both reader modes; mixed next/previous uses only direct images.
- A session-bound reader list cannot be replaced by earlier `MediaStateCache`, `PhotoCache`, or `MediaManager` callbacks; recreation restores only its own index.
- Recursive default preserves grouped order; global new-to-old and old-to-new are strictly timestamp ordered across folder paths.
- `./gradlew.bat :app:compileDebugKotlin` and relevant JVM tests pass.
- Latest debug APK is installed and target device scenarios/evidence are captured.

## Risks / Open Questions

- Device library data may not contain a mixed remote folder with two images and sufficiently distinct cross-folder timestamps; if absent, report the exact scenario that could not be exercised.
- Reader deletion currently updates legacy global caches; session ownership must remain authoritative while preserving deletion UI behavior.
