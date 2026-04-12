---
intent: Improve remote/local folder image preview reliability and confirm Local Photos navigation remains populated after recent media-preview changes.
constraints: Do not regress the already-fixed MKV/WMV video thumbnail path, do not break existing folder navigation semantics, and keep ADB real-device verification mandatory.
success_criteria: Folder image previews load reliably during root/subfolder browsing, Local Photos opens into populated folders/media on device, and local build/tests still pass.
risk_level: low

verification_contract:
  verify_steps:
    - run tests: cargo test --manifest-path rust-core/Cargo.toml
    - run tests: .\\gradlew.bat :app:compileDebugKotlin
    - run tests: .\\gradlew.bat :app:assembleDebug
    - check: adb verify WebDAV root folder image previews, a remote image-heavy subfolder, Local Photos root, and one Local Photos child folder/media screen
    - confirm: no empty Local Photos screen and no visibly missing image previews in the verified views

governance_contract:
  approval_gates:
    - none (user explicitly requested investigation, fix, and testing)
  rollback: revert the request-options reliability tweak if preview loading regresses
  ownership: Codex executor
---

# Design: Image Preview and Local Gallery Reliability

## Context

The user reported two regressions after recent media preview changes:
1. Many folder image thumbnails are hard to load or appear missing.
2. Entering `Local Photos` can appear empty.

Recent code inspection and ADB reproduction on `192.168.31.216:5555` showed:
- WebDAV root and image-heavy subfolders can currently load, but the behavior is intermittent from the user's report.
- `Local Photos` and nested local folders currently open with content on device.
- Folder preview image requests use a very aggressive `timeout(3_000)` path in `WebDavImageLoader.buildRequestOptions(..., isFolderPreview = true)`.

## Hypotheses

### Hypothesis A (recommended)
Folder image preview requests are failing intermittently because folder previews are intentionally low-latency but currently too aggressive, especially for many concurrent remote images. The 3-second timeout can cause otherwise-valid preview loads to fail under load.

### Hypothesis B
Local Photos emptiness is caused by navigation/state regression rather than repository/query failure. Current ADB reproduction does not confirm this, so avoid speculative navigation rewrites unless new evidence appears.

## Recommended approach

Apply the minimum-risk reliability fix:
- relax/remove the 3-second folder-preview request timeout
- restore high fetch priority for folder preview image requests
- keep all existing video-thumbnail logic unchanged
- verify on device across remote image folders and local folders/media

## Why this approach

- Targets a concrete, code-level reliability risk already present.
- Avoids destabilizing the now-working local/recent video paths.
- Gives a measurable before/after validation path with ADB screenshots.
