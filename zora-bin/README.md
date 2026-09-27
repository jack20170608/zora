# zora-bin

提供 Java 应用的 `start.sh`、`stop.sh`、`status.sh` 和版本管理 `deploy.sh`。这是**脚本资源 JAR**，不是 Java 主程序；依赖它本身不会自动在服务器上安装脚本。部署时先将 JAR 内的 `bin/` 解压到应用版本目录，后续也可以使用 `deploy.sh` 创建新版本。

## 部署布局

```text
my-app/
├── env.tag               # 环境标记文件(sit,uat,prod等, 可选, 如果没有的话从环境变量中读取APP_ENV)
├── active -> 1.2/        # 当前版本软链
├── 1.0/
├── 1.1/
├── 1.2/
│   ├── app.jar
│   ├── lib/               # 可选的应用依赖
│   ├── config/            # 可选的配置
│       ├── setenv            # 可选的环境变量设置
│       ├── setenv-sit        # 可选的环境变量设置（SIT 环境）
│       ├── setenv-prod       # 可选的环境变量设置（PROD 环境）
│       ├── application.sh    # 可选的应用配置
│       └── application-sit.conf  # 可选的应用配置(SIT 环境)
│   └── bin/
│       ├── lifecycle.sh
│       ├── start.sh
│       ├── stop.sh
│       ├── status.sh
│       └── deploy.sh
├── logs/                 # 自动创建
└── run/                  # 自动创建
```

运行环境需要 Bash、`nohup`、`ps`（macOS 后备识别方案）和 Java；Linux 上优先使用 `/proc`。`active` 必须指向应用根目录下的版本目录。将应用产物放在版本目录的 `app.jar`，或者设置绝对路径 `ZORA_APP_JAR`。脚本从任何目录调用均可：

```bash
chmod +x my-app/active/bin/*.sh
my-app/active/bin/start.sh --spring.profiles.active=prod
my-app/active/bin/status.sh
my-app/active/bin/stop.sh
```

`status.sh` 在运行时返回 0，未运行（含失效 PID）返回 1；`start.sh` 对重复启动和启动失败返回非 0；`stop.sh` 不会对 PID 文件中记录的无关进程发出信号，停止超时也不会强制终止。PID 文件同时保存启动时的实际版本 JAR 路径，避免切换 `active` 后误判进程。应用的标准输出和错误输出追加至共享的 `logs/app.log`。

| 环境变量 | 默认值 | 含义 |
| --- | --- | --- |
| `ZORA_APP_HOME` | 版本目录的上一级 | 应用部署根目录，建议绝对路径 |
| `ZORA_APP_JAR` | `$ZORA_APP_HOME/active/app.jar` | 要运行的 JAR，必须是绝对路径 |
| `ZORA_JAVA_CMD` | `java` | Java 可执行程序路径或命令 |
| `ZORA_JAVA_OPTS` | 空 | 空格分隔的 JVM 选项；含空格的选项请使用 Java argfile（如 `@/path/options.txt`） |
| `ZORA_RUN_DIR` | `$ZORA_APP_HOME/run` | PID 和锁目录 |
| `ZORA_LOG_DIR` | `$ZORA_APP_HOME/logs` | 日志目录 |
| `ZORA_STOP_TIMEOUT` | `30` | 等待优雅停止的秒数 |

运行参数可直接传给 `start.sh`；例如 `start.sh --server.port=8080`。应用需自行处理 SIGTERM 以实现优雅退出。

## 发布及回退

首次发布时，从解压到 `target/dist/bin/` 的脚本执行发布（`ZORA_APP_HOME` 指向应用根目录）；由于此前没有运行中的版本，首次发布不会自动启动应用：

```bash
mkdir -p my-app
ZORA_APP_HOME="$(cd my-app && pwd -P)" bash target/dist/bin/deploy.sh 1.0 /path/to/my-app-1.0.jar
my-app/active/bin/start.sh
```

以后可通过当前版本的脚本发布新版本：

```bash
my-app/active/bin/deploy.sh 1.3 /path/to/my-app-1.3.jar /path/to/lib
my-app/active/bin/deploy.sh --activate 1.2   # 回退到已经发布的版本
```

`LIB_DIR` 可省略，目录会复制为版本目录的 `lib/`；只有应用自身支持加载 `lib/` 时才需要它（普通 `java -jar` 不会自动加载该目录）。新版本号只允许字母、数字、点、下划线和短横线，且不得包含 `..`；已有版本不可覆盖。发布过程先暂存并复制新版本，再停止当前进程、切换 `active`，如果原进程在运行则启动新版本。新版本启动失败时恢复旧 `active` 并尝试重启旧进程；原先未运行则不自动启动。旧版本目录不会自动删除。进程日志及 PID 始终位于应用根目录。

发布与普通启停使用互斥锁；意外中断遗留锁时，需确认没有进行中的操作后手动删除 `run/.deploy.lock` 或 `run/.lifecycle.lock`。生产环境发布前请先备份应用及数据；回退只切换应用版本，不回退数据库。

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

将应用 JAR 重命名为 `app.jar`，与 `target/dist/bin/` 一起放入版本目录（例如 `my-app/1.0/`），再创建 `active` 软链；复制到 Unix 主机后执行 `chmod +x bin/*.sh`。脚本面向 Linux/macOS 部署；Windows Git Bash 不支持此处的 Unix 软链语义或 Windows 原生 Java 进程管理。

## 开发与测试

从仓库根目录读取 `VERSION` 构建独立模块（需先安装父 POM/BOM）：

```bash
mvn -pl zora-bin -am test -Drevision="$(tr -d '[:space:]' < VERSION)"
bash zora-bin/src/test/scripts/test-lifecycle.sh
```

Shell 集成测试使用临时文件和模拟的 Java 进程，需在支持 Unix 软链的 Linux/macOS 下运行；可用 `bash -n zora-bin/src/main/resources/bin/*.sh` 做语法检查。
