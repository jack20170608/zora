package top.ilovemyhome.zora.text;

public class CharUtils implements CharPool {

    public static boolean isAscii(char ch) {
        return ch < 128;
    }

    public static boolean isAsciiPrintable(char ch) {
        return ch >= 32 && ch < 127;
    }

    public static boolean isLetter(char ch) {
        return isLetterUpper(ch) || isLetterLower(ch);
    }

    public static boolean isLetterUpper(final char ch) {
        return ch >= 'A' && ch <= 'Z';
    }

    public static boolean isLetterLower(final char ch) {
        return ch >= 'a' && ch <= 'z';
    }

    public static boolean isNumber(char ch) {
        return ch >= '0' && ch <= '9';
    }

    public static boolean isEmoji(char c) {
        //noinspection ConstantConditions
        return !((c == 0x0) || //
            (c == 0x9) || //
            (c == 0xA) || //
            (c == 0xD) || //
            ((c >= 0x20) && (c <= 0xD7FF)) || //
            ((c >= 0xE000) && (c <= 0xFFFD)) || //
            ((c >= 0x100000) && (c <= 0x10FFFF)));
    }

    public static boolean isBlankChar(int c) {
        return Character.isWhitespace(c)
            || Character.isSpaceChar(c)
            || c == '\ufeff'
            || c == '\u202a'
            || c == '\u0000'
            // issue#I5UGSQ，Hangul Filler
            || c == '\u3164'
            // Braille Pattern Blank
            || c == '\u2800'
            // MONGOLIAN VOWEL SEPARATOR
            || c == '\u180e';
    }
}
