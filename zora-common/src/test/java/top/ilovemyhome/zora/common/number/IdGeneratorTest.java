package top.ilovemyhome.zora.common.number;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class IdGeneratorTest {
    @Test
    public void test() {
        // 1. 雪花算法ID
        IdGenerator generator = IdGenerator.getInstance(1, 1);
        long snowflakeId = generator.nextSnowflakeId();
        System.out.println("雪花算法ID: " + snowflakeId);

        // 2. UUID（带横线/无横线）
        String uuid = generator.nextUUID();
        String uuidWithoutHyphen = generator.nextUUIDWithoutHyphen();
        System.out.println("标准UUID: " + uuid);
        System.out.println("无横线UUID: " + uuidWithoutHyphen);

        // 3. 单机递增ID
        long incrementId1 = generator.nextIncrementId();
        long incrementId2 = generator.nextIncrementId();
        System.out.println("递增ID1: " + incrementId1); // 0
        System.out.println("递增ID2: " + incrementId2); // 1

        assertThat(incrementId2 > incrementId1).isTrue();

        // 4. 多线程测试雪花算法（验证线程安全）
        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                long id = generator.nextSnowflakeId();
                System.out.println(Thread.currentThread().getName() + " - 雪花ID: " + id);
            }).start();
        }
    }
}
