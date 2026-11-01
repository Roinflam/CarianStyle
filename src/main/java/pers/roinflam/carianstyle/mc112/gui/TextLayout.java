package pers.roinflam.carianstyle.mc112.gui;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 文字排版：量宽度、按宽度截断、自动换行
 * <p>
 * 和字体渲染器分开写，是为了不进游戏也能测（传一个假的字符宽度进来就行）。
 * 换行规则照 1.20.1 的 StringSplitter：超宽时退回到这一行最后一个空格处断开（空格本身丢掉），
 * 没有空格就在超宽的那个字符前断开；换行符强制断行；每行至少放下一个有宽度的字符；
 * 空文字得到零行，末尾的换行符会多出一个空行。
 */
public final class TextLayout {

    /**
     * 字符宽度
     */
    public interface CharWidth {
        /**
         * @param c    字符（§ 也是普通字符）
         * @param bold 是否粗体
         * @return 宽度（像素）
         */
        int width(char c, boolean bold);
    }

    private TextLayout() {
    }

    /**
     * 文字段的总宽度
     *
     * @param runs   文字段
     * @param widths 字符宽度
     * @return 宽度
     */
    public static int width(@Nonnull List<TextRun> runs, @Nonnull CharWidth widths) {
        int total = 0;
        for (TextRun run : runs) {
            total += width(run.text, run.bold(), widths);
        }
        return total;
    }

    /**
     * 一段文字的宽度（不解析格式码）
     *
     * @param text   文字
     * @param bold   是否粗体
     * @param widths 字符宽度
     * @return 宽度
     */
    public static int width(@Nonnull String text, boolean bold, @Nonnull CharWidth widths) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += widths.width(text.charAt(i), bold);
        }
        return total;
    }

    /**
     * 从头取不超过 maxWidth 的最长前缀（不解析格式码）
     *
     * @param text     文字
     * @param maxWidth 最大宽度
     * @param widths   字符宽度
     * @return 前缀长度（char 数）
     */
    public static int headIndex(@Nonnull String text, int maxWidth, @Nonnull CharWidth widths) {
        int left = maxWidth;
        int length = text.length();
        int i = 0;
        while (i < length) {
            int step = Character.isHighSurrogate(text.charAt(i)) && i + 1 < length && Character.isLowSurrogate(text.charAt(i + 1)) ? 2 : 1;
            int w = 0;
            for (int k = 0; k < step; k++) {
                w += widths.width(text.charAt(i + k), false);
            }
            left -= w;
            if (left < 0) {
                break;
            }
            i += step;
        }
        return i;
    }

    /**
     * 从尾取不超过 maxWidth 的最长后缀（不解析格式码）
     *
     * @param text     文字
     * @param maxWidth 最大宽度
     * @param widths   字符宽度
     * @return 后缀起点（char 下标）
     */
    public static int tailIndex(@Nonnull String text, int maxWidth, @Nonnull CharWidth widths) {
        int left = maxWidth;
        int i = text.length();
        while (i > 0) {
            int step = Character.isLowSurrogate(text.charAt(i - 1)) && i - 2 >= 0 && Character.isHighSurrogate(text.charAt(i - 2)) ? 2 : 1;
            int w = 0;
            for (int k = 1; k <= step; k++) {
                w += widths.width(text.charAt(i - k), false);
            }
            left -= w;
            if (left < 0) {
                break;
            }
            i -= step;
        }
        return i;
    }

    /**
     * 从头取不超过 maxWidth 的文字段（按样式量宽度）
     *
     * @param runs     文字段
     * @param maxWidth 最大宽度
     * @param widths   字符宽度
     * @return 截出来的文字段
     */
    @Nonnull
    public static List<TextRun> head(@Nonnull List<TextRun> runs, int maxWidth, @Nonnull CharWidth widths) {
        List<TextRun> out = new ArrayList<>();
        int left = maxWidth;
        for (TextRun run : runs) {
            String text = run.text;
            int i = 0;
            while (i < text.length()) {
                left -= widths.width(text.charAt(i), run.bold());
                if (left < 0) {
                    break;
                }
                i++;
            }
            if (i > 0) {
                out.add(i == text.length() ? run : run.withText(text.substring(0, i)));
            }
            if (i < text.length()) {
                break;
            }
        }
        return out;
    }

    /**
     * 自动换行
     *
     * @param runs     文字段
     * @param maxWidth 每行最大宽度
     * @param widths   字符宽度
     * @return 每行的文字段
     */
    @Nonnull
    public static List<List<TextRun>> split(@Nonnull List<TextRun> runs, int maxWidth, @Nonnull CharWidth widths) {
        // 先摊平成字符数组，记下每个字符属于哪一段
        int total = 0;
        for (TextRun run : runs) {
            total += run.text.length();
        }
        char[] chars = new char[total];
        int[] owner = new int[total];
        int at = 0;
        for (int r = 0; r < runs.size(); r++) {
            String text = runs.get(r).text;
            for (int i = 0; i < text.length(); i++) {
                chars[at] = text.charAt(i);
                owner[at] = r;
                at++;
            }
        }

        List<List<TextRun>> lines = new ArrayList<>();
        int pos = 0;
        boolean endedWithNewline = false;
        while (pos < total) {
            int width = 0;
            boolean hadNonZero = false;
            int lastSpace = -1;
            int breakAt = -1;
            for (int k = pos; k < total; k++) {
                char c = chars[k];
                if (c == '\n') {
                    breakAt = k;
                    break;
                }
                if (c == ' ') {
                    lastSpace = k;
                }
                int w = widths.width(c, runs.get(owner[k]).bold());
                width += w;
                if (hadNonZero && width > maxWidth) {
                    breakAt = lastSpace != -1 ? lastSpace : k;
                    break;
                }
                hadNonZero |= w != 0;
            }
            if (breakAt < 0) {
                lines.add(slice(runs, chars, owner, pos, total));
                endedWithNewline = false;
                pos = total;
                break;
            }
            char c = chars[breakAt];
            lines.add(slice(runs, chars, owner, pos, breakAt));
            endedWithNewline = c == '\n';
            pos = breakAt + (c == '\n' || c == ' ' ? 1 : 0);
        }
        if (pos >= total && endedWithNewline) {
            lines.add(new ArrayList<TextRun>());
        }
        return lines;
    }

    @Nonnull
    private static List<TextRun> slice(@Nonnull List<TextRun> runs, @Nonnull char[] chars, @Nonnull int[] owner, int from, int to) {
        List<TextRun> out = new ArrayList<>();
        int i = from;
        while (i < to) {
            int r = owner[i];
            int j = i;
            while (j < to && owner[j] == r) {
                j++;
            }
            out.add(runs.get(r).withText(new String(chars, i, j - i)));
            i = j;
        }
        return out;
    }
}
