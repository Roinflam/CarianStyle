package pers.roinflam.carianstyle.mc112.gui;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一行已经排好版的文字，对应 1.20.1 的 {@code net.minecraft.util.FormattedCharSequence}
 * <p>
 * 内部就是一串展开好的 {@link TextRun}（样式已经算到每一段上，§ 码已经解析掉），
 * 画的时候不再翻译、不再解析格式，所以适合「先断好行、每帧照着画」的用法。
 * </p>
 * <p>
 * 1.12.2 版：{@link Font#split(Component, int)} 在兼容层里早就返回 {@code List<Component>}（每行一个组件），
 * 返回值类型不能再改，所以按行拿这个类型用 {@link Font#splitSequences(Component, int)}；
 * 画用 {@link GuiGraphics#drawString(Font, FormattedCharSequence, int, int, int, boolean)}，
 * 提示框用 {@link GuiGraphics#renderSequenceTooltip}。直接用 {@code font.split} 拿到的组件列表也一样能画，两条路结果相同。
 * </p>
 * 不可变。
 */
public final class FormattedCharSequence {

    /**
     * 空行（同 1.20.1 FormattedCharSequence.EMPTY）
     */
    public static final FormattedCharSequence EMPTY = new FormattedCharSequence(Collections.<TextRun>emptyList());

    @Nonnull
    private final List<TextRun> runs;

    private FormattedCharSequence(@Nonnull List<TextRun> runs) {
        this.runs = runs;
    }

    /**
     * 由文字段建一行
     *
     * @param runs 文字段（复制一份，调用方之后改原列表不影响这里）
     * @return 一行
     */
    @Nonnull
    public static FormattedCharSequence of(@Nonnull List<TextRun> runs) {
        return runs.isEmpty() ? EMPTY : new FormattedCharSequence(Collections.unmodifiableList(new ArrayList<>(runs)));
    }

    /**
     * 由组件建一行（按当前语言翻译、展开样式；不断行）
     *
     * @param text 组件
     * @return 一行
     */
    @Nonnull
    public static FormattedCharSequence of(@Nonnull Component text) {
        return of(text.runs());
    }

    /**
     * 原样文字 + 样式（同 1.20.1 FormattedCharSequence.forward：§ 当普通字符）
     *
     * @param text  文字
     * @param style 样式
     * @return 一行
     */
    @Nonnull
    public static FormattedCharSequence forward(@Nonnull String text, @Nonnull Style style) {
        return of(Component.raw(text, style));
    }

    /**
     * 几行首尾相接拼成一行（同 1.20.1 FormattedCharSequence.composite）
     *
     * @param parts 各段
     * @return 一行
     */
    @Nonnull
    public static FormattedCharSequence composite(@Nonnull List<FormattedCharSequence> parts) {
        List<TextRun> all = new ArrayList<>();
        for (FormattedCharSequence part : parts) {
            all.addAll(part.runs);
        }
        return of(all);
    }

    /**
     * @return 文字段（只读）
     */
    @Nonnull
    public List<TextRun> runs() {
        return runs;
    }

    /**
     * @return 是否一个字都没有
     */
    public boolean isEmpty() {
        for (TextRun run : runs) {
            if (!run.text.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 转回组件（每段一个原样子组件，样式照抄），给只收 Component 的老接口用
     *
     * @return 新组件
     */
    @Nonnull
    public Component toComponent() {
        return runs.isEmpty() ? Component.empty() : Component.ofRuns(runs);
    }

    /**
     * @return 去掉样式后的纯文字
     */
    @Nonnull
    public String getString() {
        StringBuilder builder = new StringBuilder();
        for (TextRun run : runs) {
            builder.append(run.text);
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return "FormattedCharSequence" + runs;
    }
}
