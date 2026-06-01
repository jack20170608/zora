package top.ilovemyhome.zora.poc.persistence.rocksdb.timeindex;

import org.junit.jupiter.api.Test;
import org.rocksdb.RocksDBException;

import java.util.Map;

public class TimeIndexDemo {
    @Test
    void testTimeIndexStoreV1() throws Exception {
        try (TimeIndexStoreV1 store = new TimeIndexStoreV1("D:\\jack\\temp\\my-time-index-db")) {
            // ==========================================
            // 【乱序写入】：故意打乱时间顺序插入！
            // ==========================================
            store.put(20230101L, "2023年数据".getBytes());
            store.put(20250101L, "2025年数据".getBytes());
            store.put(20240101L, "2024年数据".getBytes());
            store.put(20200101L, "2020年数据".getBytes());
            store.put(20260101L, "2026年数据".getBytes());

            System.out.println("乱序写入完成！\n");

            // ==========================================
            // 【按时间范围查询】：自动按时间排序输出
            // ==========================================
            System.out.println("=== 查询 2021 ~ 2025 年的数据 ===");
            var list = store.queryByTimeRange(20210000L, 20259999L);

            for (Map.Entry<Long, byte[]> entry : list) {
                long ts = entry.getKey();
                String data = new String(entry.getValue());
                System.out.println("时间：" + ts + " → " + data);
            }
        }
    }

    @Test
    void testTimeIndexStoreV2() throws RocksDBException {
        try (TimeIndexStoreV2 store = new TimeIndexStoreV2("d:\\jack\\temp\\time-index-db")) {

            // 乱序写入测试数据
            store.put(20200101L, "2020数据".getBytes());
            store.put(20230101L, "2023数据".getBytes());
            store.put(20250101L, "2025数据".getBytes());
            store.put(20210101L, "2021数据".getBytes());
            store.put(20220101L, "2022数据".getBytes());
            store.put(20240101L, "2024数据".getBytes());
            store.put(20190101L, "2019数据".getBytes());

            System.out.println("=== 分页查询：时间 2020~2025，第 1 页，每页 3 条，正序 ===");

            // 分页查询
            TimeIndexStoreV2.PageResult<Map.Entry<Long, byte[]>> page =
                store.queryPage(20200000L, 20259999L, 1, 3, true);

            // 输出
            System.out.println("总条数：" + page.total);
            System.out.println("总页数：" + page.pages);
            System.out.println("当前页：" + page.pageNo);
            for (Map.Entry<Long, byte[]> entry : page.list) {
                System.out.println(entry.getKey() + " → " + new String(entry.getValue()));
            }
        }
    }
}
