package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 附魔描述的排版与着色。
 *
 * <h3>要解决什么</h3>
 * <p>
 * 本模组的描述是这种写法：
 * </p>
 * <pre>
 * 攻击目标后施加1级猩红腐败，持续[附魔等级]×20秒：每秒受到3%当前+0.075%最大生命值的
 * 魔法伤害（无视护甲，每级+33%），恢复效果-25%，护甲-50%
 * </pre>
 * <p>
 * 内容本身没问题，问题在于它被压成了一整段等宽同色的文字：
 * 一百多个字、七八个数字、三层语义混在一起，只能逐字扫。
 * </p>
 *
 * <h3>利用作者已有的标点</h3>
 * <p>
 * 这些描述的书写习惯其实很稳定，标点本身就带着结构
 * （113 条里 48 条有 {@code ；}、50 条有 {@code ：}、40 条有括号）：
 * </p>
 * <ul>
 *   <li>{@code ；} 分隔的是<b>互相独立的效果</b> —— 拆成各自一行，前面加项目符号；</li>
 *   <li>{@code ：} 前是<b>触发条件</b>、后是<b>具体作用</b> —— 条件单独一行，作用缩进跟在下面；</li>
 *   <li>{@code （…）} 是<b>补充说明</b> —— 压暗，让主干先被读到；</li>
 *   <li>数字与等级占位符分色，扫读时能直接跳到关键值上。</li>
 * </ul>
 * <p>
 * 全程<b>不改动一个字</b>，只是把作者已经写出来的层次显示出来。
 * 这样新增附魔时不需要按什么格式写，照原来的习惯写就能得到排版。
 * </p>
 *
 * <h3>自己断行，不用 {@code Font#split}</h3>
 * <p>
 * 原版的换行器对中文是「哪儿都能断」——因为中文没有空格，它只能按宽度硬切。
 * 结果 {@code （冷却180秒，夜晚减半）} 会被断成 {@code 冷却18} / {@code 0秒}，
 * 一个数字被劈成两半，读到一半得回头拼。
 * </p>
 * <p>
 * 所以这里自己分词再贪心排版：<b>数字连同单位是一个不可分割的词</b>，
 * 中文按字断（保持中文该有的自由换行），占位符也整体不拆。
 * </p>
 *
 * <h3>括号与引号内不切分</h3>
 * <p>
 * {@code （当主手武器拥有「暗月」附魔时：夜晚回复持续时间翻倍）} 这种补充说明里
 * 也有 {@code ：}。切分时按括号深度判断，只在最外层动手，
 * 否则一句完整的补充会被从中间劈开。
 * </p>
 *
 * @author FlameForge
 * @version 2.0
 */
@OnlyIn(Dist.CLIENT)
public final class DescriptionText {

    /** 表示「显示原始公式、不代入等级」 */
    public static final int FORMULA = 0;

    /**
     * 描述里出现过的全部等级占位符写法。
     * <p>作者在不同附魔里用了不同的措辞，这里全部认下来。</p>
     */
    private static final String PLACEHOLDER = "\\[(?:附魔等级|等级|效果等级|冻伤等级)\\]";

    /**
     * {@code ([等级]-1)×N+M}，两个数字后面允许各带一个百分号。
     * <p>两个百分号必须成对出现；一个有一个没有说明这不是同一量纲，不评估。</p>
     */
    private static final Pattern OFFSET_FORM = Pattern.compile(
            "\\(" + PLACEHOLDER + "-1\\)×([0-9]+(?:\\.[0-9]+)?)(%?)\\+([0-9]+(?:\\.[0-9]+)?)(%?)");

    /** {@code [等级]×N} */
    private static final Pattern SIMPLE_FORM = Pattern.compile(
            PLACEHOLDER + "×([0-9]+(?:\\.[0-9]+)?)");

    /** 裸占位符（没跟乘法的），代入时直接换成等级数字 */
    private static final Pattern BARE = Pattern.compile(PLACEHOLDER);

    /** 高亮用：数字 + 可选单位 */
    private static final Pattern NUMBER = Pattern.compile(
            "[+\\-]?[0-9]+(?:\\.[0-9]+)?(?:%|秒|格|级|层|倍|点)?");

    /** 占位符与代入结果的高亮色（金色，与界面强调色一致） */
    private static final TextColor COLOR_LEVEL = TextColor.fromRgb(0xD9B46A);

    /** 数值高亮色（浅青，在暗底上比白字更跳但不刺眼） */
    private static final TextColor COLOR_NUMBER = TextColor.fromRgb(0x8FD0C8);

    /** 正文色 */
    private static final TextColor COLOR_TEXT = TextColor.fromRgb(0xE8E2D4);

    /** 触发条件色：比正文略亮，让「什么时候生效」先被看到 */
    private static final TextColor COLOR_CONDITION = TextColor.fromRgb(0xFFF4E0);

    /** 补充说明色：压暗，主干读完再看 */
    private static final TextColor COLOR_ASIDE = TextColor.fromRgb(0x9C927E);

    private DescriptionText() {
    }

    /**
     * 排好版的一行——<b>已经断好行，调用方直接画，不要再走 {@code Font#split}</b>。
     */
    public static final class Line {

        /** 该行的富文本 */
        private final Component text;

        /** 缩进级别：0 = 效果主体，1 = 该效果的具体作用（额外缩进一级） */
        private final int indent;

        /**
         * 是否为一条效果的首行。
         * <p>
         * 项目符号只画在首行：一条效果折成三行时，三行都带符号会看起来像三条效果，
         * 正好抹掉了分段本来要传达的信息。
         * </p>
         */
        private final boolean blockStart;

        Line(Component text, int indent, boolean blockStart) {
            this.text = text;
            this.indent = indent;
            this.blockStart = blockStart;
        }

        /**
         * @return 富文本
         */
        @Nonnull
        public Component getText() {
            return text;
        }

        /**
         * @return 缩进级别
         */
        public int getIndent() {
            return indent;
        }

        /**
         * @return 是否为一条效果的首行（项目符号画在这里）
         */
        public boolean isBlockStart() {
            return blockStart;
        }
    }

    /**
     * 把原始描述加工成排好版、已断行的多行富文本。
     *
     * @param raw    原始描述文本
     * @param level  代入的附魔等级；{@link #FORMULA} 表示保留原始公式
     * @param font   字体，用于测量宽度
     * @param width  可用宽度（像素），已扣除项目符号与缩进
     * @param indent 作用行相对效果行多出的缩进（像素）
     * @return 逐行结果，顺序即显示顺序
     */
    @Nonnull
    public static List<Line> layout(@Nonnull String raw, int level,
                                    @Nonnull Font font, int width, int indent) {
        String text = level == FORMULA ? raw : substitute(raw, level);
        boolean evaluated = level != FORMULA;

        List<Line> lines = new ArrayList<>();
        for (String effect : splitTopLevel(text, '；')) {
            String trimmed = effect.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            List<Span> spans = tokenize(trimmed, evaluated, COLOR_TEXT);
            int colon = indexOfTopLevel(trimmed, '：');

            // 拆不拆冒号，看这一段是否<b>本来就要折行</b>。
            //
            // 早先的判据是「冒号后面的内容超过 12 字」，于是同一句「夜晚时：」
            // 在暗月那儿被拆成两行、在满月那儿没拆——同样的写法两种排版，看着像随机的。
            // 改成按实际宽度判断之后规则就自洽了：一行放得下就不动它，
            // 放不下反正要断，那就断在冒号这个语义边界上，而不是断在半句话中间。
            if (colon > 0 && totalWidth(font, spans) > width) {
                lines.addAll(wrap(font, tokenize(trimmed.substring(0, colon + 1),
                        evaluated, COLOR_CONDITION), width, 0, true));
                lines.addAll(wrap(font, tokenize(trimmed.substring(colon + 1),
                        evaluated, COLOR_TEXT), width - indent, 1, false));
            } else {
                lines.addAll(wrap(font, spans, width, 0, true));
            }
        }
        if (lines.isEmpty()) {
            lines.addAll(wrap(font, tokenize(text, evaluated, COLOR_TEXT), width, 0, true));
        }
        return lines;
    }

    /**
     * 一个不可再分的着色片段。
     */
    private static final class Span {

        /** 文本 */
        private final String text;

        /** 颜色 */
        private final TextColor color;

        /** 是否加粗（仅等级占位符用） */
        private final boolean bold;

        Span(String text, TextColor color, boolean bold) {
            this.text = text;
            this.color = color;
            this.bold = bold;
        }

        /**
         * @return 本片段的富文本形式
         */
        @Nonnull
        Component toComponent() {
            Style style = Style.EMPTY.withColor(color);
            return Component.literal(text).withStyle(bold ? style.withBold(true) : style);
        }
    }

    /**
     * 测量若干片段拼起来的总宽度。
     *
     * @param font  字体
     * @param spans 片段
     * @return 像素宽度
     */
    private static int totalWidth(@Nonnull Font font, @Nonnull List<Span> spans) {
        int sum = 0;
        for (Span span : spans) {
            sum += font.width(span.toComponent());
        }
        return sum;
    }

    /**
     * 贪心排版：逐片段累加，超出宽度就换行。
     * <p>
     * 因为 {@link #tokenize} 已经保证「数字 + 单位」「占位符」各自是一个片段，
     * 而中文被拆成了单字，所以在片段边界换行天然满足中文排版习惯，
     * 又不会把一个数字劈开。
     * </p>
     *
     * @param font   字体
     * @param spans  片段
     * @param width      可用宽度
     * @param indent     缩进级别
     * @param blockStart 本批的第一行是否为一条效果的开头
     * @return 已断好行的结果
     */
    @Nonnull
    private static List<Line> wrap(@Nonnull Font font, @Nonnull List<Span> spans,
                                   int width, int indent, boolean blockStart) {
        List<Line> lines = new ArrayList<>();
        MutableComponent current = Component.empty();
        int used = 0;
        boolean empty = true;

        for (Span span : spans) {
            int w = font.width(span.toComponent());
            if (!empty && used + w > width) {
                lines.add(new Line(current, indent, blockStart && lines.isEmpty()));
                current = Component.empty();
                used = 0;
            }
            // 行首不留空白类字符，否则每一行都会莫名缩进一格
            if (empty && span.text.isBlank()) {
                continue;
            }
            current.append(span.toComponent());
            used += w;
            empty = false;
        }
        if (!empty) {
            lines.add(new Line(current, indent, blockStart && lines.isEmpty()));
        }
        return lines;
    }

    /**
     * 把一段文本切成着色片段。
     * <p>
     * 切分粒度是刻意的：数字连同紧跟的单位是<b>一个</b>片段（不可断开），
     * 中文与标点<b>逐字</b>成片段（可自由断行），占位符整体一个片段。
     * </p>
     *
     * @param text      文本
     * @param evaluated 是否处于代入模式（代入后占位符已消失）
     * @param baseColor 正文色
     * @return 片段列表
     */
    @Nonnull
    private static List<Span> tokenize(@Nonnull String text, boolean evaluated,
                                       @Nonnull TextColor baseColor) {
        List<Span> spans = new ArrayList<>();
        int depth = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '（' || c == '(' || c == '「') {
                depth++;
            } else if (c == '）' || c == ')' || c == '」') {
                depth = Math.max(0, depth - 1);
            }
            // 括号内是补充说明，整体压暗；里面的数字也不再高亮，
            // 否则一段本该退后的文字里又跳出几个亮色，压暗就白压了
            boolean aside = depth > 0 || c == '）' || c == ')';
            TextColor color = aside ? COLOR_ASIDE : baseColor;

            if (!evaluated) {
                Matcher ph = BARE.matcher(text);
                if (ph.find(i) && ph.start() == i) {
                    spans.add(new Span(ph.group(), COLOR_LEVEL, true));
                    i = ph.end();
                    continue;
                }
            }
            Matcher num = NUMBER.matcher(text);
            if (num.find(i) && num.start() == i) {
                spans.add(new Span(num.group(), aside ? color : COLOR_NUMBER, false));
                i = num.end();
                continue;
            }
            spans.add(new Span(String.valueOf(c), color, false));
            i++;
        }
        return spans;
    }

    /**
     * 按分隔符切分，但跳过括号与引号内部。
     *
     * @param text      文本
     * @param separator 分隔符
     * @return 切分结果
     */
    @Nonnull
    private static List<String> splitTopLevel(@Nonnull String text, char separator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '（' || c == '(' || c == '「') {
                depth++;
            } else if (c == '）' || c == ')' || c == '」') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && (c == separator || c == ';')) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    /**
     * 找出最外层的第一个指定字符。
     *
     * @param text   文本
     * @param target 目标字符
     * @return 下标；不存在时返回 -1
     */
    private static int indexOfTopLevel(@Nonnull String text, char target) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '（' || c == '(' || c == '「') {
                depth++;
            } else if (c == '）' || c == ')' || c == '」') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && (c == target || c == ':')) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 按等级代入并求出可算的式子。
     *
     * @param raw   原始描述
     * @param level 等级
     * @return 代入后的文本
     */
    @Nonnull
    private static String substitute(@Nonnull String raw, int level) {
        StringBuilder out = new StringBuilder(raw.length() + 16);

        Matcher m = OFFSET_FORM.matcher(raw);
        while (m.find()) {
            String unitA = m.group(2);
            String unitB = m.group(4);
            if (!unitA.equals(unitB)) {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
                continue;
            }
            double per = Double.parseDouble(m.group(1));
            double base = Double.parseDouble(m.group(3));
            m.appendReplacement(out,
                    Matcher.quoteReplacement(trim((level - 1) * per + base) + unitA));
        }
        m.appendTail(out);

        String stage = out.toString();
        out.setLength(0);
        m = SIMPLE_FORM.matcher(stage);
        while (m.find()) {
            double per = Double.parseDouble(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(trim(level * per)));
        }
        m.appendTail(out);

        return BARE.matcher(out.toString()).replaceAll(String.valueOf(level));
    }





    /**
     * 去掉浮点数末尾多余的零。
     *
     * @param value 数值
     * @return 文本
     */
    @Nonnull
    private static String trim(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        String s = String.format("%.2f", value);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * 等级选择条的标签。
     *
     * @param level 等级；{@link #FORMULA} 表示公式档
     * @return 标签文本
     */
    @Nonnull
    public static Component chipLabel(int level) {
        return level == FORMULA
                ? Component.translatable("carianstyle.codex.desc.formula")
                : Component.literal(UiTheme.roman(level));
    }
}
