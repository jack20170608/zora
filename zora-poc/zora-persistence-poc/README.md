# zora-persistence-poc

数据持久层框架与模式预研聚合模块。

## 目的

本模块用于聚合各类 Java 数据持久层框架的 POC 子模块，所有代码均在 `test` scope 下运行，不会被打包到生产产物中。

## 子模块

- `zora-rocksdb-poc` — RocksDB Java API 功能与特性预研

## 运行测试

```bash
mvn test -pl zora-persistence-poc
```

或从项目根目录：

```bash
mvn test -pl zora-poc/zora-persistence-poc
```
