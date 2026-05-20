package top.ilovemyhome.zora.poc.persistence.rocksdb;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TransactionDB pessimistic and optimistic transaction tests.
 */
class TransactionTest {

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldCommitAndRollbackTransaction() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        TransactionDBOptions txnDbOptions = new TransactionDBOptions();

        try (TransactionDB txnDb = TransactionDB.open(options, txnDbOptions, tempDir.toString())) {

            Transaction txn = txnDb.beginTransaction(new WriteOptions());

            txn.put("key1".getBytes(), "value1".getBytes());
            txn.put("key2".getBytes(), "value2".getBytes());

            assertThat(txnDb.get("key1".getBytes())).isNull();

            txn.commit();
            txn.close();

            assertThat(new String(txnDb.get("key1".getBytes()))).isEqualTo("value1");

            Transaction txn2 = txnDb.beginTransaction(new WriteOptions());
            txn2.put("key3".getBytes(), "value3".getBytes());
            txn2.rollback();
            txn2.close();

            assertThat(txnDb.get("key3".getBytes())).isNull();
        }

        txnDbOptions.close();
        options.close();
    }

    @Test
    void shouldUseOptimisticTransaction() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);

        try (OptimisticTransactionDB optTxnDb = OptimisticTransactionDB.open(
                options, tempDir.toString())) {

            Transaction txnA = optTxnDb.beginTransaction(new WriteOptions());
            txnA.put("key1".getBytes(), "valueA".getBytes());

            Transaction txnB = optTxnDb.beginTransaction(new WriteOptions());
            txnB.put("key1".getBytes(), "valueB".getBytes());

            txnA.commit();
            txnA.close();

            // txnB conflict at commit time
            try {
                txnB.commit();
            } catch (RocksDBException e) {
                assertThat(e.getMessage()).containsIgnoringCase("busy");
                txnB.rollback();
            }
            txnB.close();
        }

        options.close();
    }

    @Test
    void shouldReadSnapshotInTransaction() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        TransactionDBOptions txnDbOptions = new TransactionDBOptions();

        try (TransactionDB txnDb = TransactionDB.open(options, txnDbOptions, tempDir.toString())) {

            txnDb.put("balance".getBytes(), "100".getBytes());

            Transaction txn = txnDb.beginTransaction(new WriteOptions());

            String before = new String(txn.get(new ReadOptions(), "balance".getBytes()));
            assertThat(before).isEqualTo("100");

            txn.put("balance".getBytes(), "80".getBytes());

            String withinTxn = new String(txn.get(new ReadOptions(), "balance".getBytes()));
            assertThat(withinTxn).isEqualTo("80");

            String outsideTxn = new String(txnDb.get("balance".getBytes()));
            assertThat(outsideTxn).isEqualTo("100");

            txn.commit();
            txn.close();

            String afterCommit = new String(txnDb.get("balance".getBytes()));
            assertThat(afterCommit).isEqualTo("80");
        }

        txnDbOptions.close();
        options.close();
    }
}
