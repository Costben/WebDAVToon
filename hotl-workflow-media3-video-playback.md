---
intent: Introduce first-class local + WebDAV video support with Media3 playback while preserving existing folder hierarchy/preview logic parity with images.
success_criteria: Video files appear in folder cards and waterfall grids with the same hierarchy and 4-preview behavior as images; tapping a video opens a Media3 player page (not webtoon/photo mode) with gesture controls and seek UI; local and WebDAV playback both work in v1; settings contains a dedicated video section with placeholder options; verification matrix passes.
risk_level: medium
auto_approve: true
branch: hotl/media3-video-playback
dirty_worktree: allow
---

## Steps

- [x] **Step 1: Prepare reference workspace and ignore rules**
action: Clone `https://github.com/Kindness-Kismet/One-Player` into `reference/One-Player`, then update `.gitignore` to include `/reference/` so the reference repo never enters commits.
loop: false
verify:
  - type: artifact
    path: reference/One-Player
    assert:
      kind: exists
  - type: shell
    command: powershell -NoProfile -Command "& { git check-ignore -q reference/One-Player; if ($LASTEXITCODE -ne 0) { Write-Error 'reference/One-Player is not ignored'; exit 1 } }"

- [x] **Step 2: Define media model for image+video compatibility**
action: Update `app/src/main/java/erl/webdavtoon/Models.kt` to support media type and optional video metadata (at least duration), while keeping existing image flows backward compatible.
loop: until Kotlin compiles
max_iterations: 4
verify: ./gradlew.bat :app:compileDebugKotlin --console=plain

- [x] **Step 3: Add media-type and extension helper utilities**
action: Add/extend helpers under `app/src/main/java/erl/webdavtoon/` for image/video extension detection and shared media classification so local and WebDAV repositories reuse one rule set.
loop: until helper tests/build pass
max_iterations: 4
verify: ./gradlew.bat :app:compileDebugKotlin --console=plain

- [x] **Step 4: Upgrade local repository paging from image-only to mixed media**
action: Refactor `app/src/main/java/erl/webdavtoon/LocalPhotoRepository.kt` so `queryMediaPage` and `getPhotos` return both images and videos with correct sort, search, size filter, and pagination behavior.
loop: until debug build succeeds
max_iterations: 4
verify: ./gradlew.bat :app:assembleDebug --console=plain

- [x] **Step 5: Upgrade local folder aggregation to include videos**
action: Update local folder aggregation in `app/src/main/java/erl/webdavtoon/LocalPhotoRepository.kt` so folder counts, preview candidates (up to 4), and internal-photos virtual folder logic include videos using the same hierarchy semantics as images.
loop: until unit tests and build succeed
max_iterations: 4
verify:
  - type: shell
    command: ./gradlew.bat :app:testDebugUnitTest --console=plain
  - type: shell
    command: ./gradlew.bat :app:assembleDebug --console=plain

- [x] **Step 6: Extend Rust core model to carry media type metadata**
action: Update `rust-core/src/models.rs` to represent media type and required metadata for video-aware listing while keeping UniFFI-compatible structures.
loop: until Rust tests pass
max_iterations: 4
verify: cargo test --manifest-path rust-core/Cargo.toml

- [x] **Step 7: Extend WebDAV photo listing to mixed media listing**
action: Update `rust-core/src/remote_fs.rs` to include video files in listing/filtering, preserve hidden-file exclusions, and keep deterministic sorting behavior.
loop: until Rust tests pass
max_iterations: 4
verify: cargo test --manifest-path rust-core/Cargo.toml

- [x] **Step 8: Extend WebDAV folder aggregation and preview logic for videos**
action: Update `rust-core/src/remote_fs.rs` folder aggregation so folder visibility, subfolder detection, and up-to-4 preview URI selection include video entries with the same hierarchy logic as images.
loop: until Rust tests pass
max_iterations: 4
verify: cargo test --manifest-path rust-core/Cargo.toml

- [x] **Step 9: Wire Rust repository and UniFFI surface updates**
action: Update `rust-core/src/repository.rs`, regenerate/update UniFFI bindings in `app/src/main/java/uniffi/rust_core/rust_core.kt`, and adapt `app/src/main/java/erl/webdavtoon/RustWebDavPhotoRepository.kt` mapping to new mixed-media records.
loop: until Android debug build passes
max_iterations: 4
verify: ./gradlew.bat :app:assembleDebug --console=plain
gate: human

- [x] **Step 10: Add media thumbnail loading path for videos**
action: Extend `app/src/main/java/erl/webdavtoon/WebDavImageLoader.kt` (or a dedicated media thumbnail loader) to load video thumbnails for both local and WebDAV URIs, while retaining current image thumbnail quality controls.
loop: until debug build passes
max_iterations: 4
verify: ./gradlew.bat :app:assembleDebug --console=plain

- [x] **Step 11: Update waterfall item UI for video badges**
action: Update `app/src/main/res/layout/item_photo.xml` and `app/src/main/java/erl/webdavtoon/PhotoAdapter.kt` to show video indicator UI (play icon + duration label) while preserving existing multi-select behavior.
loop: until debug build passes
max_iterations: 4
verify: ./gradlew.bat :app:assembleDebug --console=plain

