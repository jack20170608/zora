# 20 备份与恢复

## 目标

掌握 RocksDB 的 Checkpoint 和 BackupEngine 机制，实现数据库的备份、恢复和一致性快照创建。

---

## 步骤 1：Checkpoint（轻量级快照）

### 1.1 创建 Checkpoint

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BackupRestoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateCheckpoint() throws RocksDBException {
        RocksDB.loadLibrary();

        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.resolve("db").toString());

        db.put("key1".getBytes(), "value1".getBytes());
        db.put("key2".getBytes(), "value2".getBytes());

        // Create checkpoint
        Checkpoint checkpoint = Checkpoint.create(db);
        String checkpointDir = tempDir.resolve("checkpoint").toString();
        checkpoint.createCheckpoint(checkpointDir);
        checkpoint.close();

        // Continue writing to original DB
        db.put("key3".getBytes(), "value3".getBytes());

        // Checkpoint should only contain key1, key2
        try (Options checkpointOpts = new Options();
             RocksDB checkpointDb = RocksDB.open(checkpointOpts, checkpointDir)) {
            assertThat(new String(checkpointDb.get("key1".getBytes()))).isEqualTo("value1");
            assertThat(new String(checkpointDb.get("key2".getBytes()))).isEqualTo("value2");
            assertThat(checkpointDb.get("key3".getBytes())).isNull();
        }

        // Original DB contains all keys
        assertThat(new String(db.get("key3".getBytes()))).isEqualTo("value3");

        db.close();
        options.close();
    }
}
```

### 1.2 Checkpoint 原理

Checkpoint 通过创建 SST 文件的硬链接（hard link）实现，因此：
- 创建速度极快（只复制元数据，不复制数据）
- 磁盘空间与原始 DB 共享，直到原始 SST 被 compaction 删除
- 依赖底层文件系统支持硬链接

---

## 步骤 2：BackupEngine（增量备份）

### 2.1 创建备份

```java
@Test
void shouldCreateAndRestoreBackup() throws RocksDBException {
    RocksDB.loadLibrary();

    String dbPath = tempDir.resolve("db").toString();
    String backupPath = tempDir.resolve("backups").toString();

    // Phase 1: Create database and backup
    {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, dbPath);

        db.put("data".getBytes(), "original".getBytes());

        BackupEngineOptions backupOptions = new BackupEngineOptions(backupPath);
        try (BackupEngine backupEngine = BackupEngine.open(options.getEnv(), backupOptions)) {
            backupEngine.createNewBackup(db, true);  // flush=true
        }
        backupOptions.close();

        db.close();
        options.close();
    }

    // Phase 2: Corrupt original database (simulate failure)
    {
        Options options = new Options();
        RocksDB db = RocksDB.open(options, dbPath);
        db.put("data".getBytes(), "corrupted".getBytes());
        db.close();
        options.close();
    }

    // Phase 3: Restore from backup
    {
        Options options = new Options();
        BackupEngineOptions backupOptions = new BackupEngineOptions(backupPath);

        try (BackupEngine backupEngine = BackupEngine.open(options.getEnv(), backupOptions)) {
            RestoreOptions restoreOptions = new RestoreOptions(true);  // keep log files
            backupEngine.restoreDbFromLatestBackup(dbPath, dbPath, restoreOptions);
            restoreOptions.close();
        }
        backupOptions.close();
        options.close();
    }

    // Phase 4: Verify restored data
    {
        Options options = new Options();
        RocksDB db = RocksDB.open(options, dbPath);
        assertThat(new String(db.get("data".getBytes()))).isEqualTo("original");
        db.close();
        options.close();
    }
}
```

### 2.2 备份管理

```java
@Test
void shouldManageBackupVersions() throws RocksDBException {
    RocksDB.loadLibrary();

    String dbPath = tempDir.resolve("db").toString();
    String backupPath = tempDir.resolve("backups").toString();

    Options options = new Options().setCreateIfMissing(true);
    RocksDB db = RocksDB.open(options, dbPath);

    BackupEngineOptions backupOptions = new BackupEngineOptions(backupPath);
    BackupEngine backupEngine = BackupEngine.open(options.getEnv(), backupOptions);

    // Create multiple backups
    db.put("version".getBytes(), "v1".getBytes());
    backupEngine.createNewBackup(db, true);

    db.put("version".getBytes(), "v2".getBytes());
    backupEngine.createNewBackup(db, true);

    db.put("version".getBytes(), "v3".getBytes());
    backupEngine.createNewBackup(db, true);

    // List backups
    List<BackupInfo> backupInfos = backupEngine.getBackupInfo();
    assertThat(backupInfos).hasSize(3);

    for (BackupInfo info : backupInfos) {
        System.out.println("Backup ID: " + info.backupId()
            + ", Timestamp: " + info.timestamp()
            + ", Size: " + info.size());
    }

    // Delete old backups (keep last 2)
    backupEngine.purgeOldBackups(2);

    List<BackupInfo> remaining = backupEngine.getBackupInfo();
    assertThat(remaining).hasSize(2);

    backupEngine.close();
    backupOptions.close();
    db.close();
    options.close();
}
```

---

## 步骤 3：BackupEngine 配置

### 3.1 增量备份与压缩

```java
@Test
void shouldConfigureBackupEngine() throws RocksDBException {
    RocksDB.loadLibrary();

    String dbPath = tempDir.resolve("db").toString();
    String backupPath = tempDir.resolve("backups").toString();

    Options options = new Options().setCreateIfMissing(true);
    RocksDB db = RocksDB.open(options, dbPath);

    BackupEngineOptions backupOptions = new BackupEngineOptions(backupPath)
        .setBackupLogFiles(true)           // Include WAL files
        .setMaxValidBackupsToOpen(5)       // Max backups to track
        .setSync(true);                     // fsync after each file

    try (BackupEngine backupEngine = BackupEngine.open(options.getEnv(), backupOptions)) {
        backupEngine.createNewBackup(db, true);
    }

    backupOptions.close();
    db.close();
    options.close();
}
```

---

## 验证检查清单

- [ ] Checkpoint 创建后，原始 DB 继续写入不影响 Checkpoint 内容
- [ ] Checkpoint 目录可独立打开为只读 DB
- [ ] BackupEngine 可创建多个版本的增量备份
- [ ] 从最新备份恢复后数据与备份时一致
- [ ] `purgeOldBackups(n)` 正确保留最近的 n 个备份
- [ ] 备份包含 WAL 文件时可恢复到崩溃前的最新状态

---

## Checkpoint vs BackupEngine 对比

| 特性 | Checkpoint | BackupEngine |
|---|---|---|
| 速度 | 极快（硬链接） | 较快（增量复制） |
| 磁盘空间 | 与原始 DB 共享 | 独立存储 |
| 版本管理 | 无 | 支持多版本 |
| 跨机器恢复 | 需复制整个目录 | 备份目录可直接复制 |
| 压缩 | 无 | 可选 |
| 适用场景 | 本地快照、快速回滚 | 长期备份、灾难恢复 |

---

## 常见问题

**Q: Checkpoint 依赖硬链接，Windows 支持吗？**
A: Windows NTFS 支持硬链接，但某些环境（如 WSL1、某些容器）可能不支持，此时 Checkpoint 会回退为文件复制。

**Q: 备份时 DB 还能写入吗？**
A: 可以。BackupEngine 使用 Snapshot 机制保证备份一致性。

**Q: 备份损坏如何检测？**
A: BackupEngine 支持 `verifyBackup(backupId)` 方法验证备份完整性。
