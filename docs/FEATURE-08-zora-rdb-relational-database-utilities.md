# FEATURE 08 - zora-rdb 关系型数据库通用工具模块

## 概述

zora-rdb 是 zora 框架中专门提供关系型数据库通用功能的模块，封装了常用的数据库连接池管理和 Flyway 数据库版本迁移功能，简化项目中数据库相关配置。

## 功能特性

1. **连接池管理**：基于 HikariCP 封装，提供简单的配置和构建方式
2. **Flyway 迁移支持**：简化 Flyway 的配置和迁移执行流程
3. **配置集成**：无缝集成 zora-config 支持外部化配置加载
4. **合理的默认值**：提供合理的默认连接池参数，减少配置工作量

## 模块结构

```
zora-rdb
├── src/main/java/top/ilovemyhome/zora/rdb
│   ├── config
│   │   ├── RdbConfig.java          # 数据库配置类
│   │   └── RdbConfigLoader.java    # 从 zora-config 加载配置
│   ├── pool
│   │   └── DataSourcePoolBuilder.java  # HikariCP 连接池构建器
│   └── flyway
│       └── FlywayMigrationRunner.java  # Flyway 迁移执行器
├── src/main/resources/metadata
│   └── metadata.json                # 模块元数据
└── README.md                         # 模块说明文档
```

## 使用示例

### 基本用法 - 手动配置

```java
// 1. 创建配置
RdbConfig config = RdbConfig.builder()
    .jdbcUrl("jdbc:h2:mem:testdb")
    .username("sa")
    .password("")
    .driverClassName("org.h2.Driver")
    .maximumPoolSize(10)
    .build();

// 2. 创建连接池
HikariDataSource dataSource = DataSourcePoolBuilder.create(config).build();

// 3. 执行 Flyway 迁移
FlywayMigrationRunner runner = FlywayMigrationRunner.builder(dataSource)
    .locations("classpath:db/migration")
    .baselineOnMigrate(true)
    .build();
runner.migrate();

// 4. 使用完毕后关闭连接池
DataSourcePoolBuilder.closeDataSource(dataSource);
```

### 使用 zora-config 从配置文件加载

```properties
# application.properties
db.main.jdbcUrl=jdbc:postgresql://localhost:5432/mydb
db.main.username=postgres
db.main.password=secret
db.main.maximumPoolSize=20
db.main.minimumIdle=5
```

```java
// 加载配置
RdbConfig config = RdbConfigLoader.load("db.main");
HikariDataSource dataSource = DataSourcePoolBuilder.create(config).build();
```

### Flyway 高级配置

```java
FlywayMigrationRunner runner = FlywayMigrationRunner.builder(dataSource)
    .locations("classpath:db/migration", "filesystem:./custom-migrations")
    .baselineOnMigrate(true)
    .baselineVersion("1")
    .baselineDescription("Initial baseline")
    .defaultSchema("public")
    .validateOnMigrate(true)
    .build();

// 检查是否有待执行的迁移
if (runner.hasPendingMigrations()) {
    System.out.println("There are " + runner.getPendingMigrationCount() + " pending migrations");
    runner.migrate();
}
```

## 依赖说明

zora-rdb 只提供通用功能，不包含具体数据库驱动，使用者需要在项目中自行引入目标数据库驱动：

- MySQL: `mysql:mysql-connector-java`
- PostgreSQL: `org.postgresql:postgresql`
- H2: `com.h2database:h2`

## 配置选项

### RdbConfig 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| jdbcUrl | - | 数据库 JDBC URL |
| username | - | 数据库用户名 |
| password | - | 数据库密码 |
| driverClassName | - | JDBC 驱动类名 |
| maximumPoolSize | 10 | 连接池最大连接数 |
| minimumIdle | 2 | 最小空闲连接数 |
| idleTimeout | 600000 (10分钟) | 空闲连接超时时间 |
| connectionTimeout | 30000 (30秒) | 连接获取超时时间 |
| maxLifetime | 1800000 (30分钟) | 连接最大生命周期 |
| poolName | zora-rdb-pool | 连接池名称 |
| autoCommit | true | 是否自动提交 |
| readOnly | false | 是否只读模式 |

## 版本信息

- 初始版本：1.0.1-SNAPSHOT
- 创建日期：2026-03-31

## 维护者

ilovemyhome
