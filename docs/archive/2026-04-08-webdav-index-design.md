# WebDAV 本地索引与目录聚合设计（2026-04-08）

## 背景

当前 WebDAV 目录进入慢，根因不是 UI 本身，而是目录列表阶段会对每个候选子目录递归探测其整棵子树是否存在图片：

- `D:/Erl-Project/WebDAVToon/rust-core/src/remote_fs.rs`
  - `list_folders_webdav(path)`
  - `check_has_images_webdav(folder_path)` 内部使用 `op.lister_with(folder_path).recursive(true)`
- `D:/Erl-Project/WebDAVToon/app/src/main/java/erl/webdavtoon/FolderViewActivity.kt`
- `D:/Erl-Project/WebDAVToon/app/src/main/java/erl/webdavtoon/SubFolderActivity.kt`

这意味着：

1. 打开当前层目录前，需要先把每个直接子目录往下递归探测。
2. 子目录越多、层级越深，首次进入越慢。
3. 当前 `rust_core.db` 只是“远端扫完后写回缓存”，不是“先读索引后后台刷新”。

用户已经明确接受以下产品取舍：

- 目标优先级：**进入目录要快**。
- 允许缓存短时间不完全新鲜（约 **1~5 分钟**）。
- 首次可先显示当前层所有非隐藏直接子目录。
- 后台索引完成后，再**整页批量刷新**过滤纯空目录。
- 不做全库预热。
- 不做“一张新图触发全库重建”。
- 复用现有 `rust_core.db`，不再引入第二个数据库文件。

---

## 方案结论

采用：**方案 B：文件时间索引 + 目录聚合**。

配套 UI 刷新语义采用：**方案 A：稳定型刷新**。

即：

1. 先返回缓存（或首次的一层直列结果），页面秒开。
2. 后台刷新当前目录索引。
3. 刷新完成后，如果当前页仍停留在该目录，则**一次性替换当前页列表**。
4. 不做一条条增量冒出。

---

## Intent Contract

```yaml
intent: 为 WebDAV 目录浏览增加基于本地数据库的目录/文件索引，使进入目录时优先读取缓存或一层直列结果，避免每次递归整棵子树。
constraints:
  - 不新增第二个数据库文件，继续使用 rust_core.db。
  - 不改变现有图片浏览、删除、WebDAV 连接配置的外部行为。
  - 首次进入未知目录时允许短时间显示“尚未过滤的直接子目录”，但不能卡住页面等待递归扫完整棵树。
  - 新增一张图片时只允许触发当前目录及其祖先聚合更新，不能导致全库重建。
success_criteria:
  - WebDAV 进入目录时不再阻塞于对每个子目录执行 recursive(true) 探测。
  - 已访问目录可直接从本地索引返回结果，再后台刷新。
  - 后台刷新完成后，当前页只发生一次整页更新。
  - 对一个目录新增/删除图片时，只更新该目录与祖先目录聚合，不触发全库扫描。
  - 旧版本数据库可自动迁移到新 schema，且不会破坏已有 photos/folders 缓存数据。
risk_level: medium
```

## Verification Contract

```yaml
verify_steps:
  - run tests: cargo test --manifest-path D:/Erl-Project/WebDAVToon/rust-core/Cargo.toml
  - run tests: .\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
  - check: 首次进入一个较大的 WebDAV 目录时，页面应先出现缓存或直接子目录结果，而不是等待递归探测完成
  - check: 后台刷新后，当前页目录列表应一次性更新，不是一条条跳动
  - check: 在已索引目录新增 1 张图片后，只刷新该目录及祖先目录聚合；重新进入同目录不应触发全库重建
  - confirm: logcat 中不应再看到“进入目录前对每个子目录做 recursive(true) 才返回 UI”这一慢路径成为主链路
```

## Governance Contract

```yaml
approval_gates:
  - 数据库 schema 定稿后再开始实现 migration
  - Rust Repository 接口变化定稿后再调整 Kotlin 调用链
  - 真机验证通过前不删除旧缓存逻辑兜底
rollback:
  - 保留现有 photos/folders 表和旧读取路径作为兜底
  - 新索引表采用增量 schema，出现问题时可退回旧 get_folders 逻辑
  - 所有新增逻辑以 feature-like 分支收敛在 get_folders/index refresh 调用链，不直接影响删除/读文件功能
ownership:
  - 当前线程负责设计、实现、真机验证与回归
```

---

## 备选方案与取舍

### 方案 A（放弃）：继续递归探测，但做并发/限流优化

优点：改动小。

缺点：本质上仍是“进入当前层前，先扫每个子目录整棵子树”，目录大时只会“稍快一点”，不会变成秒开。

