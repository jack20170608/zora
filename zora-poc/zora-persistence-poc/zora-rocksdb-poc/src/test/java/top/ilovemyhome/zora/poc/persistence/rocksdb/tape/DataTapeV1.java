package top.ilovemyhome.zora.poc.persistence.rocksdb.tape;

import org.rocksdb.*;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * 带时间索引的数据磁带
 * 支持：追加、顺序读、按时间范围读、不可篡改
 */
public class DataTapeV1 implements AutoCloseable {
    private final RocksDB db;
    private static final byte[] LAST_OFFSET_KEY = longToBytes(-1);

    // 初始化
    public DataTapeV1(String dbPath) throws RocksDBException {
        RocksDB.loadLibrary();
        Options options = new Options();
        options.setCreateIfMissing(true);
        this.db = RocksDB.open(options, dbPath);
    }

    // ==========================================
    // 【核心】追加数据 + 自动写入时间索引
    // ==========================================
    public long append(byte[] data) throws RocksDBException {
        long offset = getNextOffset();
        long timestamp = System.currentTimeMillis(); // 自动取当前时间

        // 1. 写入主数据：offset → data
        db.put(longToBytes(offset), data);

        // 2. 写入时间索引：timestamp + offset → empty
        // 这样可以按时间排序 + 去重
        byte[] indexKey = buildTimeIndexKey(timestamp, offset);
        db.put(indexKey, new byte[0]);

        // 3. 更新偏移量
        db.put(LAST_OFFSET_KEY, longToBytes(offset + 1));
        return offset;
    }

    // ==========================================
    // 【新功能】按时间范围读取记录（ startTime ~ endTime ）
    // ==========================================
    public List<Map.Entry<Long, byte[]>> readByTimeRange(long startTime, long endTime) throws RocksDBException {
        List<Map.Entry<Long, byte[]>> result = new ArrayList<>();

        try (RocksIterator iter = db.newIterator()) {
            // 从 startTime 开始 seek
            iter.seek(buildTimeIndexKey(startTime, 0));

            while (iter.isValid()) {
                byte[] key = iter.key();

                // 只处理时间索引（长度=16：8字节时间 + 8字节偏移）
                if (key.length != 16) {
                    iter.next();
                    continue;
                }

                // 解析时间和偏移量
                long ts = bytesToLong(Arrays.copyOfRange(key, 0, 8));
                long offset = bytesToLong(Arrays.copyOfRange(key, 8, 16));

                // 超过结束时间就停止
                if (ts > endTime) break;

                // 读取真实数据
                byte[] data = db.get(longToBytes(offset));
                if (data != null) {
                    result.add(new AbstractMap.SimpleEntry<>(offset, data));
                }
                iter.next();
            }
        }
        return result;
    }

    // ==========================================
    // 原来的功能全部保留
    // ==========================================
    public byte[] read(long offset) throws RocksDBException {
        return db.get(longToBytes(offset));
    }

    public List<byte[]> readAll() throws RocksDBException {
        List<byte[]> result = new ArrayList<>();
        try (RocksIterator iter = db.newIterator()) {
            iter.seekToFirst();
            while (iter.isValid()) {
                long key = bytesToLong(iter.key());
                if (key == -1) { iter.next(); continue; }
                if (iter.key().length == 16) { iter.next(); continue; } // 跳过索引
                result.add(iter.value());
                iter.next();
            }
        }
        return result;
    }

    public long size() throws RocksDBException {
        byte[] val = db.get(LAST_OFFSET_KEY);
        return val == null ? 0 : bytesToLong(val);
    }

    // ==========================================
    // 工具方法
    // ==========================================
    private long getNextOffset() throws RocksDBException {
        byte[] val = db.get(LAST_OFFSET_KEY);
        return val == null ? 0 : bytesToLong(val);
    }

    // 构建时间索引key：timestamp(8) + offset(8) → 16字节
    private byte[] buildTimeIndexKey(long timestamp, long offset) {
        return ByteBuffer.allocate(16)
                .putLong(timestamp)
                .putLong(offset)
                .array();
    }

    private static byte[] longToBytes(long v) {
        return ByteBuffer.allocate(8).putLong(v).array();
    }

    private static long bytesToLong(byte[] b) {
        return ByteBuffer.wrap(b).getLong();
    }

    @Override
    public void close() {
        db.close();
    }
}
