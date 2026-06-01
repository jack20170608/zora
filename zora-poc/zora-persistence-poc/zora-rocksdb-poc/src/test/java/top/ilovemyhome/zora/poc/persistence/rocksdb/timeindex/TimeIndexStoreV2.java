package top.ilovemyhome.zora.poc.persistence.rocksdb.timeindex;

import org.rocksdb.*;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * 支持：
 * 1. 任意时间戳乱序写入
 * 2. 按时间范围查询
 * 3. 标准分页（pageNo, pageSize）
 * 4. 正序 / 倒序
 */
public class TimeIndexStoreV2 implements AutoCloseable {
    private final RocksDB db;

    public TimeIndexStoreV2(String dbPath) throws RocksDBException {
        RocksDB.loadLibrary();
        Options options = new Options();
        options.setCreateIfMissing(true);
        this.db = RocksDB.open(options, dbPath);
    }

    // ==========================================
    // 写入：任意时间戳（乱序写入）
    // ==========================================
    public void put(long timestamp, byte[] data) throws RocksDBException {
        byte[] key = buildTimeKey(timestamp);
        db.put(key, data);
    }

    // ==========================================
    // 【分页查询】时间范围 + 页码 + 每页条数
    // ==========================================
    public PageResult<Map.Entry<Long, byte[]>> queryPage(
            long startTime,
            long endTime,
            int pageNo,
            int pageSize,
            boolean asc  // true=正序，false=倒序
    ) throws RocksDBException {
        List<Map.Entry<Long, byte[]>> list = new ArrayList<>();
        long total = 0;

        try (RocksIterator iter = db.newIterator()) {
            // 1. 先 seek 到起始位置
            if (asc) {
                iter.seek(buildTimeKey(startTime));
            } else {
                iter.seekToLast();
            }

            // 2. 跳过前面的页
            int skip = (pageNo - 1) * pageSize;
            int count = 0;

            while (iter.isValid()) {
                long ts = extractTimestampFromKey(iter.key());

                // 时间范围判断
                if (asc && ts > endTime) break;
                if (!asc && ts < startTime) break;

                total++;
                if (count >= skip && list.size() < pageSize) {
                    list.add(new AbstractMap.SimpleEntry<>(ts, iter.value()));
                }

                count++;
                if (asc) iter.next();
                else iter.prev();
            }
        }

        // 封装分页结果
        PageResult<Map.Entry<Long, byte[]>> result = new PageResult<>();
        result.list = list;
        result.pageNo = pageNo;
        result.pageSize = pageSize;
        result.total = total;
        result.pages = (total + pageSize - 1) / pageSize;
        return result;
    }

    // ==========================================
    // 工具方法
    // ==========================================
    private byte[] buildTimeKey(long timestamp) {
        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.putLong(timestamp);
        buf.putLong(new Random().nextLong()); // 唯一后缀，避免覆盖
        return buf.array();
    }

    private long extractTimestampFromKey(byte[] keyBytes) {
        return ByteBuffer.wrap(keyBytes).getLong();
    }

    @Override
    public void close() {
        db.close();
    }

    // ==========================================
    // 分页结果对象
    // ==========================================
    public static class PageResult<T> {
        public List<T> list;        // 当前页数据
        public int pageNo;          // 页码
        public int pageSize;        // 每页条数
        public long total;          // 总条数
        public long pages;          // 总页数
    }
}
