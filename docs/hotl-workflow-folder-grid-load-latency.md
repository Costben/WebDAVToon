# HOTL Workflow: Folder Grid Load Latency

## Design

- Evidence: `Douyin` has 332 child folders; navigation repeated a direct-media probe, and six visible preview inspections serialized behind the preview Rust repository lock.
- Decision: retain lazy visible preview loading, remove redundant resolver probing for folder grids, cache empty direct-media checks, and make preview work cancellable before it reaches the native scanner.

## Workflow

1. Add focused cache and visible-preview scheduler tests.
2. Implement the resolver, cache, preview-URI, and scheduler changes.
3. Build and run unit tests.
4. Install the debug APK and reproduce `ComfyOUT/Douyin` entry, re-entry, and refresh on the required device.

## Human checkpoints

- Review after implementation: mixed folders still redirect to the mixed view and force refresh bypasses the empty-media cache.
- Review after device verification: confirm the previous duplicate direct-media listing is absent from logcat.

## Execution record

- `:app:testDebugUnitTest :app:compileDebugKotlin` and `:app:assembleDebug` passed.
- On `<TARGET_DEVICE_IP>:5555`, `ComfyOUT/Douyin` first entry emitted one direct-media listing; re-entry hit `emptyDirectMediaCacheHit` and loaded the 332-folder grid in `102ms` without a `list_photos` call.
- A forced refresh emitted one direct-media listing as intended. During a fast scroll, pending previews for detached cards were skipped while the active scan completed.
