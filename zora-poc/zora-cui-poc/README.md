# zora-cui-poc

CUI（Command-line User Interface）技术预研聚合模块。

## 目的

本模块用于聚合 Java 终端交互、REPL、聊天式命令行界面等方向的 POC 子模块。当前包含 `zora-claude-cui-poc`，用于验证 Java + JLine 能否构建类似 Claude 的聊天式终端交互壳。

## 模块列表

| 模块 | 说明 |
|---|---|
| `zora-claude-cui-poc` | 使用 JLine 构建 Claude-like mock CUI。 |

## 开发约定

- POC 依赖应限制在本聚合模块或叶子模块中，不影响正式功能模块。
- 每个叶子模块应提供 README、metadata 和测试资源配置。
- 可运行 CUI POC 允许在 `src/main/java` 中提供入口类，便于验证真实启动体验。
