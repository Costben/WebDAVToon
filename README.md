# WebDAVToon 📱

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Rust](https://img.shields.io/badge/Rust-Core-orange.svg)](https://www.rust-lang.org)
[![Android Build and Release](https://github.com/Costben/WebDAVToon/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/Costben/WebDAVToon/actions/workflows/build-and-release.yml)
[![Latest Release](https://img.shields.io/github/v/release/Costben/WebDAVToon?label=release)](https://github.com/Costben/WebDAVToon/releases)

**WebDAVToon** 是一款专为漫画与图片爱好者设计的 Android 高性能开源应用。界面基于 Jetpack Compose 与 COUI 设计体系构建，底层由 Rust 异步网络引擎驱动，支持直接浏览本地相册及远程服务器（WebDAV、SMB、FTP）上的图库与漫画，无需提前下载即可享受流畅无缝的阅读体验。

---

## ✨ 核心功能

- **🚀 多源数据驱动**：原生支持本地相册（MediaStore）、WebDAV、SMB (Samba v2/v3)、FTP 四类数据源，可保存多台服务器并一键切换。
- **🔍 局域网自动发现**：配置服务器时点击「发现设备」即可扫描当前网段，免去手输 IP。
- **🖼️ 自适应瀑布流预览**：图片与视频封面混合排布，支持双指实时捏合缩放（1 ~ 4 列），网格列数在全应用内保持统一。
- **🕒 文件夹四宫格跟随排序**：文件夹封面按当前排序取图 —— 选「最新」就展示该文件夹最新的 4 张，选「最旧」则展示最旧的 4 张；切换排序时保留原有封面，后台算好后以淡出淡入过渡替换，不会闪回占位图；下拉刷新会重新计算。（SMB 目录受协议限制不跟随排序，切换时会给出提示）
- **🔀 多种排序方式**：名称 A-Z / Z-A、日期最新 / 最旧、随机文件夹、随机图片、随机图片（按文件夹分组）。
- **📖 专业双阅读模式**：
  - **卡片模式 (Card Mode)**：经典的左右滑动翻页，适合画集、插画与传统单页漫画。
  - **条漫模式 (Webtoon Mode)**：垂直连续滚动无缝拼接，针对长条漫进行了宽度优先流式加载优化，杜绝模糊与卡顿。
- **🎬 视频播放**：视频交给系统外部播放器播放，可选「默认播放器」或「每次询问」。
- **✅ 批量操作与震动反馈**：长按进入多选，支持全选、收藏、分享、删除，操作均有震动反馈；删除图片与删除服务器前均有二次确认。
- **📂 深度文件夹导航**：支持无限层级的子文件夹递归浏览与本地缓存，轻松管理海量目录。
- **🕯️ 全屏沉浸体验**：自动隐藏系统状态栏与导航栏，点击屏幕中央呼出控制栏，阅读无干扰。
- **⚡ 高性能网络与缓存**：基于 Rust + tokio 的异步 I/O 流式传输，配合深度定制的 Glide 内存 / 磁盘两级缓存，二次加载瞬时呈现；缩略图解码档位与解码后内存缓存大小均可在设置中调节，并支持一键清理缓存。
- **🌗 现代界面**：全面迁移至 Jetpack Compose 与 COUI 设计体系，浅色 / 深色主题自适应，中英文双语。

---

## 🏗️ 系统架构

本项目采用 **Kotlin + Rust (UniFFI)** 混合架构：

```text
┌─────────────────────────────────────────────────────────────┐
│                       Android (Kotlin)                      │
│  界面层 (Jetpack Compose + COUI Design System)              │
│  状态层 (ViewModel + StateFlow)                              │
│  媒体层 (Custom Glide Loader + Disk / Memory Cache)          │
│  数据层 (Room Database / Jetpack DataStore / Keystore)       │
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

- **Android / Kotlin 层**：Jetpack Compose + COUI 负责界面渲染与手势交互，ViewModel + StateFlow 驱动页面状态，Glide 处理图片解码与两级缓存，Room / DataStore / Keystore 分别负责结构化数据、偏好设置与登录凭据加密。
- **Rust Core (`rust-core`)**：负责统一远程文件系统抽象（`RemoteService`）、异步高并发请求、安全凭据隔离与媒体数据流服务。

---

## 🚀 快速配置指南

### 1. WebDAV 配置（群晖、AList、Nextcloud 等）
- **服务器地址**：完整的 HTTP/HTTPS URL，例如 `http://192.168.1.100:5244/dav`。
- **用户名 / 密码**：WebDAV 认证凭据。
- 点击 **测试连接**，系统将调用 Rust 核心实时列出根目录，验证网络与凭据连通性。
- 支持保存多台服务器并随时切换，无需重复输入地址与账号。

### 2. SMB (Windows 共享 / NAS)
- **主机**：IP 地址或主机名（例如 `192.168.1.100` 或 `NAS-SERVER`）。无需输入 `smb://` 前缀。
- **共享文件夹**：可点击 **列出共享** 从服务器枚举，也可手动填写共享名称（例如 `Comics` 或 `Media`）。
- **认证方式**：底层强制适配 NTLM 认证并兼容现代 SMB v3 协议，完美避免 Android 平台的 `NoCredentials` 异常；用户名留空即以访客身份连接。
- 注意：受协议限制，SMB 服务器的文件夹封面不跟随排序，切换排序时会给出提示。

### 3. FTP 配置
- 输入 FTP 主机、端口（默认 21）及登录凭据，即可无缝像本地文件夹一样浏览。

### 4. 局域网设备发现
- 不确定服务器地址时，在服务器配置页点击 **发现设备**，应用会扫描当前网段并列出可用的主机。

---

## 🛠️ 构建与编译

### 环境要求
- **Android Studio** Hedgehog 或更高版本
- **Android SDK**：`compileSdk 37`、`targetSdk 36`、`minSdk 24`（Android 7.0 及以上）
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
./gradlew :app:testDebugUnitTest

# 3. 构建 Debug APK
./gradlew assembleDebug

# 4. 构建 Release APK（需在 keystore/signing.properties 中配置签名信息）
./gradlew assembleRelease
```

构建完成后，APK 除保留在 `app/build/outputs/apk/` 外，还会自动导出到项目根目录，方便直接取用：
- `webdavtoon-debug.apk`
- `webdavtoon-release.apk`

推送 `v*` 标签会触发 CI 自动构建 Release，并把 APK 上传到对应的 GitHub Release。

---

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 开源。欢迎提交 Issue 与 Pull Request 共同改进！
