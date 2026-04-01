package top.ilovemyhome.zora.common.text;

import java.util.StringJoiner;

/**
 * 华丽分隔符生成工具类
 * 支持生成多种风格的装饰性分隔符，用于文档注释、控制台输出、日志美化等场景
 */
public class FancySeparatorUtils {

    // ==================== 纯字符简约风格（无乱码，全场景兼容） ====================
    /**
     * 生成直线分隔符（如 —、=、~）
     * @param ch 分隔符基础字符
     * @param length 分隔符长度
     * @return 生成的直线分隔符
     */
    public static String generateLineSeparator(char ch, int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("分隔符长度必须大于0");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * 生成对称标题分隔符（左右带装饰，中间放标题）
     * @param decorator 装饰字符
     * @param title 中间标题
     * @param totalLength 分隔符总长度
     * @return 对称标题分隔符
     */
    public static String generateSymmetricTitleSeparator(char decorator, String title, int totalLength) {
        if (totalLength <= title.length() + 2) {
            throw new IllegalArgumentException("总长度必须大于标题长度+2（左右至少各留1位装饰）");
        }
        // 计算左右两侧装饰字符的长度（平分剩余空间）
        int decorLength = (totalLength - title.length() - 2) / 2;
        String leftDecor = generateLineSeparator(decorator, decorLength);
        String rightDecor = generateLineSeparator(decorator, decorLength);
        // 处理奇数长度的补位
        if ((totalLength - title.length() - 2) % 2 != 0) {
            rightDecor += decorator;
        }
        return String.format("%s %s %s", leftDecor, title, rightDecor);
    }

    // ==================== 预设常用简约风格（直接调用，无需传基础字符） ====================
    /**
     * 预设：横线分隔符（——————————）
     */
    public static String presetHorizontalLine(int length) {
        return generateLineSeparator('—', length);
    }

    /**
     * 预设：等号分隔符（══════════）
     */
    public static String presetEqualSign(int length) {
        return generateLineSeparator('═', length);
    }

    /**
     * 预设：点号分隔符（••••••••••）
     */
    public static String presetDot(int length) {
        return generateLineSeparator('•', length);
    }

    /**
     * 预设：波浪线分隔符（~~~~~~~~~~）
     */
    public static String presetWaveLine(int length) {
        return generateLineSeparator('~', length);
    }

    // ==================== 边框风格分隔符（生成带区域感的包围边框） ====================
    /**
     * 生成简单矩形边框（包围指定内容）
     * @param content 要包围的内容（单行）
     * @param useDoubleBorder 是否使用双线边框（╔═╗  vs ┌─┐）
     * @return 带边框的内容
     */
    public static String generateRectangleBorder(String content, boolean useDoubleBorder) {
        // 定义边框字符
        char topLeft, topRight, bottomLeft, bottomRight, horizontal, vertical;
        if (useDoubleBorder) {
            topLeft = '╔';
            topRight = '╗';
            bottomLeft = '╚';
            bottomRight = '╝';
            horizontal = '═';
            vertical = '║';
        } else {
            topLeft = '┌';
            topRight = '┐';
            bottomLeft = '└';
            bottomRight = '┘';
            horizontal = '─';
            vertical = '│';
        }

        // 生成上下边框
        String borderLine = topLeft + generateLineSeparator(horizontal, content.length() + 2) + topRight;
        String contentLine = vertical + " " + content + " " + vertical;
        String bottomLine = bottomLeft + generateLineSeparator(horizontal, content.length() + 2) + bottomRight;

        // 拼接结果（使用换行符分隔行）
        StringJoiner sj = new StringJoiner(System.lineSeparator());
        sj.add(borderLine);
        sj.add(contentLine);
        sj.add(bottomLine);
        return sj.toString();
    }

    // ==================== 特殊符号/Emoji风格（视觉华丽，注意UTF-8编码） ====================
    /**
     * 生成Emoji装饰分隔符（适合轻量化/年轻化输出）
     * @param emoji Emoji字符（如 "✨"、"🌟"、"🎉"）
     * @param length Emoji重复次数
     * @return Emoji组合分隔符
     */
    public static String generateEmojiSeparator(String emoji, int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Emoji重复次数必须大于0");
        }
        if (emoji == null || emoji.isBlank()) {
            throw new IllegalArgumentException("Emoji字符不能为空");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(emoji);
        }
        return sb.toString();
    }

    /**
     * 预设：常用Emoji分隔符（星光款）
     */
    public static String presetStarEmojiSeparator(int length) {
        return generateEmojiSeparator("✨", length);
    }

    /**
     * 预设：常用Emoji分隔符（礼花款）
     */
    public static String presetFireworkEmojiSeparator(int length) {
        return generateEmojiSeparator("🎉", length);
    }


}