### 方案 B（采用）：文件时间索引 + 目录聚合

优点：

- 读路径从“远端递归扫描”改成“本地索引读取”。
- 能做按目录增量更新。
- 能支持缓存 TTL、后台刷新、整页稳定更新。

缺点：

- 需要新增 schema、聚合逻辑、刷新策略。
- 首次进入某目录时，可能先看到未过滤的直接子目录，再在后台索引完成后批量收敛。

### 方案 C（放弃）：全库预热索引

优点：命中后体验好。

缺点：

- 首次成本极高。
- 不符合用户“不做全库预热 / 不做全库重建”的要求。

---

## 当前问题的精确定义

### 现状慢路径

`list_folders_webdav(path)` 当前流程：

1. 远端列出当前目录的直接子项。
2. 对每个候选子目录调用 `check_has_images_webdav(...)`。
3. `check_has_images_webdav(...)` 使用 `recursive(true)` 深扫该目录整棵子树。
4. 只有确认“这个子树里存在图片”后，才把该子目录作为可显示目录返回。

因此，页面要想“进入下一层”，往往要等多次递归探测完成。

### 现状重复放大点

- `FolderViewActivity` 点击目录时，会再次 `getFolders(realPath, false)` 做预检查。
- `SubFolderActivity` 点击目录时，也会再次 `getFolders(realPath, false)` 做预检查。

即使用户只是想进入下一层，当前链路也会继续把“列目录”当成“判断整个子树是否有图”的时机，导致体感很慢。

---

## 目标行为

### 用户可感知行为

1. 打开目录时优先秒开：
   - 已有缓存：直接显示缓存。
   - 首次无缓存：先显示当前层所有非隐藏直接子目录。
2. 后台刷新完成后：
   - 当前页一次性更新。
   - 纯空目录会被整批过滤掉。
3. 新增 1 张图片：
   - 只更新其所在目录和祖先聚合。
   - 不触发全库重建。

### 非目标

- 不追求目录列表的强一致秒级同步。
- 不做后台全库巡检。
- 不做新数据库进程或额外 sidecar 服务。

---

## 数据库设计

继续使用 `rust_core.db`，在保留现有 `photos` / `folders` 表的前提下，新增专用索引表。

### 1. `file_index`

记录“某个目录下直接文件”的已知状态，用于增量判断和直接图片聚合。

建议字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `path` | TEXT PRIMARY KEY | 文件完整远端路径 |
| `folder_path` | TEXT NOT NULL | 所属直接父目录 |
| `name` | TEXT NOT NULL | 文件名 |
| `size` | INTEGER NOT NULL | 文件大小 |
| `modified_at` | INTEGER NOT NULL | 远端修改时间（秒） |
| `is_image` | INTEGER NOT NULL | 是否为支持的图片格式 |
| `last_seen_at` | INTEGER NOT NULL | 本次扫描最后一次看见该文件的时间 |

索引：

- `idx_file_index_folder(folder_path)`
- `idx_file_index_folder_image(folder_path, is_image)`

### 2. `folder_index`

记录目录的直接结构和聚合结果，替代现在“递归探测后一次性写 `folders` 表”的做法。

建议字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `path` | TEXT PRIMARY KEY | 目录完整路径 |
| `parent_path` | TEXT NOT NULL | 父目录 |
| `name` | TEXT NOT NULL | 目录名 |
| `is_local` | INTEGER NOT NULL DEFAULT 0 | 远端目录统一为 0，兼容未来扩展 |
| `direct_subfolder_count` | INTEGER NOT NULL DEFAULT 0 | 直接子目录数 |
| `direct_image_count` | INTEGER NOT NULL DEFAULT 0 | 当前目录直接图片数 |
| `descendant_image_count` | INTEGER NOT NULL DEFAULT 0 | 后代目录中的图片数（不含 direct） |
| `newest_direct_image_mtime` | INTEGER NOT NULL DEFAULT 0 | 当前目录直接图片最新时间 |
| `newest_descendant_image_mtime` | INTEGER NOT NULL DEFAULT 0 | 后代图片最新时间 |
| `preview_uris_json` | TEXT NOT NULL DEFAULT '[]' | 预览图 URI 列表 |
| `list_last_scanned_at` | INTEGER NOT NULL DEFAULT 0 | 当前目录一层直列的扫描时间 |
| `aggregate_last_scanned_at` | INTEGER NOT NULL DEFAULT 0 | 当前目录聚合结果的扫描时间 |
| `scan_state` | TEXT NOT NULL DEFAULT 'stub' | `stub / listed / indexed / partial / error` |
| `last_error` | TEXT | 最近一次扫描错误 |

