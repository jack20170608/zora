package top.ilovemyhome.zora.common.lang;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Objects;

public final class StringConvertUtils {

    public static final String EMPTY_STRING = "";

    private StringConvertUtils() {
    }

    public static String toStr(Object value, String defaultValue) {
        if (null == value) {
            return defaultValue;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return value.toString();
    }

    public static String toStr(Object value) {
        return toStr(value, null);
    }

    public static String toStr(Object[] array) {
        return toDelimitedString(array, ",");
    }

    public static String toDelimitedString(Object[] array, String delimiter) {
        if (array == null || array.length == 0) {
            return EMPTY_STRING;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(toStr(array[i]));
        }
        return sb.toString();
    }

    public static boolean isEmpty(String valueStr) {
        return null == valueStr || EMPTY_STRING.equals(valueStr);
    }

    public static Short toShort(String value, Short defaultValue) {
        if (isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Short.parseShort(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Short toShort(String value) {
        return toShort(value, null);
    }

    public static Integer toInt(String value, Integer defaultValue) {
        if (isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Integer toInt(String value) {
        return toInt(value, null);
    }

    public static Long toLong(String value, Long defaultValue) {
        if (isEmpty(value)) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value.trim()).longValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Long toLong(String value) {
        return toLong(value, null);
    }

    public static Boolean toBool(String value, Boolean defaultValue) {
        Boolean result = defaultValue;
        if (isEmpty(value)) {
            return defaultValue;
        }
        String trimedValueStr = value.trim().toLowerCase();
        switch (trimedValueStr) {
            case "true":
            case "yes":
            case "y" :
            case "1":
                result = true;
                break;
            case "false":
            case "no":
            case "n":
            case "0":
                result = false;
                break;
            default:
                break;
        }
        return result;
    }

    public static Boolean toBool(String value){
        return toBool(value, null);
    }

    public static <E extends Enum<E>> E toEnum(Class<E> clazz, String value, E defaultValue){
        if (isEmpty(value)){
            return defaultValue;
        }
        try {
            return Enum.valueOf(clazz, value);
        }catch (Exception e){
            return defaultValue;
        }
    }

    public static <E extends Enum<E>> E toEnum(Class<E> clazz, String value){
        return toEnum(clazz, value, null);
    }

    public static BigInteger toBigInteger(String value, BigInteger defaultValue){
        if (isEmpty(value)){
            return defaultValue;
        }
        try {
            return new BigInteger(value.trim());
        }catch (Exception e){
            return defaultValue;
        }
    }

    public static BigInteger toBigInteger(String value){
        return toBigInteger(value, null);
    }

    public static BigDecimal toBigDecimal(String value, BigDecimal defaultValue){
        if (isEmpty(value)){
            return defaultValue;
        }
        try {
            return new BigDecimal(value.trim());
        }catch (Exception e){
            return defaultValue;
        }
    }

    public static BigInteger toBigDecimal(String value){
        return toBigInteger(value, null);
    }

    public static String str(byte[] bytes, String charset){
        return str(bytes, isEmpty(charset) ? Charset.defaultCharset() : Charset.forName(charset));
    }

    public static String str(byte[] bytes, Charset charset){
        if (bytes == null){
            return null;
        }
        if (null == charset){
            return new String(bytes);
        }
        return new String(bytes, charset);
    }

    public static String[] toStrArray(String str) {
        return toStrArray(",", str);
    }

    public static String[] toStrArray(String split, String str) {
        if (Objects.isNull(str) || Objects.isNull(split)){
            return null;
        }
        return str.split(split);
    }


}
