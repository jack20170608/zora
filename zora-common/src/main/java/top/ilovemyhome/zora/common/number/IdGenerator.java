package top.ilovemyhome.zora.common.number;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通用 ID 生成器
 * 支持：雪花算法（分布式唯一ID）、UUID、单机递增ID
 */
public class IdGenerator {
    // ====================== 雪花算法核心参数 ======================
    /** 开始时间戳 (2025-01-01 00:00:00)，减少ID长度 */
    private static final long EPOCH = 1735689600000L;
    /** 机器ID位数 (5位，支持0-31) */
    private static final long WORKER_ID_BITS = 5L;
    /** 数据中心ID位数 (5位，支持0-31) */
    private static final long DATACENTER_ID_BITS = 5L;
    /** 序列号位数 (12位，每毫秒最多生成4096个ID) */
    private static final long SEQUENCE_BITS = 12L;

    /** 机器ID最大值 (31) */
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    /** 数据中心ID最大值 (31) */
    private static final long MAX_DATACENTER_ID = (1L << DATACENTER_ID_BITS) - 1;
    /** 序列号最大值 (4095) */
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;

    /** 机器ID左移位数 (12) */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    /** 数据中心ID左移位数 (17) */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    /** 时间戳左移位数 (22) */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    // 雪花算法运行时参数
    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    // ====================== 单机递增ID ======================
    private final AtomicLong incrementId = new AtomicLong(0);

    // ====================== 单例模式 ======================
    private static volatile IdGenerator INSTANCE;

    /**
     * 获取单例实例（默认机器ID=1，数据中心ID=1）
     */
    public static IdGenerator getInstance() {
        if (INSTANCE == null) {
            synchronized (IdGenerator.class) {
                if (INSTANCE == null) {
                    INSTANCE = new IdGenerator(1, 1);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 自定义机器ID/数据中心ID（分布式场景）
     * @param workerId 机器ID (0-31)
     * @param datacenterId 数据中心ID (0-31)
     */
    public static IdGenerator getInstance(long workerId, long datacenterId) {
        if (INSTANCE == null) {
            synchronized (IdGenerator.class) {
                if (INSTANCE == null) {
                    INSTANCE = new IdGenerator(workerId, datacenterId);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 构造器（校验机器ID/数据中心ID合法性）
     */
    private IdGenerator(long workerId, long datacenterId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(String.format("Worker ID must be between 0 and %d", MAX_WORKER_ID));
        }
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException(String.format("Datacenter ID must be between 0 and %d", MAX_DATACENTER_ID));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    // ====================== 雪花算法生成ID ======================
    /**
     * 生成雪花算法ID（线程安全）
     * @return 64位长整型唯一ID
     */
    public synchronized long nextSnowflakeId() {
        long timestamp = getCurrentTimestamp();

        // 时钟回拨检查：若当前时间 < 上次生成ID的时间，抛出异常（避免ID重复）
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(
                    String.format("Clock moved backwards. Refusing to generate ID for %d milliseconds", lastTimestamp - timestamp)
            );
        }

        // 同一毫秒内，序列号递增
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            // 序列号溢出（同一毫秒生成超过4096个ID）
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp); // 等待下一毫秒
            }
        } else {
            sequence = 0L; // 不同毫秒，序列号重置为0
        }

        lastTimestamp = timestamp;

        // 拼接ID：时间戳 + 数据中心ID + 机器ID + 序列号
        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 获取当前时间戳（毫秒）
     */
    private long getCurrentTimestamp() {
        return Instant.now().toEpochMilli();
    }

    /**
     * 等待下一毫秒（解决序列号溢出）
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }

    // ====================== UUID生成 ======================
    /**
     * 生成标准UUID（36位，含横线）
     * @return 如：550e8400-e29b-41d4-a716-446655440000
     */
    public String nextUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成无横线UUID（32位）
     * @return 如：550e8400e29b41d4a716446655440000
     */
    public String nextUUIDWithoutHyphen() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成安全随机UUID（基于加密强随机数）
     * @return 无横线安全UUID
     */
    public String nextSecureUUID() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return UUID.nameUUIDFromBytes(bytes).toString().replace("-", "");
    }

    // ====================== 单机递增ID ======================
    /**
     * 生成单机递增ID（线程安全）
     * @return 从0开始递增的长整型ID
     */
    public long nextIncrementId() {
        return incrementId.getAndIncrement();
    }

    /**
     * 重置单机递增ID（慎用，仅测试/重置场景）
     * @param start 起始值
     */
    public void resetIncrementId(long start) {
        incrementId.set(start);
    }


}