索引：

- `idx_folder_index_parent(parent_path)`
- 视需要追加 `idx_folder_index_parent_scan(parent_path, scan_state)`

### 3. 现有表的处理

- `photos`：继续作为图片页缓存使用，短期不删。
- `folders`：短期保留，避免一次性大迁移；目录页逐步改为从 `folder_index` 派生返回。

这样可以把风险集中在新索引链路，不阻断现有图片读取缓存。

---

## 目录可见性规则

对某个目录 `X`，是否在父目录列表中显示，规则改为：

```text
visible(X) =
  X 是非隐藏直接子目录
  AND (
    X.direct_image_count > 0
    OR X.descendant_image_count > 0
    OR X.aggregate 仍未知（scan_state != indexed）
  )
```

解释：

- **未知先显示**：首次无聚合结果时，先让用户进入，不要卡住等待递归确认。
- **已知再收敛**：后台索引完成后，若确认整个子树为空，再在当前页批量移除。

这条规则对应用户已批准的“先显示全部非隐藏直接子目录，再后台过滤纯空目录”。

---

## 刷新与 TTL 策略

### TTL 目标

- 允许目录索引有短时间陈旧窗口。
- 避免每次进入目录都重新递归扫全树。

### 建议值

- `LIST_TTL_SECONDS = 60`
  - 控制“当前目录直接子项列表”多久认为过期。
- `AGGREGATE_TTL_SECONDS = 300`
  - 控制“目录聚合结果”多久认为过期。

### 解释

- 用户看到的新子目录 / 文件变化，通常会在 1 分钟内通过当前目录直列更新被发现。
- “这个子目录整棵子树是否为空”的聚合判断，允许最多 5 分钟陈旧。
- 用户主动下拉刷新时：**忽略 TTL，立即刷新当前目录。**

---

## 读路径设计

### `get_folders(path, force_refresh = false)`

目标：**快返回**。

流程：

1. 先查 `folder_index` 中 `parent_path = path` 的直接子目录记录。
2. 若存在可用缓存：
   - 直接返回排序后的目录列表。
3. 若无缓存：
   - 执行一次远端 **仅当前层** 的直列（非递归）。
   - 把所有非隐藏直接子目录写入 `folder_index`，`scan_state = listed`。
   - 这些目录先按“未知先显示”规则返回。
4. 不在这条快路径里对每个子目录做 `recursive(true)` 深扫。

### `get_folders(path, force_refresh = true)`

目标：**后台刷新当前目录索引并返回收敛后的结果**。

流程：

1. 重新直列当前目录（仅一层）。
2. 更新当前目录下的直接子目录记录。
3. 更新当前目录直接文件记录（写入 `file_index`）。
4. 计算当前目录 `direct_image_count` / `newest_direct_image_mtime` / 预览图。
5. 对当前目录的直接子目录：
   - 仅当该子目录没有聚合缓存、TTL 过期、或用户显式强刷时，才刷新其聚合。
6. 当前目录聚合完成后，重新生成当前页应显示的目录列表。
7. 如果当前页面仍停留在该目录，则由 Kotlin/UI 一次性替换列表。

---

## 聚合更新设计

### 基本思想

“新增 1 张图片”不应该导致全库重建，而是局部更新。

### 当前目录刷新后，需要更新的范围

如果目录 `A/B/C/` 被刷新：

1. 更新 `C/` 的 `file_index` 与 `folder_index` 直接统计。
2. 根据 `C/` 的变化，回推更新：
   - `A/B/C/`
   - `A/B/`
   - `A/`
   - `/`
3. 祖先目录只重新计算：
   - 子目录图片计数聚合
   - 最新图片时间聚合
   - 预览图聚合

即：**只更新当前目录及其祖先，不扫描旁支目录。**

### 聚合字段计算

对任意目录 `D`：

```text
D.total_image_count = D.direct_image_count + sum(child.direct_image_count + child.descendant_image_count)
D.descendant_image_count = sum(child.direct_image_count + child.descendant_image_count)
D.has_subfolders = D.direct_subfolder_count > 0
D.newest_image_mtime = max(D.newest_direct_image_mtime, children newest)
D.preview_uris_json = 当前目录直接图片预览 + 子目录预览，截断到固定数量（如 4）
```

### 删除与失效

刷新当前目录时：

- 当前轮未再出现的直接文件：从 `file_index` 删除。
- 当前轮未再出现的直接子目录：
  - 从 `folder_index` 的父子关系中移除，或标记为失效后清理。
- 再触发祖先聚合重算。

---

## 首次进入未知目录的行为

这是本设计最关键的用户体验调整。

