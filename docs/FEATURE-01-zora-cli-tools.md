# FEATURE-01: zora-cli-tools 命令行工具父模块

## 概述

zora-cli-tools 是 zora 项目的一个子模块，作为多个命令行工具的父模块，用于组织和管理各种命令行工具。

## 模块设计

### 定位

zora-cli-tools 是一个 `pom` 类型的 Maven 项目，它本身不包含具体的命令行实现，而是作为父模块容纳多个独立的命令行工具子模块。这样设计的好处是：

1. **模块化管理**：每个命令行工具都是独立的子模块，可以独立开发、测试和发布
2. **依赖统一管理**：所有命令行工具共享相同的基础依赖配置
3. **易于扩展**：新增命令行工具只需要添加新的子模块即可

### 依赖管理

zora-cli-tools 已经引入了以下核心依赖：

- **zora-common**：通用工具类
- **slf4j-api**：日志接口
- **picocli**：命令行参数解析框架（推荐用于构建现代化的 CLI 应用）

### 目录结构

```
zora-cli-tools/
├── pom.xml              # 父模块 POM 文件，packaging = pom
├── src/
│   ├── main/
│   │   └── resources/   # 公共资源文件目录
│   └── test/
│       └── resources/   # 测试资源文件目录
└── [cli-tool-1]/        # 具体命令行工具子模块 1
└── [cli-tool-2]/        # 具体命令行工具子模块 2
```

## 使用方式

### 添加新的命令行工具

1. 在 `zora-cli-tools` 目录下创建新的子模块目录
2. 创建子模块的 `pom.xml`，继承自 `zora-cli-tools`
3. 在 `zora-cli-tools/pom.xml` 的 `<modules>` 中添加新子模块
4. 在根 `pom.xml` 中无需修改，因为 zora-cli-tools 已经注册

### 示例子模块 POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>top.ilovemyhome.zora</groupId>
        <artifactId>zora-cli-tools</artifactId>
        <version>${revision}</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <groupId>top.ilovemyhome.zora</groupId>
    <artifactId>zora-cli-tools-[tool-name]</artifactId>
    <name>zora-cli-tools-[tool-name] - [Tool Description]</name>
    <description>[Detailed description]</description>

    <!-- 可以打包成可执行 jar -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>your.main.Class</mainClass>
                        </manifest>
                    </archive>
                    <descriptorRefs>
                        <descriptorRef>jar-with-dependencies</descriptorRef>
                    </descriptorRefs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## 为什么选择 Picocli

Picocli 是一个现代化的 Java 命令行解析框架，具有以下优点：

- 注解驱动的编程模型，代码简洁
- 支持子命令、TAB 补全、颜色输出
- 零依赖（可选依赖）
- 活跃维护，文档完善

## 注意事项

1. 每个命令行工具应该保持单一职责，只做一件事并做好
2. 所有 CLI 工具都应该提供友好的帮助信息
3. 遵循 picocli 的最佳实践，使用注解方式定义命令
