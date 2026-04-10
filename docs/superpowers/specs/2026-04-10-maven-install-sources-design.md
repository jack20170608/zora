# 设计：Maven Install 时同时安装源码

## 背景

当前项目执行 `mvn install` 时只安装编译后的 class jar 包，不安装源码。用户希望在执行 `mvn install` 时也能同时安装源码，方便在 IDE 中调试时查看源代码。

## 设计决策

- **配置方式：** 全局配置在根 POM，所有模块都生效
- **插件版本：** 使用 maven-source-plugin 最新稳定版本 3.3.1
- **执行时机：** 绑定到 verify 阶段，在 install 之前自动执行

## 配置内容

在根 `pom.xml` 的 `<build><plugins>` 中添加以下配置：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-source-plugin</artifactId>
    <version>3.3.1</version>
    <executions>
        <execution>
            <phase>verify</phase>
            <goals>
                <goal>jar-no-fork</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## 效果

执行 `mvn install` 后，每个模块会生成两个文件安装到本地仓库：
- `xxx-1.0.1-SNAPSHOT.jar` - 编译后的 class 文件
- `xxx-1.0.1-SNAPSHOT-sources.jar` - 源代码

IDE（如 IntelliJ IDEA）会自动关联源码，调试时可以直接查看。

## 范围影响

- 只修改根 `pom.xml` 一个文件
- 不改变现有构建逻辑，只是增加源码构建步骤
- 所有子模块自动继承，不需要逐个修改
