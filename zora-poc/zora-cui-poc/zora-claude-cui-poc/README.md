# zora-claude-cui-poc

使用 Java + JLine 构建 Claude-like CUI 的 POC 子模块。

## 目标

本模块验证 Java 是否能够构建一个类似 Claude 的聊天式终端交互壳。当前版本仅使用 mock assistant，不接入真实 Claude / Anthropic API。

## 功能

- 彩色欢迎页和提示符
- JLine 输入历史
- `:help`、`:clear`、`:exit`、`:quit` 内置命令
- mock assistant 流式输出效果
- 简单会话轮次状态

## 构建

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc test
```

## 运行

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc exec:java
```

启动后输入普通文本进行 mock 对话，输入 `:help` 查看命令，输入 `:exit` 退出。

## 说明

本模块不读取 API Key，不调用真实 LLM，也不产生外部网络请求。后续如果需要接入真实 Claude API，应新增独立客户端抽象，并使用官方 Anthropic Java SDK。
