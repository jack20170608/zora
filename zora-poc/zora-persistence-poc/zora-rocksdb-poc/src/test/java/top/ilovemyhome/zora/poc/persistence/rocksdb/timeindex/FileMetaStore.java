package top.ilovemyhome.zora.poc.persistence.rocksdb.timeindex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rocksdb.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 完整功能：
 * 1. 大文件流式存储（不OOM）
 * 2. 保存原始文件名、大小、类型、时间
 * 3. 时间索引 + 分页查询
 * 4. 乱序写入
 */
public class FileMetaStore implements AutoCloseable {
    private final RocksDB db;
    private static final int CHUNK_SIZE = 1024 * 256; // 256KB 块
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    static {
        RocksDB.loadLibrary();
    }

    public FileMetaStore(String dbPath) throws RocksDBException {
        Options options = new Options();
        options.setCreateIfMissing(true);
        this.db = RocksDB.open(options, dbPath);
    }

    // ==========================================
    // 【上传文件 + 自动保存元数据】
    // ==========================================
    public void upload(long timestamp, String filePath) throws Exception {
        Path path = Paths.get(filePath);
        String originalName = path.getFileName().toString();
        long size = Files.size(path);
        String contentType = Files.probeContentType(path);
        long fileId = new Random().nextLong();

        // 1. 写入元数据
        FileMeta meta = new FileMeta(timestamp, fileId, originalName, size, contentType);
        String json = JSON_MAPPER.writeValueAsString(meta);
        System.out.println(json);
        db.put(buildMetaKey(timestamp, fileId), json.getBytes());

        // 2. 流式写入文件内容（分块，不OOM）
        try (var is = Files.newInputStream(path)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int chunkIndex = 0;
            int len;
            while ((len = is.read(buffer)) != -1) {
                byte[] chunk = Arrays.copyOf(buffer, len);
                db.put(buildChunkKey(timestamp, fileId, chunkIndex++), chunk);
            }
        }
    }

    // ==========================================
    // 【下载文件】自动用原始文件名保存
    // ==========================================
    public void download(long timestamp, String outputDir) throws Exception {
        try (RocksIterator iter = db.newIterator()) {
            // 1. 找到元数据
            iter.seek(buildMetaKey(timestamp, 0));
            FileMeta meta = JSON_MAPPER.readValue(iter.value(), FileMeta.class);
            Path outPath = Paths.get(outputDir, meta.originalName);

            // 2. 流式下载文件
            try (var os = Files.newOutputStream(outPath)) {
                iter.seek(buildChunkKey(timestamp, meta.fileId, 0));
                while (iter.isValid()) {
                    KeyMeta km = parseChunkKey(iter.key());
                    if (km.timestamp() != timestamp || km.fileId() != meta.fileId) break;
                    os.write(iter.value());
                    iter.next();
                }
            }
        }
    }

    // ==========================================
    // 【分页查询】返回文件元数据列表
    // ==========================================
    public PageResult<FileMeta> queryPage(long startTime, long endTime, int pageNo, int pageSize, boolean asc) throws Exception{
        List<FileMeta> list = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        long total = 0;

        try (RocksIterator iter = db.newIterator()) {
            iter.seek(buildMetaKey(startTime, 0));
            while (iter.isValid()) {
                String json = new String(iter.value());
                System.out.println("Read from the rocksDb!");
                System.out.println(json);
                FileMeta meta = JSON_MAPPER.readValue(json, FileMeta.class);
                if (meta.timestamp > endTime) break;

                String key = meta.timestamp + "-" + meta.fileId;
                if (!unique.contains(key)) {
                    unique.add(key);
                    total++;
                    if (list.size() < pageSize && total > (long) (pageNo - 1) * pageSize) {
                        list.add(meta);
                    }
                }
                iter.next();
            }
        }

        PageResult<FileMeta> res = new PageResult<>();
        res.list = list;
        res.pageNo = pageNo;
        res.pageSize = pageSize;
        res.total = total;
        res.pages = (total + pageSize - 1) / pageSize;
        return res;
    }

    // ==========================================
    // 元数据结构
    // ==========================================
    public static class FileMeta {
        public long timestamp;
        public long fileId;
        public String originalName;
        public long size;
        public String contentType;

        public FileMeta() {}
        public FileMeta(long timestamp, long fileId, String originalName, long size, String contentType) {
            this.timestamp = timestamp;
            this.fileId = fileId;
            this.originalName = originalName;
            this.size = size;
            this.contentType = contentType;
        }
    }

    // ==========================================
    // Key 工具
    // ==========================================
    private byte[] buildMetaKey(long ts, long fileId) {
        return ByteBuffer.allocate(17)
                .put((byte) 1) // 标记=元数据
                .putLong(ts)
                .putLong(fileId)
                .array();
    }

    private byte[] buildChunkKey(long ts, long fileId, int idx) {
        return ByteBuffer.allocate(25)
                .put((byte) 2) // 标记=文件块
                .putLong(ts)
                .putLong(fileId)
                .putInt(idx)
                .array();
    }

    private KeyMeta parseChunkKey(byte[] key) {
        ByteBuffer buf = ByteBuffer.wrap(key);
        buf.get(); // 跳过标记
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
