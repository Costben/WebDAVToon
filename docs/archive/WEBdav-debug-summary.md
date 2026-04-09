# WebDAV 真机排查总结

## 本次排查背景

在真机上安装最新版 APK 后，用户反馈：

- 可以通过 **Test Connection** 连接到 WebDAV 服务器
- 但进入实际浏览页面时，看不到任何内容
- 用户初步怀疑是 Rust 核心库崩溃

本次排查目标是确认：

1. 是否是 Rust 核心库启动崩溃
2. 是否是 WebDAV 连接失败
3. 是否是浏览列表逻辑或 Rust ↔ Kotlin/UniFFI 数据边界问题

---

## 已确认事实

### 1. Rust 核心库没有在启动阶段崩溃

真机日志确认以下步骤都成功：

- `System.loadLibrary("rust_core")` 成功
- Rust logger 初始化成功
- `RustRepository` 初始化成功
- `init_webdav` 被正常调用

因此可以排除：

> “App 一启动就因为 Rust 核心库崩溃而导致 WebDAV 完全不可用”

---

### 2. WebDAV 连接本身是成功的

真机日志显示：

- `test_webdav` 被调用成功
- 当前服务器配置为：`http://192.168.31.179:5005/NSFW`
- 根目录实际存在返回内容

新增诊断日志中出现：

> `WebDAV root has 5 entries, but none matched visible image-folder rules for endpoint=http://192.168.31.179:5005/NSFW`

这说明：

- 服务器可达
- 账号密码可用
- `test connection` 结果可信

因此可以排除：

> “服务器地址错误 / 账号密码错误 / 服务完全不可访问”

---

### 3. 真机上还存在系统级权限控制器崩溃问题

设备系统日志多次出现：

- `com.google.android.permissioncontroller` 崩溃
- `Permission controller keeps stopping`

这会影响：

- 首次权限弹窗
- App 前台拉起流程
- 部分真机自动化验证步骤

这不是 WebDAVToon 自己的业务逻辑问题，但会干扰复现场景。

---

## 当前真正命中的核心问题

### `getFolders()` 在 Rust → Kotlin 的 UniFFI 解码阶段失败

最关键的真机日志如下：

```text
E/RustWebDavPhotoRepo: Failed to get webdav folders
java.nio.BufferUnderflowException
    at uniffi.rust_core.FfiConverterString.read(rust_core.kt:1063)
    at uniffi.rust_core.FfiConverterTypeFolder.read(rust_core.kt:1519)
    at uniffi.rust_core.FfiConverterSequenceTypeFolder.read(rust_core.kt:1877)
    at uniffi.rust_core.RustRepository.getFolders(rust_core.kt:1388)
    at erl.webdavtoon.RustWebDavPhotoRepository.getFolders(RustWebDavPhotoRepository.kt:90)
```

这说明问题不是简单的“筛选后为空”，而是更前一层：

> Rust 返回的 `Folder` 列表，在 Kotlin/UniFFI 侧解码时发生了 `BufferUnderflowException`

换句话说，当前浏览失败的第一现场是：

1. `getFolders()` 调用 Rust 成功进入
2. Rust 返回 `Folder` 序列
3. Kotlin UniFFI 绑定在读取 `Folder` buffer 时发生越界/长度不足
4. `RustWebDavPhotoRepository.getFolders()` catch 后返回空列表
5. 然后才进入新增的“空列表诊断”逻辑

因此目前界面看到的“没有可显示图片文件夹”提示，**在这个具体服务器现场里只是次级结果**，不一定是根因。

---

## 本次已经做过的修复

为了避免之前所有空场景都静默显示为空白页，本次已做了一版最小修复：

- 保留原有“只显示包含受支持图片的非隐藏文件夹”的产品语义
- 不放宽图片筛选逻辑
- 在 Kotlin 层补了一条远程空列表诊断路径

当前行为变为：

- 若 WebDAV 根目录本身为空：提示“根目录下没有任何文件夹或文件”
- 若 WebDAV 根目录有内容但不符合显示规则：提示“没有可显示的图片文件夹”

该修复已经通过：

```text
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
BUILD SUCCESSFUL
```

但是，这个修复并没有消除当前设备上 `getFolders()` 的 UniFFI 解码异常。

---

## 当前最可能的根因判断

优先级最高的怀疑点：

### Rust `Folder` Record 与 Kotlin UniFFI 绑定之间存在结构不一致

需要重点排查：

1. `rust-core/src/models.rs` 中 `Folder` 的字段顺序、字段类型
2. `app/src/main/java/uniffi/rust_core/rust_core.kt` 中生成的 `Folder` 读取顺序
3. Rust 本地库是否已经重新编译并与 APK 中实际打包的 native lib 保持一致
4. 是否存在字段新增/删除后，UniFFI 绑定未同步更新导致的 buffer 布局不匹配
5. 是否某个返回的 `Folder` 记录本身包含异常/损坏数据，导致长度读取错位

---

## 当前结论

本轮排查后的最准确结论是：

> 这不是“服务器连不上”，也不是“Rust 核心库启动即崩”。
>
> 真正卡住浏览功能的是：
>
> **WebDAV 文件夹列表在 Rust → Kotlin 的 UniFFI 解码阶段发生了 `BufferUnderflowException`。**

---

## 建议的下一步

下一步应优先沿以下方向继续：

1. 核对 Rust `Folder` record 与 Kotlin UniFFI 生成绑定是否完全一致
2. 确认当前 APK 中打包的 native `rust_core` 与 Kotlin 生成绑定对应的是同一版接口
3. 对 `getFolders()` 返回的单个 `Folder` 数据做更细粒度定位，找出是哪条记录触发了解码失败
4. 修复 UniFFI / record 布局不一致后，再回到真机验证“空列表提示”是否仍然需要触发

---

## 补充说明

本次排查同时说明了一点：

- **Test Connection 成功 ≠ 浏览路径一定可用**

因为两条链路不同：

- `testWebdav()`：只验证连接与根目录列出名字
- `getFolders()`：需要走完整的 Rust → UniFFI → Kotlin 文件夹结果链路

而当前真正失败的正是后者。
