# zora-rdb - Relational Database Utilities

zora-rdb 是 zora 框架中提供关系型数据库通用功能的模块，提供数据库连接池管理和基于 Liquibase 的 SQL 脚本迁移。

## 功能特性

- **连接池管理**: 基于 HikariCP 封装的高性能数据库连接池
- **SQL 迁移**: 使用 Liquibase 执行 `V<版本>__<描述>.sql` 脚本
- **配置支持**: 集成 zora-config 实现灵活的外部化配置

## 使用方法

### 依赖引入

```xml
<dependency>
    <groupId>top.ilovemyhome</groupId>
    <artifactId>zora-rdb</artifactId>
    <version>${zora.version}</version>
</dependency>
```

### 连接池使用

```java
// 创建连接池配置
RdbConfig config = RdbConfig.builder()
    .jdbcUrl("jdbc:h2:mem:testdb")
    .username("sa")
    .password("")
    .driverClassName("org.h2.Driver")
    .maximumPoolSize(10)
    .build();

// 创建连接池
HikariDataSource dataSource = DataSourcePoolBuilder.create(config).build();
```

### SQL 迁移使用

```java
// 历史类名保留，底层迁移引擎已改为 Liquibase
FlywayMigrationRunner runner = FlywayMigrationRunner.builder(dataSource)
    .locations("classpath:db/migration")
    .build();

// 执行迁移
int applied = runner.migrate();
```

Liquibase 使用新的 `databasechangelog` 历史表，不自动导入旧 `flyway_schema_history`。已有数据库升级前请先制定历史迁移方案，避免重新执行已应用的 SQL。

## 模块结构

```
zora-rdb
├── src
│   ├── main
│   │   ├── java
│   │   │   └── top/ilovemyhome/zora/rdb
│   │   │       ├── config   # 配置类
│   │   │       ├── pool     # 连接池相关
│   │   │       └── flyway   # 历史包名，内部是 Liquibase 迁移引擎
│   │   └── resources
│   │       └── metadata
│   │           └── metadata.json
│   └── test
│       ├── java
│       └── resources
└── pom.xml
```

## 开发指南

- 本模块只提供通用的抽象和封装，不依赖具体的数据库驱动实现
- 需要使用者在项目中引入具体的数据库驱动（如 MySQL、PostgreSQL 等）
- 所有配置项都可以通过外部配置文件进行自定义调整
