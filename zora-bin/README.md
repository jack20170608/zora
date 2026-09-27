# zora-bin

提供 Java 应用的 `start.sh`、`stop.sh`、`status.sh`，以及内部共享的 `lifecycle.sh`。这是**脚本资源 JAR**，不是 Java 主程序；依赖它本身不会自动在服务器上安装脚本，部署时须将 JAR 内 `bin/` 目录解压到应用目录。

## 部署布局

```text
my-app/
├── app.jar
├── bin/
│   ├── lifecycle.sh
│   ├── start.sh
│   ├── stop.sh
│   └── status.sh
├── logs/                 # 自动创建
└── run/                  # 自动创建
```

运行环境需要 Bash、`nohup`、`ps`（macOS 后备识别方案）和 Java；Linux 上优先使用 `/proc`。将应用产物放在 `app.jar`，或者设置绝对路径 `ZORA_APP_JAR`。脚本从任何目录调用均可：

```bash
chmod +x my-app/bin/*.sh
my-app/bin/start.sh --spring.profiles.active=prod
my-app/bin/status.sh
my-app/bin/stop.sh
```

`status.sh` 在运行时返回 0，未运行（含失效 PID）返回 1；`start.sh` 对重复启动和启动失败返回非 0；`stop.sh` 不会对 PID 文件中记录的无关进程发出信号，停止超时也不会强制终止。应用的标准输出和错误输出追加至 `logs/app.log`。

| 环境变量 | 默认值 | 含义 |
| --- | --- | --- |
| `ZORA_APP_HOME` | `bin/` 的上一级 | 应用部署目录，建议绝对路径 |
| `ZORA_APP_JAR` | `$ZORA_APP_HOME/app.jar` | 要运行的 JAR，必须是绝对路径 |
| `ZORA_JAVA_CMD` | `java` | Java 可执行程序路径或命令 |
| `ZORA_JAVA_OPTS` | 空 | 空格分隔的 JVM 选项；含空格的选项请使用 Java argfile（如 `@/path/options.txt`） |
| `ZORA_RUN_DIR` | `$ZORA_APP_HOME/run` | PID 和锁目录 |
| `ZORA_LOG_DIR` | `$ZORA_APP_HOME/logs` | 日志目录 |
| `ZORA_STOP_TIMEOUT` | `30` | 等待优雅停止的秒数 |

运行参数可直接传给 `start.sh`；例如 `start.sh --server.port=8080`。应用需自行处理 SIGTERM 以实现优雅退出。

## Maven 应用集成

在应用 POM 中添加依赖（使用 zora-bom 管理版本时可省略依赖上的 version）：

```xml
<dependency>
    <groupId>top.ilovemyhome.zora</groupId>
    <artifactId>zora-bin</artifactId>
    <version>${zora.version}</version>
    <scope>runtime</scope>
</dependency>
```

打包时解压脚本资源（在应用 POM 中定义 `zora.version` 为所使用的 Zora 发布版本，如 `1.0.3`）：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <version>3.7.1</version>
    <executions>
        <execution>
            <id>unpack-zora-bin</id>
            <phase>package</phase>
            <goals><goal>unpack</goal></goals>
            <configuration>
                <artifactItems>
                    <artifactItem>
                        <groupId>top.ilovemyhome.zora</groupId>
                        <artifactId>zora-bin</artifactId>
                        <version>${zora.version}</version>
                        <type>jar</type>
                        <includes>bin/*.sh</includes>
                        <outputDirectory>${project.build.directory}/dist</outputDirectory>
                    </artifactItem>
                </artifactItems>
            </configuration>
        </execution>
    </executions>
</plugin>
```

将应用 JAR 重命名为 `app.jar` 与 `target/dist/bin/` 一起发布；复制到 Unix 主机后执行 `chmod +x bin/*.sh`。脚本面向 Linux/macOS 部署；Windows Git Bash 可运行模拟进程测试，但不支持 Windows 原生 Java 进程的生命周期管理。

## 开发与测试

从仓库根目录读取 `VERSION` 构建独立模块（需先安装父 POM/BOM）：

```bash
mvn -pl zora-bin -am test -Drevision="$(tr -d '[:space:]' < VERSION)"
bash zora-bin/src/test/scripts/test-lifecycle.sh
```

Shell 集成测试使用临时文件和模拟的 Java 进程，不依赖生产应用；可用 `bash -n zora-bin/src/main/resources/bin/*.sh` 做语法检查。
