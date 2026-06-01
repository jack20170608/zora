package top.ilovemyhome.zora.poc.persistence.rocksdb.timeindex;

import org.junit.jupiter.api.Test;

public class LargeFileTimeStoreDemo {

    @Test
    void testLargeFile() throws Exception{
        try (LargeFileTimeStore store = new LargeFileTimeStore("D:\\jack\\temp\\large-file-db")) {

            // ========== 写入超大文件（GB级也没问题） ==========
            store.putLargeFile(20250101L, "D:\\download\\Rocky-10.0-x86_64-minimal.iso");
            System.out.println("大文件写入完成！");

            // ========== 分页查询 ==========
            var page = store.queryFilePage(20250000L, 20259999L, 1, 10, true);
            System.out.println("总文件数：" + page.total);
            System.out.println("文件时间戳列表：" + page.list);

            // ========== 读取并保存到本地（流式，不占内存） ==========
            store.getLargeFile(20250101L, "D:\\Rocky-10.0-x86_64-minimal-copy.iso");
            System.out.println("文件已导出！");
        }
    }
}
