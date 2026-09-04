# Folder Grid Load Latency

## Objective

Reduce the delay when opening remote folders with many child folders (for example `ComfyOUT/Douyin`) without changing folder, mixed-content, or media navigation outcomes.

## Scope

- Skip the navigation resolver's direct-media query when it has already found real child folders; let the target folder screen perform that single query and retain the existing mixed-content redirect.
- Cache a successful empty non-recursive remote-media result, keyed by remote account and folder, so revisits do not re-list a folder known to have no direct media. A pull-to-refresh bypasses and replaces the cache.
- Schedule remote folder-preview inspection through one visible-item queue; discard pending work as cards leave the viewport and preserve the existing preview cache behavior.
- Preserve non-empty preview URIs returned by the Rust folder query.

## Constraints

- Do not eagerly inspect all folders in a large remote directory.
- Preserve mixed-folder routing, refresh semantics, and stale-cache recovery through force refresh.
- Keep unrelated pending reader-ordering changes intact.

## Affected files

- `FolderNavigationResolver.kt`, `FolderAdapter.kt`, `FolderViewActivity.kt`, `SubFolderActivity.kt`
- `RustWebDavPhotoRepository.kt`, `RemoteFolderPreviewMemoryCache.kt`
- New preview scheduler and focused JVM tests

## Acceptance checks

- A remote folder with child folders does not call `getPhotos(... recursive=false)` in `FolderNavigationResolver` before opening `SubFolderActivity`.
- An empty direct-media response is reused until a forced refresh, while non-empty media behavior is unchanged.
- Only attached folder cards enqueue preview scans; detached queued cards are not inspected.
- Existing returned remote preview URIs render without a second inspection.

## Verification

- `./gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin`
- Install the debug APK on `<TARGET_DEVICE_IP>:5555`, open `ComfyOUT/Sample` twice, refresh once, and capture logcat plus UI/activity evidence under `.artifacts/adb/2026-07-27/`.

## Risks / open questions

- Empty-media cache is intentionally invalidated by force refresh; remote changes made outside the app can remain unseen until then.
- Mixed folders take the existing `SubFolderActivity` redirect path after the resolver short-circuit.
