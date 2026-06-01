package top.ilovemyhome.zora.poc.persistence.rocksdb.timeindex;

public class FileMetaDemo {
    public static void main(String[] args) throws Exception {
        try (FileMetaStore store = new FileMetaStore("d:\\jack\\temp\\file-meta-db")) {

            // 上传文件（自动保存原始名称）
            store.upload(20250101L, "C:\\Users\\27528\\Pictures\\Screenshots\\S1.png");
            store.upload(20250102L, "D:\\download\\markdown-cheat-sheet.md");

            // 分页查询（返回文件信息）
            var page = store.queryPage(20250000L, 20259999L, 1, 10, true);
            System.out.println("总文件：" + page.total);
            for (var meta : page.list) {
                System.out.println("时间：" + meta.timestamp
                        + " 文件名：" + meta.originalName
                        + " 大小：" + meta.size + " byte");
            }

            // 下载（自动用原始文件名保存）
            store.download(20250101L, "./download");
        }
    }
}
