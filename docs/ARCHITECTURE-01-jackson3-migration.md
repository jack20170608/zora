# ARCHITECTURE-01 全仓 Jackson 3 迁移

## 范围

- 统一使用 `tools.jackson:jackson-bom:3.2.3` 管理依赖，不再引入 Jackson 2 BOM。
- `zora-json` 使用 Jackson 3 的 `JsonMapper.builder()`、`SimpleModule` 和内置 Java Time 编解码器；保留现有日期格式与未知字段校验。
- `zora-rocksdb` 的图存储继续使用 Smile 二进制格式，升级至 `tools.jackson.dataformat:jackson-dataformat-smile`；RocksDB POC 同步升级 JSON 编解码器。
- HTTP 测试服务升级至 `tools.jackson.jakarta.rs:jackson-jakarta-rs-json-provider`，并固定订单字段输出顺序，以保持原有测试接口响应。
- JDBI 测试使用 Jackson 3 `TypeReference`；`zora-config` 移除未使用的 Jackson 依赖。
- `zora-muserver` 将 JJWT 聚合包拆为 API、运行时实现和 Gson 序列化器，不再传递引入 Jackson 2。
- `zora-rdb` 与迁移 CLI 用 Liquibase 5 执行现有 `V<版本>__<描述>.sql` 脚本，不再依赖 Flyway。CLI 入口名称尚未重命名，底层已经不是 Flyway。

## 依赖边界

Jackson 3.2.3 本身仍依赖 `com.fasterxml.jackson.core:jackson-annotations:2.22`，并继续使用 `com.fasterxml.jackson.annotation` 注解包；由 Jackson 3 BOM 管理版本，不能强制排除。

全仓依赖树不再包含 Jackson 2 的 core、databind、Smile 或 REST provider，只有 Jackson 3 上游必需的 `jackson-annotations:2.22`。

**破坏性变更**：Liquibase 使用 `databasechangelog` / `databasechangeloglock`，不读取已有的 `flyway_schema_history`。已有数据库上线前须单独制定数据迁移计划；已执行的脚本不能直接再次运行。`FlywayMigrationRunner.getFlyway()` 现在返回 `SqlMigrationEngine`，不再返回 Flyway 对象；`migrate()` 返回执行的脚本数，而不是 Flyway 的结果对象。Liquibase 脚本由来源路径和版本共同标识，移动迁移目录需要同步迁移历史标识。

旧 Builder 的 `baselineOnMigrate`、`baselineVersion`、`baselineDescription`、`cleanOnValidationError`、`validateOnMigrate`、`schemas` 已移除；需要基线时显式调用 `baseline()`。CLI 不再接受 Flyway 属性文件 `--config-file` 或 `--baseline-version`。`clean` 仍是破坏性操作，CLI 默认禁用，必须显式使用 `--clean-disabled=false`。

## 验证

在仓库根目录运行 `mvn test -q`：全仓测试通过（包含 PostgreSQL、H2、JWT、HTTP 与 Smile）。`zora-rocksdb` 的 `ValueCodecTest` 覆盖 Smile 字节头及顶点/边的往返编解码。可运行 `mvn dependency:tree '-Dincludes=com.fasterxml.jackson*:*'` 复查依赖树。

Smile 数据与旧 JSON 格式数据库不互通；本次迁移不负责已有数据库的数据转换。
