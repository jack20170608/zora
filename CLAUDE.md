# CLAUDE.md

提供给CLAUDE的开发指引。

## General Guidelines
- 每次主要的功能或者架构调整，都在docs目录下写一份文档，文档名称请按照序号`01,02,03`加上前缀`ARCHITECTURE`或者`FEATURE`来命名，方便后续查阅。
- 代码提交时，请在commit message中注明本次提交的主要内容和目的，例如：`feat: add user authentication module` 或者 `refactor: optimize database connection handling
- 代码中请保持良好的注释习惯，尤其是在复杂的逻辑或者算法部分，确保其他开发者能够理解代码的意图和实现细节。
- 代码注释，包括但不限于：类注释、方法注释、重要逻辑的行内注释等，应该清晰、简洁，并且与代码保持同步更新，而且语言采用英文
- 项目开发生成的文档采用中文
- 初始化子模块的时候，同步创建`main/resource`和`test/resource`目录，方便后续存放配置文件和测试资源。
- 为每一个子模块都生成`metadata/metadata.json`文件，包含模块的基本信息、功能描述等内容，并把metadata放到resource里面，enable filter，方便后续的维护和管理,metadata文件的格式可以参考以下示例.
```json
{
  "groupId": "@project.groupId@",
  "artifactId": "@project.artifactId@",
  "description": "@project.description@",
  "version": "@project.version@",
  "scmUrl": "@project.scmUrl@"
}
```

- 为每一个子模块都生成README.md文件，详细说明该子模块的功能、使用方法和开发指南，确保其他开发者能够快速上手和理解该模块的作用和实现细节。

## jar模块的测试dependency如下
```xml
    <!-- Testing -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-simple</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-api</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-engine</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
```
