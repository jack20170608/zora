# zora-claude-tui-poc

使用 Java + JLine 构建 Claude-like TUI 的 POC 子模块。

## 目标

本模块验证 Java 是否能够构建一个类似 Claude 的聊天式终端交互壳。当前版本仅使用 mock assistant，不接入真实 Claude / Anthropic API。

## 功能

- 彩色欢迎页和提示符
- JLine 输入历史
- `:help`、`:clear`、`:config`、`/config`、`:exit`、`:quit` 内置命令
- mock assistant 流式输出效果
- 简单会话轮次状态
- 模块本地配置菜单与 properties 文件持久化

## 构建

```bash
mvn -pl zora-poc/zora-tui-poc/zora-claude-tui-poc test
```

## 运行

```bash
mvn -pl zora-poc/zora-tui-poc/zora-claude-tui-poc exec:java
```

启动后输入普通文本进行 mock 对话，输入 `:help` 查看命令，输入 `:exit` 退出。

## 配置菜单

在主提示符中输入 `:config` 或 `/config` 可进入配置菜单。菜单采用问答式交互：

1. `Model`：选择 mock 模型名称。
2. `Theme`：选择主题名称。
3. `Stream delay`：选择 mock 流式输出字符延迟。
4. `Save`：保存配置并返回主会话。
5. `Back`：放弃本次修改并返回主会话。

子菜单中可输入 `b`、`back`、`q` 或 `quit` 返回上一级或退出配置菜单。

## 配置文件

配置保存在模块本地文件：

```text
zora-poc/zora-tui-poc/zora-claude-tui-poc/config/zora-claude-tui.properties
```

支持的属性和值如下：

| 属性 | 支持值 | 默认值 | 说明 |
|---|---|---|---|
| `model` | `mock-claude`, `mock-opus`, `mock-sonnet` | `mock-claude` | 仅用于本地 mock assistant 展示，不代表真实 Claude 模型调用。 |
| `theme` | `light`, `dark` | `dark` | 当前为配置项保留值。 |
| `streamDelayMillis` | `0`, `8`, `20` | `8` | 控制 mock 流式输出的字符延迟毫秒数。 |

示例：

```properties
model=mock-opus
theme=light
streamDelayMillis=8
```

## 安全边界

本模块不读取 API Key，不调用真实 LLM，也不产生外部网络请求。`model` 配置只影响本地 mock assistant 的演示值，不会访问真实 Claude / Anthropic 服务。后续如果需要接入真实 Claude API，应新增独立客户端抽象，并使用官方 Anthropic Java SDK。