- [x] **Step 12: Route video taps to player page (not photo/webtoon mode)**
action: Update `app/src/main/java/erl/webdavtoon/MainActivity.kt` so tapping video opens a dedicated player activity and tapping image keeps current behavior; ensure no accidental entry into `PhotoViewActivity` for video taps.
loop: until debug build passes
max_iterations: 4
verify: ./gradlew.bat :app:assembleDebug --console=plain

- [x] **Step 13: Add Media3 dependencies and player activity registration**
action: Update `app/build.gradle.kts` with Media3 dependencies and update `app/src/main/AndroidManifest.xml` to register `VideoPlayerActivity`.
loop: until dependency resolution and debug build pass
max_iterations: 4
verify: ./gradlew.bat :app:assembleDebug --console=plain

- [x] **Step 14: Implement baseline Media3 player page**
action: Create `app/src/main/java/erl/webdavtoon/VideoPlayerActivity.kt` and `app/src/main/res/layout/activity_video_player.xml` with playback controls, progress bar/seekbar, and playback state UI.
loop: until debug build passes
max_iterations: 4
verify: ./gradlew.bat :app:assembleDebug --console=plain

- [x] **Step 15: Implement player gestures and seek UX**
action: Implement gesture interactions in `VideoPlayerActivity.kt` (horizontal seek, vertical brightness/volume, tap-to-toggle controls) with on-screen feedback overlays.
loop: until gesture implementation is stable in manual verification
max_iterations: 4
verify:
  type: human-review
  prompt: Validate seek/brightness/volume gestures on a local video and confirm control overlay + progress drag behavior is usable.
gate: human

- [x] **Step 16: Implement authenticated WebDAV video playback data source**
action: Add WebDAV-auth playback path in `VideoPlayerActivity.kt` (and helper if needed) so Media3 can play protected remote URLs using the same credential model as image loading.
loop: until remote playback works in manual verification
max_iterations: 4
verify:
  type: human-review
  prompt: Validate WebDAV video playback starts successfully on an authenticated server and supports seek/pause/resume.
gate: human

- [x] **Step 17: Add settings placeholders for a dedicated video section**
action: Update `app/src/main/res/layout/activity_settings_md3.xml`, `app/src/main/java/erl/webdavtoon/SettingsActivity.kt`, `app/src/main/java/erl/webdavtoon/AppSettingsStore.kt`, `app/src/main/java/erl/webdavtoon/SettingsManager.kt`, and `app/src/main/res/values*/strings.xml` to add a standalone video settings section with placeholder options.
loop: until debug build passes
max_iterations: 4
verify: ./gradlew.bat :app:assembleDebug --console=plain

- [x] **Step 18: Add regression tests for mixed-media classification and folder logic**
action: Add Android unit tests under `app/src/test/java/erl/webdavtoon/` and Rust tests under `rust-core/src` or `rust-core/tests` covering image+video classification, folder aggregation parity, and extension filtering.
loop: until both Android and Rust tests pass
max_iterations: 4
verify:
  - type: shell
    command: ./gradlew.bat :app:testDebugUnitTest --console=plain
  - type: shell
    command: cargo test --manifest-path rust-core/Cargo.toml

- [x] **Step 19: Run end-to-end verification matrix**
action: Run full verification and ensure both local+WebDAV video are available in folder cards, waterfall list, and player path while image behavior remains intact.
loop: until full matrix passes
max_iterations: 3
verify:
  - type: shell
    command: ./gradlew.bat :app:assembleDebug --console=plain
  - type: shell
    command: ./gradlew.bat :app:testDebugUnitTest --console=plain
  - type: shell
    command: cargo test --manifest-path rust-core/Cargo.toml
  - type: human-review
    prompt: Validate scenario A/1,2,3 subfolders with mixed media shows expected hierarchy and 4-preview behavior, and all-video folder opens player on tap.
gate: human

## Verification Notes

- 2026-04-10 local verification passed:
  - `./gradlew.bat :app:assembleDebug --console=plain`
  - `./gradlew.bat :app:testDebugUnitTest --console=plain`
  - `cargo test --manifest-path rust-core/Cargo.toml`
- Debug APK exported to project root: `webdavtoon-debug.apk`
- ADB device used: `192.168.31.216:5555`
- Local player verification:
  - Started `VideoPlayerActivity` with `content://media/external/video/media/7927`
  - `dumpsys activity` confirmed `erl.webdavtoon/.VideoPlayerActivity`
  - `logcat` showed `ExoPlayerImpl` init and `MediaCodec` decoder startup
  - `dumpsys audio` recorded `setStreamVolume(... ) from erl.webdavtoon` during gesture swipe
  - `dumpsys window windows` showed `sbrt=0.91266936` on `VideoPlayerActivity` after brightness gesture
- Remote WebDAV verification:
  - Started `VideoPlayerActivity` with cached authenticated URL `http://192.168.31.179:5005/.../n0762.mkv`
  - `logcat` showed `ExoPlayerImpl` init plus remote `MediaCodec` startup without `VideoPlayerActivity` playback error
  - Navigated `FolderViewActivity -> Videos -> JAV -> ... -> ssis-252 -> MainActivity`
  - `media-list.xml` captured `videoIndicator` + `ssis-252.mp4`
  - Tapping the remote video card opened `VideoPlayerActivity` (not `PhotoViewActivity`) per `dumpsys activity`
- ADB artifacts saved under `.artifacts/adb/2026-04-10/`
