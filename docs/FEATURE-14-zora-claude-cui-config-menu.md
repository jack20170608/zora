# FEATURE-14 zora-claude-cui-poc 配置菜单

## 背景

`zora-claude-cui-poc` 是一个基于 Java 与 JLine 的 Claude-like 终端交互 POC。此前模块已经具备欢迎页、命令解析、mock assistant 流式输出和退出等基础能力，但缺少可交互修改运行参数的入口。

本功能为 POC 增加 `:config` / `/config` 配置菜单，用于验证终端子循环、配置持久化和运行时参数更新的可行性。当前配置仍然只服务于本地 mock 行为，不接入真实 Claude / Anthropic API。

## 范围

本次功能覆盖以下内容：

- 在主会话中识别 `:config` 与 `/config` 命令。
- 打开问答式配置菜单，支持选择模型、主题和流式输出延迟。
- 将配置保存到模块本地 properties 文件。
- 启动时读取配置文件；配置缺失或非法时回退到默认值。
- 保存新的 `streamDelayMillis` 后，后续 mock assistant 输出立即使用新的流式延迟。

不在本次范围内的内容：

- 不调用真实 Claude / Anthropic API。
- 不读取或保存 API Key。
- 不建立外部网络连接。
- 不实现真实主题渲染系统；`theme` 当前作为可持久化配置值保留。

## 配置文件

配置文件路径为：

```text
zora-poc/zora-cui-poc/zora-claude-cui-poc/config/zora-claude-cui.properties
```

支持的配置项：

| 配置项 | 支持值 | 默认值 | 说明 |
|---|---|---|---|
| `model` | `mock-claude`, `mock-opus`, `mock-sonnet` | `mock-claude` | 本地 mock assistant 的模型标签。 |
| `theme` | `light`, `dark` | `dark` | 主题配置保留值。 |
| `streamDelayMillis` | `0`, `8`, `20` | `8` | mock 流式输出的字符延迟毫秒数。 |

示例 properties：

```properties
model=mock-opus
theme=light
streamDelayMillis=8
```

如果配置文件不存在，程序使用默认配置启动。保存配置时会自动创建 `config` 目录与 properties 文件。

## 交互说明

在主提示符中输入以下任一命令进入配置菜单：

```text
:config
/config
```

配置菜单主项：

```text
Config
Current model: mock-claude
Current theme: dark
Current stream delay: 8
1. Model
2. Theme
3. Stream delay
4. Save
5. Back
```

交互规则：

- 输入 `1` 进入模型选择子菜单。
- 输入 `2` 进入主题选择子菜单。
- 输入 `3` 进入流式延迟选择子菜单。
- 输入 `4` 保存当前草稿配置并返回主会话。
- 输入 `5` 放弃当前草稿配置并返回主会话。
- 在子菜单中输入 `b`、`back`、`q` 或 `quit` 可返回或退出配置菜单。
- 遇到 EOF 或 Ctrl+C 时，配置菜单返回原配置，不保存草稿。

示例脚本输入：

```bash
printf ':config\n1\n2\n4\n:exit\n' | mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc exec:java
```

上述输入含义为：进入配置菜单，选择模型菜单，选择第二个模型值，保存配置，然后退出主会话。

## 安全边界

本功能仅使用 Java `Properties` 在模块本地文件系统读写配置。安全边界如下：

- 不读取 `ANTHROPIC_API_KEY`、`API_KEY` 或任何环境变量中的密钥。
- 不保存 API Key、Token、密码或其他凭据。
- 不创建 HTTP 客户端，不访问真实 Claude / Anthropic API。
- 不产生外部网络请求。
- `model` 的支持值全部为 `mock-*`，只用于本地演示和输出标签，不代表真实模型路由。

因此，当前配置菜单是离线 mock POC 功能；未来若需要接入真实 Claude API，应新增独立客户端抽象、显式配置安全边界，并使用官方 Anthropic Java SDK。

## 验证命令

### 1. 运行父 POC 模块测试

```bash
mvn -pl zora-poc/zora-cui-poc test
```

预期结果：

```text
BUILD SUCCESS
```

### 2. 运行脚本化配置菜单验证

```bash
printf ':config\n1\n2\n4\n:exit\n' | mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc exec:java
```

预期输出包含：

```text
Config
Select model
Config saved.
bye
BUILD SUCCESS
```

### 3. 验证配置文件存在

```bash
test -f zora-poc/zora-cui-poc/zora-claude-cui-poc/config/zora-claude-cui.properties
```

预期结果：命令退出码为 `0`，表示配置文件已经生成。