package top.ilovemyhome.zora.poc.persistence.rocksdb;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkpoint and BackupEngine backup/restore tests.
 */
class BackupRestoreTest {

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateCheckpoint() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.resolve("db").toString());

        db.put("key1".getBytes(), "value1".getBytes());
        db.put("key2".getBytes(), "value2".getBytes());

        Checkpoint checkpoint = Checkpoint.create(db);
        String checkpointDir = tempDir.resolve("checkpoint").toString();
        checkpoint.createCheckpoint(checkpointDir);
        checkpoint.close();

        db.put("key3".getBytes(), "value3".getBytes());

        try (Options checkpointOpts = new Options();
             RocksDB checkpointDb = RocksDB.open(checkpointOpts, checkpointDir)) {
            assertThat(new String(checkpointDb.get("key1".getBytes()))).isEqualTo("value1");
            assertThat(new String(checkpointDb.get("key2".getBytes()))).isEqualTo("value2");
            assertThat(checkpointDb.get("key3".getBytes())).isNull();
        }

        assertThat(new String(db.get("key3".getBytes()))).isEqualTo("value3");

        db.close();
        options.close();
    }

    private String toRocksDbPath(Path path) throws java.io.IOException {
        Files.createDirectories(path);
        return path.toString().replace('\\', '/');
    }

    @Test
    void shouldCreateAndRestoreBackup() throws Exception {
        String dbPath = toRocksDbPath(tempDir.resolve("db"));
        String backupPath = toRocksDbPath(tempDir.resolve("backups"));

        {
            Options options = new Options().setCreateIfMissing(true);
            RocksDB db = RocksDB.open(options, dbPath);

            db.put("data".getBytes(), "original".getBytes());

            BackupEngineOptions backupOptions = new BackupEngineOptions(backupPath);
            try (BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), backupOptions)) {
                backupEngine.createNewBackup(db, true);
            }
            backupOptions.close();

            db.close();
            options.close();
        }

        {
            Options options = new Options();
            RocksDB db = RocksDB.open(options, dbPath);
            db.put("data".getBytes(), "corrupted".getBytes());
            db.close();
            options.close();
        }

        {
            Options options = new Options();
            BackupEngineOptions backupOptions = new BackupEngineOptions(backupPath);

            try (BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), backupOptions)) {
                RestoreOptions restoreOptions = new RestoreOptions(false);
                backupEngine.restoreDbFromLatestBackup(dbPath, dbPath, restoreOptions);
                restoreOptions.close();
            }
            backupOptions.close();
            options.close();
        }

        {
            Options options = new Options();
            RocksDB db = RocksDB.open(options, dbPath);
            assertThat(new String(db.get("data".getBytes()))).isEqualTo("original");
            db.close();
            options.close();
        }
    }

    @Test
    void shouldManageBackupVersions() throws Exception {
        String dbPath = toRocksDbPath(tempDir.resolve("db"));
        String backupPath = toRocksDbPath(tempDir.resolve("backups"));

        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, dbPath);

        BackupEngineOptions backupOptions = new BackupEngineOptions(backupPath);
        BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), backupOptions);

        db.put("version".getBytes(), "v1".getBytes());
        backupEngine.createNewBackup(db, true);

        db.put("version".getBytes(), "v2".getBytes());
        backupEngine.createNewBackup(db, true);

        db.put("version".getBytes(), "v3".getBytes());
        backupEngine.createNewBackup(db, true);

        List<BackupInfo> backupInfos = backupEngine.getBackupInfo();
        assertThat(backupInfos).hasSize(3);

        for (BackupInfo info : backupInfos) {
            System.out.println("Backup ID: " + info.backupId()
                + ", Timestamp: " + info.timestamp()
                + ", Size: " + info.size());
        }

        backupEngine.purgeOldBackups(2);

        List<BackupInfo> remaining = backupEngine.getBackupInfo();
        assertThat(remaining).hasSize(2);

        backupEngine.close();
        backupOptions.close();
        db.close();
        options.close();
    }

    @Test
    void shouldConfigureBackupEngine() throws Exception {
        String dbPath = toRocksDbPath(tempDir.resolve("db"));
        String backupPath = toRocksDbPath(tempDir.resolve("backups"));

        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, dbPath);

        BackupEngineOptions backupOptions = new BackupEngineOptions(backupPath)
            .setBackupLogFiles(true)
            .setSync(true);

        try (BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), backupOptions)) {
            backupEngine.createNewBackup(db, true);
        }

        backupOptions.close();
        db.close();
        options.close();
    }
}
