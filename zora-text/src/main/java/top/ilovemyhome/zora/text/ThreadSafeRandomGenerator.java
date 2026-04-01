package top.ilovemyhome.zora.text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ThreadSafeRandomGenerator {
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom secureRandom = new SecureRandom();

    // 使用 SecureRandom
    public static String generateSecureRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    // 使用 ThreadLocalRandom
    public static String generateFastRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(ThreadLocalRandom.current().nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    public static <T> T getRandomTypeByPercentage(Map<T, Integer> typePercentageMap) {
        if (typePercentageMap == null || typePercentageMap.isEmpty()) {
            throw new IllegalArgumentException("Type percentage map cannot be null or empty");
        }
        int totalPercentage = typePercentageMap.values().stream().mapToInt(Integer::intValue).sum();
        if (totalPercentage != 100) {
            throw new IllegalArgumentException("Total percentage must be 100, but got " + totalPercentage);
        }
        int randomValue = ThreadLocalRandom.current().nextInt(100);
        int cumulativePercentage = 0;
        for (Map.Entry<T, Integer> entry : typePercentageMap.entrySet()) {
            cumulativePercentage += entry.getValue();
            if (randomValue < cumulativePercentage) {
                return entry.getKey();
            }
        }
        return typePercentageMap.keySet().iterator().next();
    }

    public static <T> Map<T, Integer> createPercentageMap(T... typesAndPercentages) {
        if (typesAndPercentages == null || typesAndPercentages.length % 2 != 0) {
            throw new IllegalArgumentException("Types and percentages must be provided in pairs");
        }

        Map<T, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < typesAndPercentages.length; i += 2) {
            T type = typesAndPercentages[i];
            if (i + 1 >= typesAndPercentages.length || !(typesAndPercentages[i + 1] instanceof Integer)) {
                throw new IllegalArgumentException("Invalid percentage value for type: " + type);
            }
            Integer percentage = (Integer) typesAndPercentages[i + 1];
            map.put(type, percentage);
        }

        return map;
    }

    public static BigDecimal generateRandomBigDecimal(BigDecimal min, BigDecimal max, int scale) {
        if (scale < 0) {
            throw new IllegalArgumentException("Scale cannot be negative");
        }

        if (min == null || max == null) {
            throw new IllegalArgumentException("Min and max values cannot be null");
        }
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("Min value cannot be greater than max value");
        }
        BigDecimal range = max.subtract(min);
        double randomDouble = ThreadLocalRandom.current().nextDouble();

        return min.add(range.multiply(BigDecimal.valueOf(randomDouble)))
            .setScale(scale, RoundingMode.HALF_UP);
    }

    public static int generateRandomInt(int min, int max, boolean includeMin, boolean includeMax) {
        int adjustedMin = includeMin ? min : min + 1;
        int adjustedMax = includeMax ? max : max - 1;

        if (adjustedMin > adjustedMax) {
            throw new IllegalArgumentException("Adjusted min value cannot be greater than adjusted max value");
        }
        return generateRandomInt(adjustedMin, adjustedMax);
    }

    public static int generateRandomInt(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("Min value cannot be greater than max value");
        }
        return ThreadLocalRandom.current().nextInt(max - min + 1) + min;
    }
}
