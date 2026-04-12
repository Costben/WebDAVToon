# 2026-04-10 Video Thumbnail and WMV Support Design

## Summary

Support problematic remote video thumbnails for `mkv`/`wmv`-class files without introducing a new media stack, keep unsupported playback on the external-player path, and export a root-level debug APK copy after builds for easier user download.

## Problem

- `Videos/JAV/饭冈かなこ/n0780.mkv` is detected as a video but does not reliably render a thumbnail.
- `wmv` is not recognized as a supported video format across the app and Rust folder scanning.
- Some containers should keep using an external player even if thumbnails can be generated.
- The user wants a debug APK copied to the project root after builds.

## Constraints

- Do not regress existing image behavior.
- Reuse existing FFmpeg thumbnail support if possible; avoid adding a new heavy library unless strictly necessary.
- Preserve ADB real-device verification as mandatory after code changes.
- Keep project-root debug artifacts controlled; only the requested APK export should land there.

## Approaches

### Approach A — Reuse existing FFmpeg retriever with smarter local-cache fallback (recommended)

- Add `wmv` to Kotlin and Rust video detection/mime mappings.
- Generalize the existing AVI thumbnail fallback so `mkv`/`wmv` can also cache a local source file before FFmpeg frame extraction.
- Route `wmv` playback through the same external-player path currently used for AVI.
- Add a Gradle task to copy the assembled debug APK to the project root.

**Pros**
- Minimal dependency risk
- Uses code paths already proven for AVI
- Smallest implementation surface

**Cons**
- Some problematic containers may still depend on partial/local cache heuristics

### Approach B — Add a new video decoding/thumbnail library

- Introduce a dedicated media extractor/thumbnail dependency for more container coverage.

**Pros**
- Potentially broader codec/container support

**Cons**
- Larger APK / dependency surface
- More integration risk
- Unnecessary because FFmpeg retriever is already present

## Recommended Design

Implement Approach A.

1. Add `wmv` support to:
   - `app/src/main/java/erl/webdavtoon/MediaTypeUtils.kt`
   - `rust-core/src/remote_fs.rs`
2. Introduce a shared “external-player-required” predicate for containers that should not use the in-app player:
   - keep `avi`
   - add `wmv`
3. Upgrade `WebDavImageLoader` thumbnail extraction:
   - detect container extension from URI/name
   - preserve original extension when caching source files
   - allow local-cache + FFmpeg fallback for problematic remote/local containers (`avi`, `mkv`, `wmv`)
4. Export a debug APK copy to the repo root using a named task and ignore exported APKs in git.
5. Update project memory so future work continues exporting the built APK to the root.

## HOTL Contracts

### Intent Contract

```yaml
intent: Support MKV/WMV thumbnail generation, route WMV playback through the external-player path, and export a root debug APK copy after builds.
constraints:
  - Do not change image loading behavior
  - Do not require a new media library unless existing FFmpeg support is insufficient
  - Preserve ADB real-device verification as a required completion step
success_criteria:
  - Videos/JAV/饭冈かなこ/n0780.mkv shows a thumbnail on device
  - WMV files are recognized as videos, can produce thumbnails, and open via external player
  - A built debug APK is copied to the project root
risk_level: medium
```

### Verification Contract

```yaml
verify_steps:
  - run tests: cargo test --manifest-path rust-core/Cargo.toml
  - run compile: .\gradlew.bat :app:compileDebugKotlin
  - run build: .\gradlew.bat :app:assembleDebug
  - check: verify exported APK exists in project root
  - check: verify via ADB that n0780.mkv thumbnail renders on device
  - check: verify via ADB that WMV uses the external-player path
  - confirm: logs, screenshot/UI evidence, and successful build output
```

### Governance Contract

```yaml
approval_gates:
  - Design approval before implementation
rollback:
  - Revert media detection, thumbnail fallback, and APK export changes if verification fails
ownership:
  - Codex implements and verifies; user reviews the delivered behavior
```

## Self-Check

- Constraints are explicit and scoped to media behavior plus build export.
- Success criteria are concrete and testable.
- Verification steps cover compile/build plus ADB behavior checks.
- `risk_level: medium` matches media/runtime/build automation work without touching auth/billing/security-sensitive flows.
