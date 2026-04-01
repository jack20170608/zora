package top.ilovemyhome.zora.common.number;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalUtils {

    public static BigDecimal div(final BigDecimal a, final BigDecimal b, int scale) {
        if (a == null || b == null) {
            return null;
        }
        return a.divide(b, scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal mul(final BigDecimal a, final BigDecimal b, int scale) {
        if (a == null || b == null) {
            return null;
        }
        return a.multiply(b).setScale(scale, RoundingMode.HALF_UP);
    }

}
