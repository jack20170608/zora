# FEATURE-13 zora-claude-tui-poc 模块

> 状态：设计中
> 关联模块：`zora-poc/zora-tui-poc/zora-claude-tui-poc`

## 1. 背景

`zora-poc` 用于隔离技术预研，避免实验性依赖进入正式模块。当前需要验证 Java 是否可以构建一个类似 Claude 的 TUI（Terminal User Interface）程序：在终端中提供聊天式交互、命令历史、彩色提示符和模拟流式输出。

本次仅验证终端交互壳，不接入真实 Claude / Anthropic API，不处理 API Key，也不产生真实 LLM 调用成本。后续如果需要接入真实模型，应单独设计 API 客户端层，并使用 Anthropic Java SDK；默认模型应使用 `claude-opus-4-8`，避免使用过期模型 ID 或已废弃参数。

## 2. 目标

本次新增 TUI POC 模块，目标如下：

1. 使用 JLine 构建一个可运行的 Java terminal chat shell。
2. 提供接近 Claude TUI 的基础体验：
   - 欢迎页
   - 彩色提示符
   - 输入历史
   - `:help`、`:clear`、`:exit`、`:quit` 等内置命令
   - mock assistant 的流式输出效果
   - 简单会话轮次状态
3. 支持 Maven 单模块构建、测试和运行。
4. 保持实验依赖只在 POC 模块内生效，不污染正式模块。
5. 配套生成模块 README、metadata、测试日志配置和本功能文档。

## 3. 非目标

本次明确不做以下内容：

- 不接入真实 Claude / Anthropic API。
- 不实现 API Key、鉴权、网络重试、限流处理。
- 不实现真实 token 流式事件解析。
- 不实现全屏 TUI、多面板布局或鼠标交互。
- 不实现复杂多行编辑、文件上传、工具调用、MCP 或 agent loop。
- 不把该 POC 作为生产可复用 CLI 框架发布。

## 4. 模块结构

新增模块采用两层结构：

```text
zora-poc/
└── zora-tui-poc/
    ├── pom.xml
    ├── metadata/
    │   └── metadata.json
    ├── README.md
    └── zora-claude-tui-poc/
        ├── pom.xml
        ├── metadata/
        │   └── metadata.json
        ├── README.md
        └── src/
            ├── main/
            │   ├── java/
            │   │   └── top/ilovemyhome/zora/poc/tui/claude/...
            │   └── resources/
            └── test/
                ├── java/
                └── resources/
                    └── simplelogger.properties
```

- `zora-tui-poc`：TUI / terminal UI 技术预研聚合模块，`packaging=pom`。
- `zora-claude-tui-poc`：具体 JLine 聊天式 TUI 实验模块。

虽然 `zora-poc` 的基础原则是实验代码通常放在 `src/test/java`，但本模块需要验证"Java 能否 build 出可运行 TUI 程序"。因此可执行入口和核心交互逻辑放在 `src/main/java`，测试放在 `src/test/java`。该例外仅限本 POC，用于验证真实 CLI 启动体验。

## 5. 类划分

计划包名：

```text
top.ilovemyhome.zora.poc.tui.claude
```

核心类：

| 类 | 职责 |
|---|---|
| `ClaudeTuiApplication` | `main` 入口，创建 JLine `Terminal`、`LineReader` 并启动 shell。 |
| `ClaudeTuiShell` | 主交互循环，读取用户输入、分发命令、输出响应。 |
| `ClaudeTuiCommand` | 命令枚举或解析结果，表示 `HELP`、`CLEAR`、`EXIT`、`CHAT` 等类型。 |
| `ClaudeTuiCommandParser` | 解析 `:help`、`:clear`、`:exit`、`:quit` 和普通聊天输入。 |
| `MockAssistant` | 根据用户输入生成 deterministic mock 响应，便于测试。 |
| `StreamingPrinter` | 模拟流式输出，隔离 sleep 与输出逻辑，便于测试时禁用延迟。 |
| `ConversationState` | 保存会话状态，例如轮次计数和退出标记。 |

