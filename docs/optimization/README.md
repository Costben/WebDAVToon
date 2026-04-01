# WebDAVToon 优化改造总索引

> 创建日期：2026-04-01
> 版本：v1.1.3

---

## 改造总览

| 优先级 | 编号 | 模块 | 文件 | 状态 |
|--------|------|------|------|------|
| P0 | 01 | 分页加载算法 | [P01-pagination.md](P01-pagination.md) | 待实施 |
| P0 | 02 | 数据层架构 | [P02-data-layer.md](P02-data-layer.md) | 待实施 |
| P1 | 03 | 图片加载策略 | [P03-image-loader.md](P03-image-loader.md) | 待实施 |
| P1 | 04 | 网络请求 | [P04-network.md](P04-network.md) | 待实施 |
| P1 | 05 | 权限处理 | [P05-permission.md](P05-permission.md) | 待实施 |
| P2 | 06 | 缩放算法 | [P06-zoom.md](P06-zoom.md) | 待实施 |
| P2 | 07 | 文件夹分组 | [P07-folder-group.md](P07-folder-group.md) | 待实施 |
| P2 | 08 | Adapter 重构 | [P08-adapter-refactor.md](P08-adapter-refactor.md) | 待实施 |
| P3 | 09 | URL 编码 | [P09-url-encoding.md](P09-url-encoding.md) | 待实施 |
| P3 | 10 | 日志系统 | [P10-logging.md](P10-logging.md) | 待实施 |

---

## 推荐执行顺序

```
P0-2 第1~3步（DataStore + 槽位迁移）
    ↓
P0-2 第4步 + P0-1 第3步（Room + 收藏分页联动）
    ↓
P0-1 第1~2步（本地/远程分页改造）
    ↓
P0-1 第4~5步（排序优化 + PhotoCache 优化）
    ↓
P0-2 第5~7步（ViewModel 改造 + 调用方更新 + 数据迁移）
    ↓
P1 全部（图片加载 → 网络请求 → 权限，三者独立可并行）
    ↓
P2 全部（缩放 → 文件夹分组 → Adapter，三者独立可并行）
    ↓
P3 全部（URL 编码 → 日志系统，两者独立可并行）
```

---

## 依赖关系

- P0-1 第3步（收藏分页）依赖 P0-2 第4步（Room）
- P0-2 第6步（调用方更新）依赖 P0-1 全部完成
- P1/P2/P3 各模块之间无强依赖，可并行实施
