package top.ilovemyhome.zora.poc.persistence.rocksdb.timeindex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rocksdb.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 支持：
 * 1. 大文件流式存储（不OOM）
 * 2. 时间范围过滤
 * 3. 文件名模糊过滤
 * 4. 文件大小范围过滤
 * 5. 文件类型过滤
 * 6. 分页
 */
public class FilterableFileStore implements AutoCloseable {
    private final RocksDB db;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int CHUNK_SIZE = 256 * 1024;

    static {
        RocksDB.loadLibrary();
    }

    public FilterableFileStore(String dbPath) throws RocksDBException {
        Options options = new Options();
        options.setCreateIfMissing(true);
        this.db = RocksDB.open(options, dbPath);
    }

    // ==========================================
    // 上传文件（自动保存元数据）
    // ==========================================
    public void upload(long timestamp, String filePath) throws Exception {
        Path path = Paths.get(filePath);
        String originalName = path.getFileName().toString();
        long size = Files.size(path);
        String contentType = Files.probeContentType(path);
        long fileId = new Random().nextLong();

        // 保存元数据
        FileMeta meta = new FileMeta(timestamp, fileId, originalName, size, contentType);
        db.put(buildMetaKey(timestamp, fileId), MAPPER.writeValueAsBytes(meta));

        // 分块保存文件内容
        try (var in = Files.newInputStream(path)) {
            byte[] buf = new byte[CHUNK_SIZE];
            int chunkIdx = 0;
            int len;
            while ((len = in.read(buf)) != -1) {
                db.put(buildChunkKey(timestamp, fileId, chunkIdx++), Arrays.copyOf(buf, len));
            }
        }
    }

    // ==========================================
    // 【核心】带过滤条件的分页查询
    // ==========================================
    public PageResult<FileMeta> query(QueryFilter filter, int pageNo, int pageSize) throws Exception {
        List<FileMeta> allMatched = new ArrayList<>();

        try (RocksIterator iter = db.newIterator()) {
            iter.seek(buildMetaKey(filter.startTime, 0));

            while (iter.isValid()) {
                FileMeta meta = MAPPER.readValue(iter.value(), FileMeta.class);

                // 超出时间范围直接退出
                if (meta.timestamp > filter.endTime) break;

                // ======================
                // 条件过滤
                // ======================
                boolean match = true;
                if (filter.nameKeyword != null && !meta.originalName.contains(filter.nameKeyword)) match = false;
                if (meta.size < filter.minSize) match = false;
                if (meta.size > filter.maxSize) match = false;
                if (filter.contentType != null && !meta.contentType.startsWith(filter.contentType)) match = false;

                if (match) allMatched.add(meta);
                iter.next();
            }
        }

        // 分页处理
        int from = Math.min((pageNo - 1) * pageSize, allMatched.size());
        int to = Math.min(pageNo * pageSize, allMatched.size());
        List<FileMeta> pageList = allMatched.subList(from, to);

        PageResult<FileMeta> res = new PageResult<>();
        res.list = pageList;
        res.pageNo = pageNo;
        res.pageSize = pageSize;
        res.total = allMatched.size();
        res.pages = (res.total + pageSize - 1) / pageSize;
        return res;
    }

    // ==========================================
    // 下载文件
    // ==========================================
    public void download(long timestamp, long fileId, String outputDir) throws Exception {
        FileMeta meta = MAPPER.readValue(db.get(buildMetaKey(timestamp, fileId)), FileMeta.class);
        Path outPath = Paths.get(outputDir, meta.originalName);
        Files.createDirectories(outPath.getParent());

        try (var out = Files.newOutputStream(outPath);
             RocksIterator iter = db.newIterator()) {

            iter.seek(buildChunkKey(timestamp, fileId, 0));
            while (iter.isValid()) {
                KeyMeta km = parseChunkKey(iter.key());
                if (km.timestamp() != timestamp || km.fileId() != fileId) break;
                out.write(iter.value());
                iter.next();
            }
        }
    }

    // ==================== KEY 结构 ====================
    private byte[] buildMetaKey(long ts, long fileId) {
        return ByteBuffer.allocate(17).put((byte) 1).putLong(ts).putLong(fileId).array();
    }

    private byte[] buildChunkKey(long ts, long fileId, int idx) {
        return ByteBuffer.allocate(25).put((byte) 2).putLong(ts).putLong(fileId).putInt(idx).array();
    }

    private KeyMeta parseChunkKey(byte[] key) {
        ByteBuffer b = ByteBuffer.wrap(key);
        b.get(); return new KeyMeta(b.getLong(), b.getLong(), b.getInt());
    }

    private record KeyMeta(long timestamp, long fileId, int chunkIndex) {}

    // ==================== 过滤条件类 ====================
    public static class QueryFilter {
        public long startTime = 0;
        public long endTime = Long.MAX_VALUE;
        public String nameKeyword;     // 文件名包含xx
        public long minSize = 0;        // 最小大小
        public long maxSize = Long.MAX_VALUE; // 最大大小
        public String contentType;     // 类型：image/ 、video/ 、application/pdf
    }

    // ==================== 元数据 ====================
    public static class FileMeta {
        public long timestamp;
        public long fileId;
        public String originalName;
        public long size;
        public String contentType;

        public FileMeta() {}
        public FileMeta(long t, long fid, String name, long s, String ct) {
            timestamp = t; fileId = fid; originalName = name; size = s; contentType = ct;
        }
    }

    // ==================== 分页结果 ====================
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