### 第一次进入某目录时

1. 若该目录此前没被索引过：
   - 只做一层直列。
   - 先显示所有非隐藏直接子目录。
2. 这些目录初始状态可以没有完整聚合信息：
   - `scan_state = listed`
   - `direct_image_count = 0`
   - `descendant_image_count = 0`
3. 但 UI 仍允许进入，因为未知目录默认可见。
4. 后台聚合完成后：
   - 纯空目录才会被整页批量滤掉。

### 这样解决了什么

- 不再“必须证明有图，才让用户进入下一层”。
- 用户可以先进入目录，再逐步让索引把列表收敛得更准。

---

## UI 刷新语义

采用用户已批准的 **方案 A：稳定型刷新**。

### Root / SubFolder 页流程

1. 页面创建时：
   - IO 线程调用 `getFolders(path, false)`。
   - 立即渲染结果（缓存或首次直列结果）。
2. 渲染后：
   - 再启动一次后台 `getFolders(path, true)`。
3. 后台刷新完成时：
   - 如果页面还停留在同一路径，则一次性 `setFolders(newList)`。
   - 不逐条 append / remove。

### 为什么不用一条条冒出来

- 会导致页面跳动。
- 用户已经明确偏好“突然刷新/整页稳定替换”。

---

## 与当前调用链的关系

### Rust 侧

需要重点改造：

- `D:/Erl-Project/WebDAVToon/rust-core/src/database.rs`
- `D:/Erl-Project/WebDAVToon/rust-core/src/repository.rs`
- `D:/Erl-Project/WebDAVToon/rust-core/src/remote_fs.rs`
- `D:/Erl-Project/WebDAVToon/rust-core/src/models.rs`（如需扩展返回模型）

### Kotlin 侧

需要重点改造：

- `D:/Erl-Project/WebDAVToon/app/src/main/java/erl/webdavtoon/RustWebDavPhotoRepository.kt`
- `D:/Erl-Project/WebDAVToon/app/src/main/java/erl/webdavtoon/FolderViewActivity.kt`
- `D:/Erl-Project/WebDAVToon/app/src/main/java/erl/webdavtoon/SubFolderActivity.kt`
- `D:/Erl-Project/WebDAVToon/app/src/main/java/erl/webdavtoon/FolderState.kt`（如需补充“保持旧列表 + loading”的状态表达）

### 关键收口点

所有优化都应该收口到：

- Rust 的 `get_folders()` 行为改变
- Kotlin 对 `getFolders(path, false)` + `getFolders(path, true)` 的两阶段调用

这样可以最小化对图片页和其他功能的侵入。

---

## 失败处理与兜底

### 远端刷新失败

- 保留并继续返回旧缓存。
- 更新 `last_error`，但不清空当前页数据。
- 页面可提示“刷新失败”，但不影响浏览已缓存目录。

### 数据库迁移失败

- 保留旧 `photos/folders` 逻辑兜底。
- `get_folders()` 最差退回当前远端直列逻辑，但应避免重新启用“进入前递归全探测”。

### 真机验证前的保守原则

- 旧表不删。
- 旧 API 先兼容。
- 新路径出错时，优先返回旧缓存，不让页面变空白。

---

## 实现阶段建议

### Phase 1：底层索引能力

- 新增 `file_index` / `folder_index` schema 与 migration。
- 写入、读取、聚合重算、TTL 判断。

### Phase 2：Rust 目录读取改造

- 把 `get_folders()` 改成“缓存优先 / 一层直列 / 可选刷新”。
- 移除主读路径中对每个子目录 `recursive(true)` 的依赖。

### Phase 3：Kotlin 双阶段刷新

- 页面先读快路径结果，再后台触发强刷。
- 强刷完成后整页替换。

### Phase 4：真机验证

- 设备上验证首次进入、重复进入、下拉刷新、新增单图后的局部更新行为。

---

## 验收标准

满足以下条件即可认为方案达标：

1. WebDAV 目录进入时，用户不再等待对子目录逐个递归证明“里面有图”。
2. 已访问目录能从 `rust_core.db` 秒开。
3. 首次无缓存目录能通过一层直列快速进入。
4. 后台刷新完成后，当前页整页替换，不是一条条跳。
5. 单目录新增/删除图片时，只更新本目录与祖先聚合，不触发全库重建。

---

## 本设计最终决定

- **采用**：本地索引（`rust_core.db`）+ 目录聚合 + 稳定型整页刷新。
- **不采用**：进入目录时递归探测每个子目录整棵子树。
- **不采用**：全库预热 / 全库重建。
- **不采用**：第二个数据库或额外后台服务。