# zora-claude-cui-poc 持久化 Config 菜单设计

## 背景

`zora-claude-cui-poc` 当前已经提供 Java + JLine 的 Claude-like 聊天式终端 POC，支持欢迎页、输入历史、`:help`、`:clear`、`:exit`、`:quit`、mock assistant 和流式输出效果。本设计在现有 REPL 基础上增加 `:config` / `/config` 问答式配置菜单，用于验证类似 Claude Code `/config` 的菜单化配置体验。

第一版选择问答式菜单而不是方向键 TUI 菜单，目标是低风险验证配置菜单、运行时配置状态和本地持久化能力。后续如需方向键交互，可复用本设计中的配置模型和持久化层，仅替换菜单输入与渲染层。

## 目标

- 支持 `:config` 和 `/config` 进入配置菜单。
- 使用问答式菜单修改 `model`、`theme`、`streamDelayMillis`。
- 将配置持久化到模块本地 properties 文件。
- 下次启动时加载已保存配置。
- 保存后的 `streamDelayMillis` 对当前进程后续 mock streaming 输出立即生效。
- 保持现有聊天 REPL、mock assistant 和命令解析结构清晰可测试。

## 非目标

- 不实现方向键、全屏刷新或高亮选择器。
- 不接入真实 Claude / Anthropic API。
- 不读取 API Key、token、环境变量或 secret manager。
- 不执行 shell 命令，不访问网络。
- 不实现用户级配置目录，如 `~/.zora`。

## 配置文件

配置文件路径固定为模块本地：

```text
zora-poc/zora-cui-poc/zora-claude-cui-poc/config/zora-claude-cui.properties
```

格式使用 Java `Properties`：

```properties
model=mock-claude
theme=dark
streamDelayMillis=8
```

默认值：

| Key | Default | Allowed values |
|---|---|---|
| `model` | `mock-claude` | `mock-claude`, `mock-opus`, `mock-sonnet` |
| `theme` | `dark` | `light`, `dark` |
| `streamDelayMillis` | `8` | `0`, `8`, `20` |

加载规则：

- 文件不存在时返回默认配置，不立即创建文件。
- 文件存在但 key 缺失时，缺失项使用默认值。
- 值非法时，该项回退默认值。
- 保存时写入完整配置，确保三个 key 都存在。

## 架构

现有结构保持不变：

```text
ClaudeCuiApplication
  -> ClaudeCuiShell
      -> ClaudeCuiCommandParser
      -> MockAssistant
      -> StreamingPrinter
```

新增后：

```text
ClaudeCuiApplication
  -> ClaudeCuiConfigRepository
      load config/zora-claude-cui.properties
  -> ClaudeCuiShell
      -> ClaudeCuiCommandParser
      -> ConfigMenuController
          -> ClaudeCuiConfigRepository
          -> ClaudeCuiConfig
      -> MockAssistant
      -> StreamingPrinter
```

新增类：

- `ClaudeCuiConfig`
  - 不可变 `record`。
  - 保存 `model`、`theme`、`streamDelayMillis`。
  - 提供默认配置、允许值校验和 copy-on-write 方法。

- `ClaudeCuiConfigRepository`
  - 通过构造函数注入 `Path`，便于测试使用临时目录。
  - 从 properties 文件加载配置。
  - 保存配置，并在必要时创建 `config` 目录。

- `ConfigMenuController`
  - 管理问答式 config 菜单子循环。
  - 使用 `LineReader.readLine("config > ")` 读取选择。
  - 在菜单内维护临时配置副本。
  - 用户选择保存时调用 repository 保存。
  - 用户选择不保存时丢弃临时配置。

- `ConfigMenuOption`
  - 表达主菜单选项或值选项，避免在 controller 中散落 magic strings。

现有类调整：

- `ClaudeCuiCommandType` 增加 `CONFIG`。
- `ClaudeCuiCommandParser` 支持 `:config` 和 `/config`。
- `ClaudeCuiApplication` 启动时创建 repository 并加载配置。
- `ClaudeCuiShell` 收到 `CONFIG` 命令后打开菜单，保存后更新当前运行时配置。

## 菜单交互

主 REPL 中输入：

```text
:config
/config
```

进入主菜单：

