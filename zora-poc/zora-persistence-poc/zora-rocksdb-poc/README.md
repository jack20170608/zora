# zora-rocksdb-poc

RocksDB 功能与特性预研子模块。

## 目的

本模块通过测试用例形式，对 RocksDB 的 Java API（rocksdbjni）进行功能和特性预研，所有代码均在 `test` scope 下运行。

## 预研范围

- 基本 CRUD（Put / Get / Delete）
- WriteBatch 批量写入
- Column Families
- Iterator 范围扫描
- Snapshot 快照读
- 配置调优（BlockCache、WriteBuffer 等）
- 备份与恢复

## 运行测试

```bash
mvn test -pl zora-rocksdb-poc
```

或从项目根目录：

```bash
mvn test -pl zora-poc/zora-persistence-poc/zora-rocksdb-poc
```
