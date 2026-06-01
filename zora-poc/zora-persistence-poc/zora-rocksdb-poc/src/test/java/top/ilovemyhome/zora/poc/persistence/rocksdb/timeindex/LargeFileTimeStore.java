package top.ilovemyhome.zora.poc.persistence.rocksdb.timeindex;

import org.rocksdb.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 支持 GB 级大文件
 * 流式分块存储 → 永不内存溢出
 * 时间索引 + 分页 + 乱序写入
 */
public class LargeFileTimeStore implements AutoCloseable {
    private final RocksDB db;
    private static final int CHUNK_SIZE = 1024 * 256; // 256KB 块大小（可调）

    static {
        RocksDB.loadLibrary();
    }

    public LargeFileTimeStore(String dbPath) throws RocksDBException {
        Options options = new Options();
        options.setCreateIfMissing(true);
        this.db = RocksDB.open(options, dbPath);
    }

    // ==========================================
    // 【流式写入大文件】不会OOM！
    // ==========================================
    public void putLargeFile(long timestamp, String filePath) throws Exception {
        Path path = Paths.get(filePath);
        byte[] buffer = new byte[CHUNK_SIZE];
        long fileId = new Random().nextLong(); // 唯一文件ID
        int chunkIndex = 0;

        try (var is = Files.newInputStream(path)) {
            int len;
            while ((len = is.read(buffer)) != -1) {
                byte[] chunk = Arrays.copyOf(buffer, len);
                byte[] key = buildChunkKey(timestamp, fileId, chunkIndex++);
                db.put(key, chunk);
            }
        }
        // 写入文件结束标记
        byte[] endKey = buildChunkKey(timestamp, fileId, -1);
        db.put(endKey, new byte[0]);
    }

    // ==========================================
    // 【流式读取并保存】不会OOM！
    // ==========================================
    public void getLargeFile(long timestamp, String outputPath) throws Exception {
        Path outPath = Paths.get(outputPath);
        Files.deleteIfExists(outPath);

        try (var os = Files.newOutputStream(outPath);
             RocksIterator iter = db.newIterator()) {

            iter.seek(buildChunkKey(timestamp, 0, 0));
            while (iter.isValid()) {
                byte[] key = iter.key();
                KeyMeta meta = parseChunkKey(key);

                if (meta.timestamp != timestamp || meta.chunkIndex == -1) {
                    break;
                }
                os.write(iter.value());
                iter.next();
            }
        }
    }

    // ==========================================
    // 分页查询（只查文件元数据，不读内容）
    // ==========================================
    public PageResult<Long> queryFilePage(
            long startTime, long endTime, int pageNo, int pageSize, boolean asc
    ) {
        List<Long> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        long total = 0;

        try (RocksIterator iter = db.newIterator()) {
            if (asc) iter.seek(buildChunkKey(startTime, 0, 0));
            else iter.seekToLast();

            int skip = (pageNo - 1) * pageSize;
            int count = 0;

            while (iter.isValid()) {
                KeyMeta meta = parseChunkKey(iter.key());
                if (meta.timestamp < startTime || meta.timestamp > endTime) break;

                if (meta.chunkIndex == -1 && !seen.contains(meta.timestamp)) {
                    seen.add(meta.timestamp);
                    total++;
                    if (count >= skip && result.size() < pageSize) {
                        result.add(meta.timestamp);
                    }
                    count++;
                }
                if (asc) iter.next();
                else iter.prev();
            }
        }

        PageResult<Long> pr = new PageResult<>();
        pr.list = result;
        pr.pageNo = pageNo;
        pr.pageSize = pageSize;
        pr.total = total;
        pr.pages = (total + pageSize - 1) / pageSize;
        return pr;
    }

    // ==========================================
    // 块 Key 构建/解析（时间戳 + 文件ID + 块序号）
    // ==========================================
    private byte[] buildChunkKey(long ts, long fileId, int chunkIdx) {
        return ByteBuffer.allocate(24)
                .putLong(ts)
                .putLong(fileId)
                .putInt(chunkIdx)
                .array();
    }

    private KeyMeta parseChunkKey(byte[] key) {
        ByteBuffer buf = ByteBuffer.wrap(key);
        return new KeyMeta(buf.getLong(), buf.getLong(), buf.getInt());
    }

    private record KeyMeta(long timestamp, long fileId, int chunkIndex) {}

    // ==========================================
    // 分页结果
    // ==========================================
    public static class PageResult<T> {
        public List<T> list;
        public int pageNo;
        public int pageSize;
        public long total;
        public long pages;
    }

    @Override
    public void close() {
        db.close();
    }
}
