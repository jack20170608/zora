# FEATURE-02: zora-flyway-cli Flyway 数据库迁移命令行工具

## 概述

zora-flyway-cli 是一个基于 Flyway 的轻量级数据库迁移命令行工具，封装了 Flyway 核心功能，提供简洁的命令行接口，方便在 CI/CD 流程和脚本中使用。

## 功能特性

- 支持所有 Flyway 核心操作：migrate、clean、info、repair、validate
- 支持通过命令行参数直接配置数据库连接
- 支持显式指定 JDBC 驱动类名
- 支持占位符替换，可多次指定多个占位符
- 支持开关控制是否启用占位符替换
- 支持加载外部配置文件
- 支持基线版本配置

## 命令行参数说明

### 全局参数

所有子命令都支持以下参数：

| 参数 | 简写 | 必填 | 类型 | 默认值 | 说明 |
|------|------|------|------|---------|------|
| `--url` | `-u` | 是 | String | - | JDBC 数据库连接 URL |
| `--username` | `--user` | 是 | String | - | 数据库用户名 |
| `--password` | `-p` | 是 | String | - | 数据库密码 |
| `--driver` | `-d` | 否 | String | - | JDBC 驱动类名，不指定则由 Flyway 自动推断 |
| `--locations` | `-l` | 否 | String | `filesystem:./sql` | 迁移脚本位置，多个位置用逗号分隔 |
| `--baseline-version` | `-b` | 否 | String | - | 已有数据库的基线版本 |
| `--config-file` | `-c` | 否 | File | - | Flyway 配置文件路径 |
| `--placeholder` | `-P` | 否 | Map | - | 占位符，格式 `key=value`，可多次指定 |
| `--placeholder-replacement` | - | 否 | boolean | `true` | 是否启用占位符替换 |
| `--help` | `-h` | 否 | boolean | - | 显示帮助信息 |
| `--version` | `-v` | 否 | boolean | - | 显示版本信息 |

### 可用子命令

| 子命令 | 说明 |
|--------|------|
| `migrate` | 执行数据库迁移，将所有未应用的迁移应用到数据库 |
| `clean` | 删除数据库中所有对象（谨慎使用） |
| `info` | 显示所有迁移的详细信息和状态 |
| `repair` | 修复迁移历史表，修复校验失败的校验和 |
| `validate` | 验证已应用的迁移和本地迁移是否一致 |

## 使用示例

### 1. 基本迁移

```bash
java -jar zora-flyway-cli.jar migrate \
  --url jdbc:postgresql://localhost:5432/mydb \
  --username postgres \
  --password mypassword
```

### 2. 指定驱动和多个占位符

```bash
java -jar zora-flyway-cli.jar migrate \
  -u jdbc:postgresql://localhost:5432/mydb \
  --user postgres \
  -p mypassword \
  -d org.postgresql.Driver \
  -l filesystem:./migrations \
  -P schema=public \
  -P tablePrefix=app_ \
  -P env=production
```

### 3. 对已有数据库创建基线

```bash
java -jar zora-flyway-cli.jar migrate \
  --url jdbc:mysql://localhost:3306/mydb \
  --username root \
  --password root \
  --baseline-version 1.0.0
```

### 4. 禁用占位符替换

```bash
java -jar zora-flyway-cli.jar migrate \
  --url jdbc:postgresql://localhost:5432/mydb \
  --username postgres \
  --password secret \
  --placeholder-replacement=false
```

### 5. 查看迁移信息

```bash
java -jar zora-flyway-cli.jar info \
  --url jdbc:postgresql://localhost:5432/mydb \
  --username postgres \
  --password secret
```

### 6. 使用配置文件

```bash
java -jar zora-flyway-cli.jar migrate \
  --config-file /path/to/flyway.properties
```

flyway.properties 示例：

```properties
flyway.url=jdbc:postgresql://localhost:5432/mydb
flyway.user=postgres
flyway.password=secret
flyway.locations=filesystem:./sql
flyway.placeholders.env=production
```

## 编译打包

### 编译

```bash
mvn clean package -pl zora-cli-tools/zora-flyway-cli
```

### 生成可执行 jar 包

项目已配置 maven-assembly-plugin，打包后会生成带有所有依赖的可执行 jar：

```
target/zora-flyway-cli-<version>-jar-with-dependencies.jar
```

## 常见驱动类名参考

| 数据库 | URL 格式 | 驱动类名 |
|--------|----------|----------|
| PostgreSQL | `jdbc:postgresql://host:port/database` | `org.postgresql.Driver` |
| MySQL | `jdbc:mysql://host:port/database` | `com.mysql.cj.jdbc.Driver` |
| Oracle | `jdbc:oracle:thin:@host:port:database` | `oracle.jdbc.OracleDriver` |
| SQL Server | `jdbc:sqlserver://host:port;databaseName=db` | `com.microsoft.sqlserver.jdbc.SQLServerDriver` |
| H2 | `jdbc:h2:mem:testdb` 或 `jdbc:h2:file:./data/db` | `org.h2.Driver` |
| SQLite | `jdbc:sqlite:path/to/database.db` | `org.sqlite.JDBC` |

## 依赖说明

- Flyway 版本：项目继承自父模块定义
- Picocli：用于命令行参数解析
- SLF4J：日志接口，使用时需要搭配具体日志实现（如 logback）

## 在 CI/CD 中使用

### GitHub Actions 示例

```yaml
- name: Run database migration
  run: |
    java -jar zora-flyway-cli.jar migrate \
      --url ${{ secrets.DB_URL }} \
      --username ${{ secrets.DB_USER }} \
      --password ${{ secrets.DB_PASSWORD }} \
      -P environment=${{ env.ENVIRONMENT }}
```

## 设计思路

为什么要做这个命令行工具：

1. **简洁性**：官方 Flyway 命令行工具相对较重，这个工具只保留核心功能，接口更简洁
2. **可定制性**：可以根据团队需求方便地进行扩展和定制
3. **易于集成**：可以轻松集成到自动化流程中
4. **学习成本低**：基于标准 Flyway，无需学习新的概念，Flyway 用户可以直接使用

## 注意事项

1. 请确保运行环境的 classpath 中包含对应的 JDBC 驱动依赖
2. `clean` 命令会删除数据库中所有对象，请谨慎在生产环境使用
3. 占位符替换默认启用，如不需要请显式禁用
4. 如果 JDBC 驱动无法自动识别，请通过 `--driver` 参数显式指定
