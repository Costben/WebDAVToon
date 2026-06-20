# Changelog

## v1.3.0 (2026-06-21)

- 新增瀑布流布局模式开关：默认「跟随缩放」(Follow Zoom)，可切换回「传统」(Legacy) 交错网格布局。
- 新增「侧栏滑出区域」设置：可自定义从屏幕左侧滑动呼出侧栏的触发区宽度 (0–100%，默认 33%)，设为 0 可彻底关闭滑动呼出以避免误触。
- 新增「显示瀑布流文件名」开关：可控制瀑布流图片下方是否显示文件名。
- 新增 ComfyUI 图片编辑入口。
- 新增隐私模式、复制服务器配置，并加宽抽屉边缘手势区域。
- 删除照片后保持滚动位置，避免列表跳动。
- 优化阅读器手势控制与条漫滚动流畅度，新增幻灯片播放控制。
- 整理项目根目录并完善构建/发布流程。

## v1.1.4 (2026-04-09)

- Improved WebDAV performance and stability (folder listing, JNI packaging, auth/slot caching).
- Added waterfall-mode multi-select actions for favorite/unfavorite and delete, with post-action refresh.
- Cleaned up duplicated unit-test helpers and archived temporary planning/debug documents.
- Updated CI/release workflow hardening for reliable Android + Rust builds.

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
