# 16 事务支持（TransactionDB）

## 目标

了解 RocksDB 的乐观事务与悲观事务模型，掌握 `TransactionDB` 的开启、提交、回滚操作。

---

## 步骤 1：TransactionDB 基础

### 1.1 打开 TransactionDB

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldOpenTransactionDb() throws RocksDBException {
        RocksDB.loadLibrary();

        Options options = new Options().setCreateIfMissing(true);
        TransactionDBOptions txnDbOptions = new TransactionDBOptions();

        try (TransactionDB txnDb = TransactionDB.open(options, txnDbOptions, tempDir.toString())) {
            assertThat(txnDb).isNotNull();
        }

        txnDbOptions.close();
        options.close();
    }
}
```

---

## 步骤 2：悲观事务（Pessimistic Transaction）

### 2.1 基本提交与回滚

```java
@Test
void shouldCommitAndRollbackTransaction() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    TransactionDBOptions txnDbOptions = new TransactionDBOptions();

    try (TransactionDB txnDb = TransactionDB.open(options, txnDbOptions, tempDir.toString())) {

        // Begin transaction
        Transaction txn = txnDb.beginTransaction(new WriteOptions());

        txn.put("key1".getBytes(), "value1".getBytes());
        txn.put("key2".getBytes(), "value2".getBytes());

        // Before commit: data not visible to other reads
        assertThat(txnDb.get("key1".getBytes())).isNull();

        // Commit
        txn.commit();
        txn.close();

        // After commit: data visible
        assertThat(new String(txnDb.get("key1".getBytes()))).isEqualTo("value1");

        // Rollback scenario
        Transaction txn2 = txnDb.beginTransaction(new WriteOptions());
        txn2.put("key3".getBytes(), "value3".getBytes());
        txn2.rollback();
        txn2.close();

        assertThat(txnDb.get("key3".getBytes())).isNull();
    }

    txnDbOptions.close();
    options.close();
}
```

### 2.2 冲突检测（悲观锁）

```java
@Test
void shouldDetectWriteConflict() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    TransactionDBOptions txnDbOptions = new TransactionDBOptions();

    try (TransactionDB txnDb = TransactionDB.open(options, txnDbOptions, tempDir.toString())) {

        // Transaction A locks key1
        Transaction txnA = txnDb.beginTransaction(new WriteOptions());
        txnA.put("key1".getBytes(), "valueA".getBytes());

        // Transaction B tries to write same key -> blocked or throws
        Transaction txnB = txnDb.beginTransaction(new WriteOptions());

        try {
            txnB.put("key1".getBytes(), "valueB".getBytes());
            // With default pessimistic locking, this may block or throw
            // depending on TransactionOptions settings
        } catch (RocksDBException e) {
            // Expected: lock conflict
            assertThat(e.getMessage()).containsIgnoringCase("lock");
        }

        txnA.rollback();
        txnA.close();
        txnB.close();
    }

    txnDbOptions.close();
    options.close();
}
```

---

## 步骤 3：乐观事务（Optimistic Transaction）

### 3.1 乐观冲突检测

```java
@Test
void shouldUseOptimisticTransaction() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    OptimisticTransactionDBOptions optimisticOptions = new OptimisticTransactionDBOptions();

    try (OptimisticTransactionDB optTxnDb = OptimisticTransactionDB.open(
            options, optimisticOptions, tempDir.toString())) {

        // Transaction A writes key1 (no lock taken)
        Transaction txnA = optTxnDb.beginTransaction(new WriteOptions());
        txnA.put("key1".getBytes(), "valueA".getBytes());

        // Transaction B also writes key1 (no lock taken)
        Transaction txnB = optTxnDb.beginTransaction(new WriteOptions());
        txnB.put("key1".getBytes(), "valueB".getBytes());

        // Commit A succeeds
        txnA.commit();
        txnA.close();

        // Commit B fails: conflict detected at commit time
        try {
            txnB.commit();
            // Should not reach here
        } catch (RocksDBException e) {
            assertThat(e.getMessage()).containsIgnoringCase("conflict");
            txnB.rollback();
        }
        txnB.close();
    }

    optimisticOptions.close();
    options.close();
}
```

---

## 步骤 4：事务与 Snapshot

### 4.1 在事务中使用 Snapshot

```java
@Test
void shouldReadSnapshotInTransaction() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    TransactionDBOptions txnDbOptions = new TransactionDBOptions();

    try (TransactionDB txnDb = TransactionDB.open(options, txnDbOptions, tempDir.toString())) {

        txnDb.put("balance".getBytes(), "100".getBytes());

        Transaction txn = txnDb.beginTransaction(new WriteOptions());

        // Read before modification
        String before = new String(txn.get(new ReadOptions(), "balance".getBytes()));
        assertThat(before).isEqualTo("100");

        // Modify within transaction
        txn.put("balance".getBytes(), "80".getBytes());

        // Read within transaction sees modified value
        String withinTxn = new String(txn.get(new ReadOptions(), "balance".getBytes()));
        assertThat(withinTxn).isEqualTo("80");

        // Read outside transaction still sees old value
        String outsideTxn = new String(txnDb.get("balance".getBytes()));
        assertThat(outsideTxn).isEqualTo("100");

        txn.commit();
        txn.close();

        // After commit, outside sees new value
        String afterCommit = new String(txnDb.get("balance".getBytes()));
        assertThat(afterCommit).isEqualTo("80");
    }

    txnDbOptions.close();
    options.close();
}
```

---

## 验证检查清单

- [ ] 悲观事务 `beginTransaction` + `commit` 后数据持久化
- [ ] 悲观事务 `rollback` 后数据未持久化
- [ ] 乐观事务无锁写入，冲突在 `commit()` 时检测
- [ ] 事务内写操作在提交前对外部不可见
- [ ] 事务内读操作可以看到本事务的未提交修改

---

## 常见问题

**Q: 悲观事务 vs 乐观事务怎么选？**
A: 冲突频繁时选悲观（避免重试开销），冲突稀少时选乐观（减少锁开销）。

**Q: 事务支持跨 Column Family 吗？**
A: 支持。`Transaction` 提供了带 `ColumnFamilyHandle` 参数的重载方法。

**Q: 事务有隔离级别设置吗？**
A: RocksDB 事务默认提供 Snapshot Isolation（SI），可通过 `TransactionOptions` 调整。
