# Changelog

## v2.0.0 (2026-09-24)

- 界面全面重做：设置、文件夹网格、瀑布流、阅读器与图库页面统一为一套全新设计，深色模式配色与控件风格全面对齐。
- 新增 FTP 与 SMB 协议支持，并可通过 mDNS 自动发现局域网内的服务器，SMB 支持访客登录与共享选择。
- 阅读器与瀑布流重做：条漫支持零间隙拼接，翻页、缩放与手势控制更稳定，瀑布流统一为「跟随缩放」布局并支持双指调整列数。
- 文件夹缩略图按当前排序显示最新或最旧的 4 张，切换排序时保留当前图片并淡入淡出替换，下拉刷新会重新计算。
- 新增完全随机排序，网格列数在全应用统一，设置中可调整瀑布流解码宽度与图片内存缓存大小。
- 长按选中、全选、收藏、分享、删除等选择操作加入震动反馈，删除服务器前需要二次确认。

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
