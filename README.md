# WebDAVToon 📱

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Rust](https://img.shields.io/badge/Rust-Core-orange.svg)](https://www.rust-lang.org)

**WebDAVToon** 是一款专为漫画与图片爱好者设计的 Android 高性能开源应用。通过结合 Kotlin 现代 Android UI 与 Rust 异步底层网络引擎，支持直接浏览本地存储及远程服务器（WebDAV、SMB、FTP）上的图库与漫画，无需提前下载即可享受流畅无缝的阅读体验。

---

## ✨ 核心功能

- **🚀 多源数据驱动**：原生支持本地存储、WebDAV、SMB (Samba v2/v3)、FTP 远程连接与多服务器切换。
- **🖼️ 自适应瀑布流预览**：以精美瀑布流形式展示图片与视频封面，支持双指实时捏合缩放（2 列 ~ 5 列）。
- **📖 专业双阅读模式**：
  - **卡片模式 (Card Mode)**：经典的左右滑动翻页，适合画集、插画与传统单页漫画。
  - **条漫模式 (Webtoon Mode)**：垂直连续滚动无缝拼接，针对长条漫进行了宽度优先流式加载优化，杜绝模糊与卡顿。
- **📂 深度文件夹导航**：支持无限层级的子文件夹递归浏览与本地缓存，轻松管理海量目录。
- **🕯️ 全屏沉浸体验**：自动隐藏系统状态栏与导航栏，点击屏幕中央呼出控制栏，阅读无干扰。
- **⚡ 高性能网络与缓存**：基于 Rust + tokio 的异步 I/O 流式传输，配合深度定制的 Glide 磁盘缓存与回放，二次加载瞬时呈现。

---

## 🏗️ 系统架构

本项目采用 **Kotlin + Rust (UniFFI)** 混合架构：

```text
┌─────────────────────────────────────────────────────────────┐
│                       Android (Kotlin)                      │
│  UI 层 (Activities / RecyclerView / Custom LayoutManagers)   │
│  图片引擎 (Custom Glide Loader + Disk / Memory Cache)        │
│  本地存储 (Room Database / Jetpack DataStore / Keystore)    │
└──────────────────────────────┬──────────────────────────────┘
                               │ UniFFI FFI 接口
┌──────────────────────────────┴──────────────────────────────┐
│                      Rust Core (rust-core)                  │
│  tokio 异步多任务运行时                                       │
│  网络与文件系统抽象 (opendal: WebDAV/FTP, smb: SMBv2/v3)    │
│  本地媒体流代理 (Media Proxy with Range Requests & Auth)     │
│  SQLite 缓存与元数据索引                                     │
└─────────────────────────────────────────────────────────────┘
```

- **Android / Kotlin 层**：负责 Material Design 3 界面交互、手势检测、Activity 生命周期管理与系统集成。
- **Rust Core (`rust-core`)**：负责统一远程文件系统抽象（`RemoteService`）、异步高并发请求、安全凭据隔离与媒体数据流服务。

---

## 🚀 快速配置指南

### 1. WebDAV 配置（群晖、AList、Nextcloud 等）
- **服务器地址**：完整的 HTTP/HTTPS URL，例如 `http://192.168.1.100:5244/dav`。
- **用户名 / 密码**：WebDAV 认证凭据。
- 点击 **测试连接**，系统将调用 Rust 核心实时列出根目录，验证网络与凭据连通性。

### 2. SMB (Windows 共享 / NAS)
- **主机**：IP 地址或主机名（例如 `192.168.1.100` 或 `NAS-SERVER`）。无需输入 `smb://` 前缀。
- **共享文件夹**：共享名称（例如 `Comics` 或 `Media`）。
- **认证方式**：底层强制适配 NTLM 认证并兼容现代 SMB v3 协议，完美避免 Android 平台的 `NoCredentials` 异常。

### 3. FTP 配置
- 输入 FTP 主机、端口（默认 21）及登录凭据，即可无缝像本地文件夹一样浏览。

---

## 🛠️ 构建与编译

### 环境要求
- **Android Studio** Hedgehog 或更高版本
- **Android SDK**（API 34+，构建目标 API 36）
- **Android NDK**：`28.2.13676358`
- **JDK**：17
- **Rust 工具链**：`stable`，包含以下 Android 交叉编译 target：
  ```bash
  rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
  ```

### 编译步骤
```bash
# 1. 运行 Rust 核心单元测试
cargo test --manifest-path rust-core/Cargo.toml

# 2. 编译并运行 Android 单元测试
./gradlew testDebugUnitTest

# 3. 构建 Debug APK
./gradlew assembleDebug

# 4. 构建 Release APK
./gradlew assembleRelease
```

---

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 开源。欢迎提交 Issue 与 Pull Request 共同改进！
