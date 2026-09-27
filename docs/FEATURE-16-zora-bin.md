# FEATURE-16: zora-bin Java 应用生命周期脚本

## 目标

把常用的 Java 应用后台启动、优雅停止、状态检查统一封装为一个可通过 Maven 引入的资源模块，避免每个应用重复维护 shell 脚本。

## 设计

- `zora-bin` 是普通 JAR 模块，脚本存放在 JAR 的 `bin/` 下；应用打包时解压脚本，并与应用 JAR 一同部署。不会在 Java 类路径加载时执行脚本。
- 应用根目录包含 `active -> 版本目录`、历史版本目录以及共享的 `logs/`、`run/`；各版本分别包含 `app.jar`、可选 `lib/` 和 `bin/`。
- 启动时写入 PID 和实际 JAR 路径，记录日志、检测短时间内退出；重复启动拒绝。停止前核对进程命令行上的 `-jar` 参数，避免误停 PID 复用后的其他进程；超时不发送 SIGKILL。
- `deploy.sh VERSION JAR [LIB_DIR]` 创建不可变版本，`deploy.sh --activate VERSION` 切回已有版本；发布期间保持旧版本，切换失败或新版本启动失败会恢复旧指向并尽力重启旧进程。
- `status.sh` 的退出码可供监控使用；部署和普通启停使用锁隔离。非正常中断遗留的锁需要运维人员确认无操作正在执行后手动删除。

## 使用

具体 Maven 依赖、解包配置、部署步骤和测试命令见 [`zora-bin/README.md`](../zora-bin/README.md)。目标运行环境为 Bash 及支持相应进程接口的 Unix 系统；Windows 原生服务管理不在此模块范围内。
