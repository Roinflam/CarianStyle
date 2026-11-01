package pers.roinflam.carianstyle.mc112.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 界面文字（可改的富文本树）
 * <p>
 * 1.12.2 的客户端界面没有 Component，物品名、Lore 也只是带 § 码的字符串。界面上要拼接、上色、换行、量宽度的文字
 * 统一用这个类，写法和 1.20.1 的 MutableComponent 基本一样：
 * <ul>
 *   <li>{@link #literal}：普通文字，<b>§ 码会被当作格式解析</b>（和 1.20.1 Component.literal 画出来的效果一致）；</li>
 *   <li>{@link #raw}：原样文字，§ 当普通字符画出来（相当于 1.20.1 的 FormattedCharSequence.forward）；</li>
 *   <li>{@link #translatable}：翻译键，画的时候按当前语言取译文，参数可以是 Component（保留各自的样式）。</li>
 * </ul>
 * 颜色可以是任意 RGB（界面用主题色），要写进物品数据时用 {@link #toLegacy()} 换成 16 色 § 码。
 * <p>
 * § 码的解析规则照 1.12.2 字体渲染器：颜色码清掉粗体等格式，§r 回到这一层自己的样式，不认识的格式码按白色处理，
 * 末尾落单的 § 按普通字符画。
 */
public class Component {

    /**
     * 内容种类
     */
    public enum Kind {
        /**
         * 普通文字（解析 § 码）
         */
        LITERAL,
        /**
         * 原样文字（§ 当普通字符）
         */
        RAW,
        /**
         * 翻译键
         */
        TRANSLATABLE
    }

    /**
     * 参数占位标记：先用标记代入格式化，再把标记换回参数本身，这样参数是 Component 时能保留样式
     */
    private static final char MARK_START = '\uF8F0';
    private static final char MARK_END = '\uF8F1';

    private static final Style UNKNOWN_CODE_STYLE = Style.EMPTY.applyLegacyFormat(TextFormatting.WHITE);

    @Nonnull
    private final Kind kind;
    /**
     * 文字，或翻译键
     */
    @Nonnull
    private final String text;
    @Nonnull
    private final Object[] args;
    @Nonnull
    private Style style = Style.EMPTY;
    @Nonnull
    private final List<Component> siblings = new ArrayList<>();

    /**
     * 翻译结果缓存：语言没变就不重新格式化
     */
    @Nullable
    private String cachedLanguage;
    @Nullable
    private List<Object> cachedParts;

    protected Component(@Nonnull Kind kind, @Nonnull String text, @Nonnull Object[] args) {
        this.kind = kind;
        this.text = text;
        this.args = args;
    }

    /**
     * 普通文字（§ 码按格式解析）
     *
     * @param text 文字
     * @return 新组件
     */
    @Nonnull
    public static Component literal(@Nullable String text) {
        return new Component(Kind.LITERAL, text == null ? "" : text, new Object[0]);
    }

    /**
     * 原样文字（§ 当普通字符画，编辑框里显示用户输入时用）
     *
     * @param text 文字
     * @return 新组件
     */
    @Nonnull
    public static Component raw(@Nullable String text) {
        return new Component(Kind.RAW, text == null ? "" : text, new Object[0]);
    }

    /**
     * 原样文字 + 指定样式（相当于 1.20.1 的 FormattedCharSequence.forward(text, style)）
     *
     * @param text  文字
     * @param style 样式
     * @return 新组件
     */
    @Nonnull
    public static Component raw(@Nullable String text, @Nonnull Style style) {
        return raw(text).setStyle(style);
    }

    /**
     * 翻译键
     *
     * @param key  翻译键
     * @param args 参数（Component 保留样式，其它对象按 String.valueOf 代入）
     * @return 新组件
     */
    @Nonnull
    public static Component translatable(@Nonnull String key, @Nonnull Object... args) {
        return new Component(Kind.TRANSLATABLE, key, args.clone());
    }

    /**
     * 带后备文字的翻译键：语言文件里有这个键就照常翻译，没有就直接显示后备文字
     * <p>
     * 对应 1.20.1 的 {@code Component.translatableWithFallback}。1.12.2 的 {@code I18n.format} 遇到没有的键
     * 会原样显示键名，所以先用 {@link I18n#hasKey} 查一下。
     * </p>
     *
     * @param key      翻译键
     * @param fallback 没有译文时显示的文字（不翻译、不格式化）
     * @param args     参数（Component 保留样式，其它对象按 String.valueOf 代入）
     * @return 新组件
     */
    @Nonnull
    public static Component translatableWithFallback(@Nonnull String key, @Nonnull String fallback, @Nonnull Object... args) {
        return I18n.hasKey(key) ? translatable(key, args) : literal(fallback);
    }

    /**
     * 空文字
     *
     * @return 新组件
     */
    @Nonnull
    public static Component empty() {
        return literal("");
    }

    /**
     * 多行翻译：1.12.2 的语言文件不解析转义，一条译文里写不了换行，一段多行文字就拆成
     * {@code key.1}、{@code key.2}……逐条翻译，中间用换行接起来（数到第一个没有译文的序号为止）。
     * 一条都没有时退回 {@code translatable(key, args)}。参数对每一行都代入一次。
     * <p>
     * 换行符在 {@link Font#split}、{@link Tooltip} 和 {@link GuiGraphics#drawWordWrap} 里会断行；单行的 drawString 不处理换行。
     *
     * @param key  翻译键前缀
     * @param args 参数
     * @return 新组件
     */
    @Nonnull
    public static Component translatableLines(@Nonnull String key, @Nonnull Object... args) {
        if (!Ui.hasTranslation(key + ".1")) {
            return translatable(key, args);
        }
        Component root = empty();
        for (int i = 1; Ui.hasTranslation(key + "." + i); i++) {
            if (i > 1) {
                root.append(raw("\n"));
            }
            root.append(translatable(key + "." + i, args));
        }
        return root;
    }

    /**
     * null 当空串
     *
     * @param text 文字
     * @return 新组件
     */
    @Nonnull
    public static Component nullToEmpty(@Nullable String text) {
        return literal(text);
    }

    @Nonnull
    public Kind kind() {
        return kind;
    }

    /**
     * 自己这一层的文字（翻译组件返回翻译键）
     *
     * @return 文字或翻译键
     */
    @Nonnull
    public String text() {
        return text;
    }

    /**
     * 翻译键（不是翻译组件时为 null）
     *
     * @return 翻译键
     */
    @Nullable
    public String key() {
        return kind == Kind.TRANSLATABLE ? text : null;
    }

    /**
     * 翻译参数（副本）
     *
     * @return 参数
     */
    @Nonnull
    public Object[] args() {
        return args.clone();
    }

    @Nonnull
    public Style getStyle() {
        return style;
    }

    /**
     * 直接替换样式
     *
     * @param style 样式
     * @return this
     */
    @Nonnull
    public Component setStyle(@Nonnull Style style) {
        this.style = style;
        return this;
    }

    /**
     * 叠加样式（参数里写了的项覆盖现有的）
     *
     * @param style 样式
     * @return this
     */
    @Nonnull
    public Component withStyle(@Nonnull Style style) {
        this.style = style.applyTo(this.style);
        return this;
    }

    /**
     * 叠加格式码
     *
     * @param formattings 格式码
     * @return this
     */
    @Nonnull
    public Component withStyle(@Nonnull TextFormatting... formattings) {
        this.style = this.style.applyFormats(formattings);
        return this;
    }

    /**
     * 用函数改样式
     *
     * @param operator 样式变换
     * @return this
     */
    @Nonnull
    public Component withStyle(@Nonnull UnaryOperator<Style> operator) {
        this.style = operator.apply(this.style);
        return this;
    }

    /**
     * 设颜色（RGB）
     *
     * @param rgb RGB；带透明度的会被去掉
     * @return this
     */
    @Nonnull
    public Component withColor(int rgb) {
        this.style = this.style.withColor(rgb & 0xFFFFFF);
        return this;
    }

    /**
     * 追加一个子组件
     *
     * @param sibling 子组件
     * @return this
     */
    @Nonnull
    public Component append(@Nonnull Component sibling) {
        siblings.add(sibling);
        return this;
    }

    /**
     * 追加一段普通文字
     *
     * @param text 文字（§ 码按格式解析）
     * @return this
     */
    @Nonnull
    public Component append(@Nonnull String text) {
        return append(literal(text));
    }

    /**
     * 子组件（只读）
     *
     * @return 列表
     */
    @Nonnull
    public List<Component> getSiblings() {
        return Collections.unmodifiableList(siblings);
    }

    /**
     * 复制：内容和样式相同，子组件列表是新列表但里面是同一批对象（同 1.20.1 copy）
     *
     * @return 新组件
     */
    @Nonnull
    public Component copy() {
        Component copy = new Component(kind, text, args);
        copy.style = style;
        copy.siblings.addAll(siblings);
        return copy;
    }

    /**
     * 只复制内容，不带样式和子组件（同 1.20.1 plainCopy）
     *
     * @return 新组件
     */
    @Nonnull
    public Component plainCopy() {
        return new Component(kind, text, args);
    }

    /**
     * 拼出来的整段文字（§ 码原样保留，翻译按当前语言）
     *
     * @return 文字
     */
    @Nonnull
    public String getString() {
        StringBuilder builder = new StringBuilder();
        appendString(builder);
        return builder.toString();
    }

    private void appendString(@Nonnull StringBuilder builder) {
        if (kind == Kind.TRANSLATABLE) {
            for (Object part : translatedParts()) {
                if (part instanceof String) {
                    builder.append((String) part);
                } else {
                    Object arg = args[(Integer) part];
                    if (arg instanceof Component) {
                        ((Component) arg).appendString(builder);
                    } else {
                        builder.append(arg);
                    }
                }
            }
        } else {
            builder.append(text);
        }
        for (Component sibling : siblings) {
            sibling.appendString(builder);
        }
    }

    /**
     * 展开成一段段同样式的文字（相邻同样式的会合并）
     *
     * @return 文字段
     */
    @Nonnull
    public List<TextRun> runs() {
        List<TextRun> out = new ArrayList<>();
        collect(Style.EMPTY, out);
        return out;
    }

    /**
     * 以给定样式为底展开
     *
     * @param parent 底样式
     * @return 文字段
     */
    @Nonnull
    public List<TextRun> runs(@Nonnull Style parent) {
        List<TextRun> out = new ArrayList<>();
        collect(parent, out);
        return out;
    }

    private void collect(@Nonnull Style parent, @Nonnull List<TextRun> out) {
        Style resolved = style.applyTo(parent);
        switch (kind) {
            case RAW:
                addRun(out, text, resolved);
                break;
            case LITERAL:
                collectFormatted(text, resolved, out);
                break;
            default:
                for (Object part : translatedParts()) {
                    if (part instanceof String) {
                        collectFormatted((String) part, resolved, out);
                    } else {
                        Object arg = args[(Integer) part];
                        if (arg instanceof Component) {
                            ((Component) arg).collect(resolved, out);
                        } else {
                            collectFormatted(String.valueOf(arg), resolved, out);
                        }
                    }
                }
                break;
        }
        for (Component sibling : siblings) {
            sibling.collect(resolved, out);
        }
    }

    /**
     * 按字体渲染器的规则解析一段带 § 码的文字
     *
     * @param text 文字
     * @param base 这一层的样式（§r 回到它）
     * @param out  输出
     */
    static void collectFormatted(@Nonnull String text, @Nonnull Style base, @Nonnull List<TextRun> out) {
        if (text.indexOf('\u00A7') < 0) {
            addRun(out, text, base);
            return;
        }
        Style current = base;
        StringBuilder pending = new StringBuilder();
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < length) {
                if (pending.length() > 0) {
                    addRun(out, pending.toString(), current);
                    pending.setLength(0);
                }
                TextFormatting formatting = Style.byCode(text.charAt(i + 1));
                if (formatting == null) {
                    // 字体渲染器把不认识的格式码当成白色
                    current = UNKNOWN_CODE_STYLE;
                } else if (formatting == TextFormatting.RESET) {
                    current = base;
                } else {
                    current = current.applyLegacyFormat(formatting);
                }
                i++;
            } else {
                pending.append(c);
            }
        }
        if (pending.length() > 0) {
            addRun(out, pending.toString(), current);
        }
    }

    private static void addRun(@Nonnull List<TextRun> out, @Nonnull String text, @Nonnull Style style) {
        if (text.isEmpty()) {
            return;
        }
        TextRun run = new TextRun(text, style.getColor(), style.flags());
        int last = out.size() - 1;
        if (last >= 0 && out.get(last).sameStyle(run)) {
            out.set(last, run.withText(out.get(last).text + text));
        } else {
            out.add(run);
        }
    }

    /**
     * 翻译结果拆成片段：String 是文字，Integer 是参数下标
     */
    @Nonnull
    private List<Object> translatedParts() {
        String language = currentLanguage();
        if (cachedParts != null && language.equals(cachedLanguage)) {
            return cachedParts;
        }
        List<Object> parts = new ArrayList<>();
        Object[] markers = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            markers[i] = MARK_START + Integer.toString(i) + MARK_END;
        }
        String formatted = I18n.format(text, markers);
        if (formatted.startsWith("Format error: ") && args.length > 0) {
            // 译文里用了 %d 之类只认数字的占位符：退回直接代入，Component 参数只保留文字
            Object[] plain = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                plain[i] = args[i] instanceof Component ? ((Component) args[i]).getString() : args[i];
            }
            parts.add(I18n.format(text, plain));
        } else {
            int from = 0;
            while (true) {
                int start = formatted.indexOf(MARK_START, from);
                int end = start < 0 ? -1 : formatted.indexOf(MARK_END, start);
                if (start < 0 || end < 0) {
                    break;
                }
                int index;
                try {
                    index = Integer.parseInt(formatted.substring(start + 1, end));
                } catch (NumberFormatException e) {
                    index = -1;
                }
                if (index < 0 || index >= args.length) {
                    break;
                }
                if (start > from) {
                    parts.add(formatted.substring(from, start));
                }
                parts.add(index);
                from = end + 1;
            }
            if (from < formatted.length()) {
                parts.add(formatted.substring(from));
            }
        }
        cachedLanguage = language;
        cachedParts = parts;
        return parts;
    }

    @Nonnull
    private static String currentLanguage() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null || mc.gameSettings == null || mc.gameSettings.language == null ? "" : mc.gameSettings.language;
    }

    /**
     * 转成带 § 码的字符串（写进物品名、聊天栏用）
     * <p>
     * 1.12.2 只有 16 种颜色，任意 RGB 会换成最接近的原版颜色；样式变化处先写颜色码（会清掉格式）再补格式码。
     *
     * @return 字符串
     */
    @Nonnull
    public String toLegacy() {
        return toLegacy(runs());
    }

    /**
     * 文字段转成带 § 码的字符串
     *
     * @param runs 文字段
     * @return 字符串
     */
    @Nonnull
    public static String toLegacy(@Nonnull List<TextRun> runs) {
        StringBuilder builder = new StringBuilder();
        Integer currentRgb = null;
        int currentFlags = 0;
        boolean first = true;
        for (TextRun run : runs) {
            boolean colorChanged = first ? run.rgb != null : !java.util.Objects.equals(currentRgb, run.rgb);
            boolean flagsDropped = (currentFlags & ~run.flags) != 0;
            if (colorChanged || flagsDropped) {
                if (run.rgb == null) {
                    builder.append('\u00A7').append('r');
                } else {
                    builder.append('\u00A7').append(Style.code(Style.nearestLegacy(run.rgb)));
                }
                currentFlags = 0;
            }
            int added = run.flags & ~currentFlags;
            appendFlagCodes(builder, added);
            currentRgb = run.rgb;
            currentFlags = run.flags;
            builder.append(run.text);
            first = false;
        }
        return builder.toString();
    }

    /**
     * 按位写格式码（k l m n o）
     *
     * @param builder 输出
     * @param flags   {@link TextRun} 的位
     */
    static void appendFlagCodes(@Nonnull StringBuilder builder, int flags) {
        if ((flags & TextRun.OBFUSCATED) != 0) {
            builder.append("\u00A7k");
        }
        if ((flags & TextRun.BOLD) != 0) {
            builder.append("\u00A7l");
        }
        if ((flags & TextRun.STRIKETHROUGH) != 0) {
            builder.append("\u00A7m");
        }
        if ((flags & TextRun.UNDERLINE) != 0) {
            builder.append("\u00A7n");
        }
        if ((flags & TextRun.ITALIC) != 0) {
            builder.append("\u00A7o");
        }
    }

    /**
     * 文字段拼成一个组件（每段一个原样子组件，样式照抄）
     *
     * @param runs 文字段
     * @return 新组件
     */
    @Nonnull
    public static Component ofRuns(@Nonnull List<TextRun> runs) {
        if (runs.size() == 1) {
            TextRun run = runs.get(0);
            return raw(run.text, run.style());
        }
        Component root = empty();
        for (TextRun run : runs) {
            root.append(raw(run.text, run.style()));
        }
        return root;
    }

    @Override
    public String toString() {
        return "Component{" + kind + " '" + text + "', style=" + style + ", siblings=" + siblings + '}';
    }
}
