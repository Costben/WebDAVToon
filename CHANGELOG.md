# Changelog

## v1.1.3 (2026-04-01)

- Completed the P0 stabilization pass with DataStore, Room, and ViewModel migration.
- Added legacy-to-new configuration migration on startup so existing installs keep their settings after upgrade.
- Fixed the local media pagination regression and tightened the media state flow.

## v1.1.2 (2026-03-31)

- 固定详情页分页同步，支持在“条漫模式”下无限翻页。
- 采用集中管理的 MediaManager，让图片管理逻辑在首页与详情页间完全共享。

## v1.1 (2026-03-31)

Commit range summary: `v1.2.7..HEAD`

- Added Android app baseline and integrated Rust core build flow.
- Added navigation drawer, multi-server management, and config dialogs.
- Restored required UniFFI-generated Kotlin bindings for stable builds.
- Fixed Rust plugin configuration and aligned NDK/Python settings for CI.
- Added and refined GitHub Actions build-and-release workflow.
- Upgraded GitHub Actions versions to Node 24-capable releases.
