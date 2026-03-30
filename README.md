# WebDAVToon 📱

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)

**WebDAVToon** 是一款专为漫画与图片爱好者设计的 Android 应用。它不仅支持浏览本地存储的图片，更原生集成了 WebDAV 协议，让您可以直接访问远程服务器（如 AList、群晖 NAS 等）上的漫画库，无需下载即可享受流畅的阅读体验。

---

## ✨ 核心功能

- **🚀 双源驱动**：完美支持本地存储与 WebDAV 远程连接。
- **🖼️ 瀑布流预览**：在 `MainActivity` 中以精美的瀑布流形式展示图片，支持双指缩放动态调整网格列数。
- **📖 专业阅读模式**：
  - **卡片模式 (Card Mode)**：经典的左右滑动翻页，适合单张图片或传统画集。
  - **Webtoon 模式**：垂直连续滚动，针对长条漫进行了宽度优先的加载优化，拒绝模糊。
- **📂 深度文件夹导航**：支持无限层级的子文件夹递归浏览，轻松管理海量内容。
- **🕯️ 沉浸式体验**：全屏沉浸式阅读，自动隐藏系统状态栏与导航栏，让内容成为唯一焦点。
- **⚡ 性能优化**：针对 WebDAV 环境深度定制的 Glide 加载策略，支持磁盘缓存与流式加载，在大图浏览时依然保持流畅。

---

## 🏗️ 项目架构

项目采用模块化设计，职责分明：

- **UI 层**：基于 Activity + RecyclerView 构建，包含 `FolderViewActivity` (入口)、`MainActivity` (瀑布流) 及 `PhotoViewActivity` (阅读器)。
- **数据层**：通过 `PhotoRepository` 接口抽象，统一了 `LocalPhotoRepository` 与 `WebDavPhotoRepository` 的操作。
- **网络层**：自研 `WebDavClient` 处理 XML 解析与网络请求，配合 `WebDavImageLoader` 实现高效的图片流加载。
- **工具层**：包含 `SettingsManager` (偏好设置)、`LogManager` (调试日志) 等实用工具。

更多细节请参考：[PROJECT_STRUCTURE.md](docs/architecture/PROJECT_STRUCTURE.md)

---

## 🛠️ 技术栈

- **Language**: Kotlin
- **Image Loading**: [Glide](https://github.com/bumptech/glide) (深度定制 WebDAV 支持)
- **UI Components**: Material Design 3, PhotoView
- **Database**: Room (用于文件夹缓存)
- **Network**: OkHttp

---

## 📥 快速开始

1. **安装**：下载最新的 APK 并安装。
2. **配置 WebDAV**：进入“设置”，输入您的 WebDAV 服务器地址、用户名及密码。
3. **浏览**：在主页选择 WebDAV 入口，即可开始探索您的远程图库。

---

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 开源。

---

## 🤝 贡献与反馈

欢迎提交 Issue 或 Pull Request 来帮助改进 WebDAVToon！如果您觉得这个项目对您有帮助，请给一个 ⭐️ 以示支持。

---

# WebDAVToon (English)

A powerful Android Image/Manga Viewer supporting both Local and WebDAV storage.

## Features
- **WebDAV Support**: Stream your manga directly from remote servers (AList, Synology, etc.).
- **Dual Reading Modes**: Card mode for flipping, and Webtoon mode for continuous vertical scrolling.
- **Staggered Grid View**: Beautiful thumbnail preview with dynamic column adjustment.
- **Immersive Mode**: Full-screen reading with auto-hiding system bars.
- **Optimized Performance**: Custom Glide integration for smooth WebDAV image streaming.

## Getting Started
1. Install the APK.
2. Go to Settings and configure your WebDAV credentials.
3. Browse and enjoy!

## License
[MIT License](LICENSE)
