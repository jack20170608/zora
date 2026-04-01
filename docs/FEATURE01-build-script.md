# 构建脚本功能说明

## 概述

为 Zora 项目添加了便捷的构建脚本，支持多种构建命令，简化 Maven 多模块项目的构建流程。

## 文件

- `build.sh` - Bash 版本（Linux/macOS/Git Bash/WSL）
- `build.bat` - Windows 批处理版本（Windows CMD）

## 使用方法

### 基本命令

```bash
# 完整构建（清理 -> 编译 -> 测试 -> 打包 -> 安装）
./build.sh

# 或者指定命令
./build.sh build

# 快速构建（跳过测试）
./build.sh quick

# 仅清理
./build.sh clean

# 仅编译
./build.sh compile

# 运行所有测试
./build.sh test

# 打包（跳过测试）
./build.sh package

# 安装到本地 Maven 仓库（跳过测试）
./build.sh install

# 查看帮助
./build.sh help
```

### Windows 使用方法

```batch
:: 完整构建
build.bat

:: 快速构建（跳过测试）
build.bat quick
```

## 功能特性

- 彩色输出，便于阅读
- 错误处理，构建失败立即退出
- 检查 Maven 是否安装
- **自动从 `VERSION` 文件读取项目版本号**，传递给 Maven
- 支持多种构建粒度选择
- 兼容 Maven 多模块项目结构

## 版本管理

项目版本号统一在根目录 `VERSION` 文件中管理，构建脚本会自动读取并传递给 Maven。修改版本时只需要编辑这个文件：

```
# 修改版本
echo "1.0.1" > VERSION
```

## 注意事项

- 确保已安装 Maven 3.x 并配置到 PATH 环境变量
- 项目使用 Java 25 版本，请确保配置正确的 JDK 版本
- 默认跳过测试的命令（package/install/quick）会加速构建，开发时建议定期运行完整测试
