package top.ilovemyhome.zora.poc.persistence.rocksdb.timeindex;

import org.rocksdb.*;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * 支持【乱序写入】+【精确时间戳索引】的存储引擎
 * 可以随时插入任意时间戳的数据，查询时自动按时间排序
 */
public class TimeIndexStoreV1 implements AutoCloseable {
    private final RocksDB db;

    // 初始化
    public TimeIndexStoreV1(String dbPath) throws RocksDBException {
        RocksDB.loadLibrary();
        Options options = new Options();
        options.setCreateIfMissing(true);
        this.db = RocksDB.open(options, dbPath);
    }

    // ==========================================
    // 【核心】写入：指定任意时间戳 + 任意数据（乱序写入也没问题）
    // ==========================================
    public void put(long timestamp, byte[] data) throws RocksDBException {
        byte[] key = buildTimeKey(timestamp);
        db.put(key, data);
    }

    // ==========================================
    // 【核心】按时间范围查询：自动按时间排序（无视插入顺序）
    // ==========================================
    public List<Map.Entry<Long, byte[]>> queryByTimeRange(long startTime, long endTime) throws RocksDBException {
        List<Map.Entry<Long, byte[]>> result = new ArrayList<>();

        try (RocksIterator iter = db.newIterator()) {
            // 从起始时间开始 seek
            iter.seek(buildTimeKey(startTime));

            while (iter.isValid()) {
                byte[] keyBytes = iter.key();
                long ts = extractTimestampFromKey(keyBytes);

                // 超过结束时间则停止
                if (ts > endTime) break;

                // 加入结果
                result.add(new AbstractMap.SimpleEntry<>(ts, iter.value()));
                iter.next();
            }
        }
        return result;
    }

    // ==========================================
    // 工具：构建时间键（时间戳 + 随机数，保证唯一不覆盖）
    // ==========================================
    private byte[] buildTimeKey(long timestamp) {
        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.putLong(timestamp);         // 前8字节：时间戳（排序用）
        buf.putLong(new Random().nextLong()); // 后8字节：唯一值（防止同时间戳覆盖）
        return buf.array();
    }

    // 从key中解析出时间戳
    private long extractTimestampFromKey(byte[] keyBytes) {
        return ByteBuffer.wrap(keyBytes).getLong();
    }

    // ==========================================
    // 关闭资源
    // ==========================================
    @Override
    public void close() {
        db.close();
    }
}
