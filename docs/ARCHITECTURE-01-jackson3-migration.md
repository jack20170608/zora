# ARCHITECTURE-01 全仓 Jackson 3 迁移

## 范围

- 统一使用 `tools.jackson:jackson-bom:3.2.3` 管理依赖，不再引入 Jackson 2 BOM。
- `zora-json` 使用 Jackson 3 的 `JsonMapper.builder()`、`SimpleModule` 和内置 Java Time 编解码器；保留现有日期格式与未知字段校验。
- `zora-rocksdb` 的图存储继续使用 Smile 二进制格式，升级至 `tools.jackson.dataformat:jackson-dataformat-smile`；RocksDB POC 同步升级 JSON 编解码器。
- HTTP 测试服务升级至 `tools.jackson.jakarta.rs:jackson-jakarta-rs-json-provider`，并固定订单字段输出顺序，以保持原有测试接口响应。
- JDBI 测试使用 Jackson 3 `TypeReference`；`zora-config` 移除未使用的 Jackson 依赖。

## 依赖边界

Jackson 3.2.3 本身仍依赖 `com.fasterxml.jackson.core:jackson-annotations:2.22`，并继续使用 `com.fasterxml.jackson.annotation` 注解包；由 Jackson 3 BOM 管理版本，不能强制排除。

**尚未实现依赖树彻底清零 Jackson 2**：`zora-rdb`、`zora-flyway-cli` 依赖的 `flyway-core:11.8.2` 会传递引入 Jackson 2 databind/core；其 `JsonUtils` 字节码直接调用 `com.fasterxml.jackson.databind.json.JsonMapper` 和 2.x 的 JavaTimeModule。即使 Flyway 12.0.0 仍依赖 Jackson 2，因此不能安全地排除再用 Jackson 3 替代。`zora-muserver` 的 `jjwt:0.13.0` 也通过 `jjwt-jackson` 引入 Jackson 2。若要求运行时完全没有 Jackson 2，需先选择不依赖其 2.x API 的 Flyway/JJWT 版本或替代实现，再单独验证数据库迁移与令牌功能。

## 验证

在仓库根目录运行 `mvn test -q`：全仓测试通过。`zora-rocksdb` 的 `ValueCodecTest` 覆盖 Smile 字节头及顶点/边的往返编解码。可运行 `mvn dependency:tree '-Dincludes=com.fasterxml.jackson*:*'` 复查上述上游传递依赖。

Smile 数据与旧 JSON 格式数据库不互通；本次迁移不负责已有数据库的数据转换。