```text
Config

1. Model              mock-claude
2. Theme              dark
3. Stream delay       8 ms
4. Save and back
5. Back without saving

Select >
```

选择 `1`：

```text
Select model

1. mock-claude
2. mock-opus
3. mock-sonnet
b. Back

Select >
```

选择 `2`：

```text
Select theme

1. light
2. dark
b. Back

Select >
```

选择 `3`：

```text
Select stream delay

1. 0 ms
2. 8 ms
3. 20 ms
b. Back

Select >
```

选择 `4`：

- 保存配置到 properties 文件。
- 返回主 REPL。
- 打印 `Config saved.`。
- 当前进程后续 streaming 输出使用新的 `streamDelayMillis`。

选择 `5`：

- 丢弃临时修改。
- 返回主 REPL。
- 打印 `Config unchanged.`。

在子菜单输入 `b` 或 `back` 返回主菜单。在主菜单输入 `b`、`back`、`q`、`quit` 等同于不保存退出。

遇到 `Ctrl+C` 或 EOF 时，不保存并返回主 REPL，打印 `Config unchanged.`。

非法选择打印：

```text
Unknown config option. Please try again.
```

## 错误处理

配置读取失败：

- 回退默认配置。
- 程序继续启动。
- 终端打印：`Failed to load config, using defaults.`

配置保存失败：

- 菜单不崩溃。
- 打印：`Failed to save config: <safe message>`。
- 保持在 config 菜单中，用户可以重试保存或选择不保存退出。

非法配置值：

- 加载时回退该项默认值。
- 菜单内只展示允许值。

## 测试策略

严格按 TDD 执行。

### 命令解析测试

扩展 `ClaudeCuiCommandParserTest`：

- `:config` 解析为 `CONFIG`。
- `/config` 解析为 `CONFIG`。
- 普通 `/hello` 仍解析为 `CHAT`。

### 配置模型测试

新增 `ClaudeCuiConfigTest`：

- 默认配置为 `mock-claude`、`dark`、`8`。
- copy-on-write 修改方法返回新实例，不修改原实例。
- 非法 model/theme/delay 回退默认值。

### 配置 repository 测试

新增 `ClaudeCuiConfigRepositoryTest`，使用临时目录：

- 文件不存在时返回默认配置。
- 保存后可以重新加载。
- 缺失 key 使用默认值。
- 非法值使用默认值。
- 保存时自动创建配置目录。

### 菜单 controller 测试

新增 `ConfigMenuControllerTest`：

- 选择 model 后保存会写入新配置。
- 选择 theme 后保存会写入新配置。
- 选择 stream delay 后保存会写入新 delay。
- 不保存退出不会写入临时修改。
- 非法输入后可以继续选择。
- EOF / Ctrl+C 退出且不保存。

### 集成验证

运行：

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc test
mvn -pl zora-poc/zora-cui-poc test
```

并使用脚本输入验证：

```bash
printf ':config\n1\n2\n4\n:exit\n' | mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc exec:java
```

## 文档更新

更新：

```text
zora-poc/zora-cui-poc/zora-claude-cui-poc/README.md
```

新增中文功能文档：

```text
docs/FEATURE-14-zora-claude-cui-config-menu.md
```

两处文档需说明：

- `:config` / `/config` 使用方式。
- 配置文件位置。
- 支持的配置项和值。
- 当前仍仅使用 mock assistant，不读取 API Key，不调用真实 LLM，不发起网络请求。

## 验收标准

- `:config` 和 `/config` 都能进入菜单。
- 菜单可以修改 `model`、`theme`、`streamDelayMillis`。
- 选择保存后写入模块本地 properties 文件。
- 程序下次启动能加载保存配置。
- 不保存退出时不会写入临时修改。
- 非法输入不会退出程序。
- 保存后的 `streamDelayMillis` 对后续 streaming 输出生效。
- 单元测试通过。
- 聚合模块测试通过。
- 未引入真实 Claude / Anthropic API、API Key、网络请求或 shell 命令执行。

## 自检

- 无 TBD/TODO/占位内容。
- 菜单范围聚焦于问答式持久化配置，不包含方向键 TUI。
- 架构职责清晰，配置模型、持久化、菜单控制和主 shell 分离。
- 测试覆盖解析、模型、持久化、菜单和集成验证。
