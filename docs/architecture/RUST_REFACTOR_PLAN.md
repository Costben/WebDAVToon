# Rust 重构计划 (RUST_REFACTOR_PLAN)

## 1. 项目目标与重构动机

本项目目前采用 Kotlin 开发，核心逻辑（特别是 WebDAV 协议处理、并发图片加载、元数据管理）虽然功能完整，但在以下方面存在改进空间：
1.  **性能与响应速度**: 手动 XML 解析和 OkHttp 调度在高并发场景下存在 CPU 和内存开销。Rust 的零成本抽象和无 GC 特性可以显著降低延迟。
2.  **跨平台复用**: 核心业务逻辑（WebDAV 交互、缓存策略、数据模型）与 UI 强耦合。重构为 Rust 核心库后，可直接复用于 iOS、Desktop (Tauri) 等平台。
3.  **安全性与稳定性**: Rust 的内存安全特性可以消除潜在的空指针异常和并发竞态条件。

## 2. 技术栈选型与性能分析

为了确保重构后的项目拥有**最佳性能**和**最小开销**，我们对关键技术栈进行了严格筛选。以下选型均基于 2024-2025 年 Rust 生态的最新标准。

### 2.1 核心交互层: UniFFI (Mozilla)
*   **选择**: `uniffi-rs`
*   **理由**:
    *   **开发效率**: 自动生成 Kotlin/Swift/Python 绑定，无需手写易错的 JNI 代码。
    *   **性能**: 虽然相比手动 JNI 有微小开销，但对于 IO 密集型任务（网络请求），其开销可忽略不计。生成的绑定代码经过 Mozilla 生产环境验证（Firefox Mobile）。
    *   **替代方案对比**: `jni-rs` 性能极高但开发维护成本过高；`diplomat` 尚不成熟。
*   **版本策略**: 始终跟随最新稳定版。

### 2.2 数据访问层: OpenDAL (Apache)
*   **选择**: `opendal`
*   **理由**:
    *   **统一接口**: 提供统一的 API 访问 WebDAV、本地文件系统 (fs)、S3 等几十种存储后端。这意味着未来扩展支持（如 Google Drive, SMB）几乎零成本。
    *   **极致性能**: 核心团队专注于高性能 IO，内部使用了高度优化的异步流处理。
    *   **生态地位**: Apache 顶级项目（或孵化中），活跃度极高，是 Rust 数据访问的事实标准。
    *   **功能完备**: 内置重试机制、并发上传/下载、元数据缓存等高级功能。

### 2.3 异步运行时: Tokio
*   **选择**: `tokio` (配置为单线程或小线程池)
*   **理由**:
    *   **事实标准**: Rust 异步生态的基石。
    *   **移动端优化**: 在 Android 上，我们不需要默认的多线程调度器（Work-stealing scheduler）。为了节省电量和减少上下文切换，我们将配置 Tokio 使用 `rt` (单线程运行时) 或限制 Worker 数量的 `rt-multi-thread`。
    *   **性能**: 极其成熟，经过数年打磨，性能损耗极低。

### 2.4 网络客户端: Reqwest (基于 Hyper)
*   **选择**: `reqwest` (配合 `rustls`)
*   **理由**:
    *   **性能与安全**: 使用纯 Rust 实现的 `rustls` 替代系统 OpenSSL，减少外部依赖，提高安全性，且通常比 OpenSSL 更快。
    *   **功能**: 支持 HTTP/2，连接池复用，gzip/brotli 自动解压。
    *   **OpenDAL 集成**: OpenDAL 默认使用 Reqwest，无需额外引入。

### 2.5 XML 解析: Quick-XML
*   **选择**: `quick-xml`
*   **理由**:
    *   **极致性能**: 它是 Rust 生态中最快的 XML 解析器之一。
    *   **零拷贝 (Zero-copy)**: 尽可能避免内存分配，直接在输入 buffer 上操作，这对解析大型 WebDAV PROPFIND 响应至关重要。
    *   **Serde 集成**: 支持通过 `serde` 直接反序列化 XML 到 Struct，兼顾开发效率与性能。

### 2.6 数据库: Rusqlite (SQLite)
*   **选择**: `rusqlite` (bundled)
*   **理由**:
    *   **性能**: 直接绑定 C 语言的 SQLite，无 ORM 运行时开销。
    *   **轻量**: 相比 `sqlx` (编译慢、二进制大)，`rusqlite` 更适合移动端嵌入式场景。
    *   **功能**: 支持预编译语句 (Prepared Statements)，查询速度极快。

## 3. 架构设计 (Rust-Core Engine)

采用 **"UI (Kotlin) + Core (Rust)"** 的混合架构。

```text
[ Android UI Layer (Kotlin) ]
       │      ▲
       │      │ (UniFFI Generated Bindings)
       ▼      │
[ Rust Interface Layer (api.rs) ]
       │
       ├─> [ Runtime Manager ] (Tokio Runtime)
       │
       ├─> [ Repository Layer ] ──────────────┐
       │     │ (Unified Data Access)          │
       │     ▼                                ▼
       │   [ OpenDAL Operator ]         [ Metadata Cache ]
       │     (WebDAV / FS)               (Rusqlite)
       │
       └─> [ Image Processor ] (Optional)
             (image-rs for thumbnails)
```

## 4. 实施路线图 (Roadmap)

### 阶段一：基础架构搭建 (Infrastructure)
1.  初始化 Rust crate `webdavtoon-core`。
2.  配置 `cargo-ndk` 和 Android Gradle 插件，实现 Rust 代码自动编译并打包进 APK。
3.  配置 `uniffi`，定义基础的 `Logger` 和 `Config` 接口，打通 Kotlin 调用 Rust 的链路。

### 阶段二：核心业务迁移 (Core Logic)
1.  **数据模型定义**: 在 Rust 中定义 `Photo`, `Folder` 等结构体。
2.  **WebDAV 客户端重写**: 使用 `opendal` 实现 `list_files` (PROPFIND) 功能。
    *   *性能优化点*: 使用 `Stream` 处理 WebDAV 响应，避免一次性加载大 XML 到内存。
3.  **本地文件扫描**: 使用 `opendal` 的 `fs` backend 实现本地文件扫描。
4.  **统一 Repository**: 封装 `WebDavRepo` 和 `LocalRepo` 为统一接口。

### 阶段三：高级功能与优化 (Advanced & Perf)
1.  **并发加载优化**: 利用 `tokio::spawn` 实现目录内容的并发预加载。
2.  **元数据缓存**: 引入 `rusqlite`，将文件夹结构和文件元数据缓存到 SQLite，减少网络请求。
3.  **图片流处理**: 实现一个 Image Source 接口，支持 HTTP Range 请求（OpenDAL 原生支持），解决 WebDAV 不支持 Range 的问题（如果服务器支持）。

### 阶段四：集成与清理 (Integration)
1.  在 Kotlin 中替换 `WebDavClient`, `PhotoRepository` 等类。
2.  移除旧的 Java 代码。
3.  进行内存泄漏检测和性能基准测试。

## 5. 风险评估与应对

1.  **二进制体积增加**:
    *   *应对*: 使用 `strip`, `lto = true`, `opt-level = "z"`, `panic = "abort"` 等编译选项优化。
2.  **调试难度**:
    *   *应对*: 集成 `android_logger` crate，将 Rust 日志直接输出到 Logcat。
3.  **构建复杂度**:
    *   *应对*: 编写详细的构建脚本，使用 Docker 容器标准化构建环境。