设计原则：

- `main` 只做组装，不承载业务逻辑。
- 命令解析、状态管理、 mock 响应和打印逻辑分离。
- 单元测试优先覆盖纯逻辑类，交互循环只做轻量测试。
- 输出样式集中管理，避免 ANSI 字符串散落在各处。

## 6. 交互流程

启动后显示欢迎页：

```text
╭────────────────────────────────────────╮
│ Zora Claude TUI POC                    │
│ Type :help for commands, :exit to quit │
╰────────────────────────────────────────╯
```

普通对话示例：

```text
you > hello
claude > I received: hello
         This is a mock streaming response from zora-claude-tui-poc.
```

内置命令：

| 命令 | 行为 |
|---|---|
| `:help` | 显示可用命令。 |
| `:clear` | 清屏并重新显示欢迎页。 |
| `:exit` | 设置退出状态并结束 shell。 |
| `:quit` | 等同于 `:exit`。 |
| 空输入 | 忽略，不增加会话轮次。 |
| 普通文本 | 作为聊天消息交给 `MockAssistant`。 |

## 7. 依赖与构建

`zora-claude-tui-poc` 新增 JLine 依赖，作用范围限制在该模块内。建议使用：

- `org.jline:jline`：终端读取、历史、ANSI 输出等基础能力。
- JUnit 5、AssertJ、Mockito、slf4j-simple：按项目现有 POC 测试约定添加。

如果根项目已有依赖管理条目，则复用 dependencyManagement；如果没有，则在本 POC 模块中显式声明 JLine 版本，避免影响其他模块。

构建验证命令：

```bash
mvn -pl zora-poc/zora-tui-poc/zora-claude-tui-poc test
```

运行验证命令：

```bash
mvn -pl zora-poc/zora-tui-poc/zora-claude-tui-poc exec:java \
  -Dexec.mainClass=top.ilovemyhome.zora.poc.tui.claude.ClaudeTuiApplication
```

如项目父 POM 未配置 `exec-maven-plugin`，则在该 POC 子模块内添加插件配置，仅用于本模块运行验证。

## 8. 错误处理

本 POC 的错误处理范围保持简单：

- `UserInterruptException` 或 Ctrl+C：优雅退出 shell。
- `EndOfFileException` 或 Ctrl+D：优雅退出 shell。
- 未知 `:` 命令：显示提示信息，并建议输入 `:help`。
- mock assistant 生成异常：显示用户友好的错误信息，不输出 Java stack trace 到交互界面。
- Terminal 初始化失败：在 `main` 层打印简短错误并以非 0 状态结束。

## 9. 测试设计

计划测试覆盖：

| 测试类 | 覆盖内容 |
|---|---|
| `ClaudeTuiCommandParserTest` | 内置命令、空输入、普通聊天输入、未知命令解析。 |
| `ConversationStateTest` | 轮次增加、退出标记、初始状态。 |
| `MockAssistantTest` | mock 响应稳定、包含用户输入、空输入不应进入响应生成。 |
| `StreamingPrinterTest` | 无延迟模式下能按顺序输出所有 token / 字符片段。 |

交互式 shell 本身不做复杂 E2E 自动化；本次主要通过单元测试和手动运行命令验证。

## 10. 后续演进

如果本 POC 验证通过，后续可以按需演进：

1. 引入真实 Anthropic Java SDK 客户端，但保持 mock 与真实客户端可切换。
2. 使用 `ANTHROPIC_API_KEY` 从环境变量读取密钥，禁止硬编码。
3. 对真实 API 调用默认使用 `claude-opus-4-8`，并使用官方 Java SDK。
4. 对长输出使用 streaming，避免非流式大响应超时。
5. 进一步增强 JLine 能力，例如补全、命令历史文件、多行输入模式。
6. 如果需要更完整的视觉界面，再评估 Lanterna 或其他 TUI 框架。