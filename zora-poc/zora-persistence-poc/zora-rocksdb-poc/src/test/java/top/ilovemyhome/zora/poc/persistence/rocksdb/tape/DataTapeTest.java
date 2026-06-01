package top.ilovemyhome.zora.poc.persistence.rocksdb.tape;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class DataTapeTest {


    @Test
    void testDataTapeV1(){

        try (DataTapeV1 tape = new DataTapeV1("./my-tape-with-time-index")) {
            // 写入3条记录（会自动记录时间）
            tape.append("订单A：支付100元".getBytes());
            Thread.sleep(10);
            tape.append("订单B：支付200元".getBytes());
            Thread.sleep(10);
            tape.append("订单C：支付300元".getBytes());

            System.out.println("总记录数：" + tape.size());

            // ==========================================
            // 【核心】按时间范围查询
            // ==========================================
            long now = System.currentTimeMillis();
            long start = now - 1000; // 1秒前
            long end = now + 1000;   // 1秒后

            System.out.println("\n=== 按时间范围读取 ===");
            var list = tape.readByTimeRange(start, end);
            for (var entry : list) {
                long offset = entry.getKey();
                String data = new String(entry.getValue());
                System.out.println("偏移量=" + offset + "，数据=" + data);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
