package pers.roinflam.carianstyle.mc112.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 画布：方法名和参数照 1.20.1 的 GuiGraphics
 * <p>
 * 色块（fill / fillGradient）先攒进缓冲，遇到画字、画物品、改裁剪区、改变换时才一次画出去，界面上几百个色块只提交几次。
 * 所以<b>不要绕过它直接调 FontRenderer / GL</b>；实在要调，先 {@link #flush()}。
 * <p>
 * 画的顺序就是遮挡顺序（后画的盖住先画的）：色块和文字关掉深度测试画，只有物品打开深度（方块模型自己要用）。
 * 裁剪区用界面坐标、可以嵌套（取交集），不受 {@link #pose()} 的变换影响，同 1.20.1。
 */
public final class GuiGraphics {

    /**
     * 提示框颜色（和原版一致）
     */
    private static final int TOOLTIP_BACKGROUND = 0xF0100010;
    private static final int TOOLTIP_BORDER_TOP = 0x505000FF;
    private static final int TOOLTIP_BORDER_BOTTOM = 0x5028007F;

    /**
     * 色块缓冲（所有画布共用，只在渲染线程用）
     */
    @Nullable
    private static BufferBuilder fillBuffer;
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    @Nonnull
    private final Minecraft minecraft;
    @Nonnull
    private final PoseStack pose;
    private final Deque<int[]> scissors = new ArrayDeque<>();
    private int guiWidth;
    private int guiHeight;
    private int scaleFactor;
    private boolean filling;

    public GuiGraphics(@Nonnull Minecraft minecraft) {
        this.minecraft = minecraft;
        this.pose = new PoseStack(this);
        updateResolution();
    }

    private void updateResolution() {
        ScaledResolution resolution = new ScaledResolution(minecraft);
        guiWidth = resolution.getScaledWidth();
        guiHeight = resolution.getScaledHeight();
        scaleFactor = resolution.getScaleFactor();
    }

    /**
     * 一帧开始（界面基类调用）：重新读缩放、关深度测试
     */
    public void begin() {
        updateResolution();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 一帧结束（界面基类调用）：把缓冲画完，清掉没配对的裁剪和变换，深度测试恢复成原版默认的打开状态
     */
    public void end() {
        flush();
        if (!scissors.isEmpty()) {
            scissors.clear();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        pose.popAll();
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Nonnull
    public PoseStack pose() {
        return pose;
    }

    @Nonnull
    public Minecraft minecraft() {
        return minecraft;
    }

    public int guiWidth() {
        return guiWidth;
    }

    public int guiHeight() {
        return guiHeight;
    }

    // 色块

    /**
     * 把攒着的色块画出去
     */
    public void flush() {
        if (!filling) {
            return;
        }
        filling = false;
        BufferBuilder buffer = fillBuffer;
        if (buffer == null) {
            return;
        }
        buffer.finishDrawing();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        // 关掉 alpha 测试：原版默认丢弃透明度低于 0.1 的像素，很淡的色块（扫描线、悬停底色）会整个消失
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        UPLOADER.draw(buffer);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    @Nonnull
    private BufferBuilder fillBuffer() {
        BufferBuilder buffer = fillBuffer;
        if (buffer == null) {
            buffer = new BufferBuilder(262144);
            fillBuffer = buffer;
        }
        if (!filling) {
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            filling = true;
        }
        return buffer;
    }

    /**
     * 实心矩形（坐标反了会自动交换，同 1.20.1）
     *
     * @param x1    左
     * @param y1    上
     * @param x2    右（不含）
     * @param y2    下（不含）
     * @param color ARGB
     */
    public void fill(int x1, int y1, int x2, int y2, int color) {
        if (x1 > x2) {
            int t = x1;
            x1 = x2;
            x2 = t;
        }
        if (y1 > y2) {
            int t = y1;
            y1 = y2;
            y2 = t;
        }
        if (x1 == x2 || y1 == y2) {
            return;
        }
        quad(x1, y1, x2, y2, color, color, color, color);
    }

    /**
     * 竖直渐变（上 → 下，坐标不交换，同 1.20.1）
     *
     * @param x1     左
     * @param y1     上
     * @param x2     右
     * @param y2     下
     * @param top    上端颜色（ARGB）
     * @param bottom 下端颜色（ARGB）
     */
    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        quad(x1, y1, x2, y2, top, top, bottom, bottom);
    }

    /**
     * 水平渐变（左 → 右；1.20.1 里靠旋转坐标系画竖直渐变实现，这里直接按顶点上色）
     *
     * @param x1    左
     * @param y1    上
     * @param x2    右
     * @param y2    下
     * @param left  左端颜色（ARGB）
     * @param right 右端颜色（ARGB）
     */
    public void fillGradientHorizontal(int x1, int y1, int x2, int y2, int left, int right) {
        quad(x1, y1, x2, y2, left, right, right, left);
    }

    /**
     * 四个角各自上色的矩形
     */
    private void quad(int x1, int y1, int x2, int y2, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        BufferBuilder buffer = fillBuffer();
        // 顶点顺序和原版 drawRect 一致：左下、右下、右上、左上
        vertex(buffer, x1, y2, bottomLeft);
        vertex(buffer, x2, y2, bottomRight);
        vertex(buffer, x2, y1, topRight);
        vertex(buffer, x1, y1, topLeft);
    }

    private static void vertex(@Nonnull BufferBuilder buffer, int x, int y, int color) {
        buffer.pos(x, y, 0.0D).color((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255).endVertex();
    }

    public void hLine(int x1, int x2, int y, int color) {
        if (x2 < x1) {
            int t = x1;
            x1 = x2;
            x2 = t;
        }
        fill(x1, y, x2 + 1, y + 1, color);
    }

    public void vLine(int x, int y1, int y2, int color) {
        if (y2 < y1) {
            int t = y1;
            y1 = y2;
            y2 = t;
        }
        fill(x, y1 + 1, x + 1, y2, color);
    }

    /**
     * 输入框选区的反色块（同 1.20.1 的 guiTextHighlight / 1.12.2 GuiTextField 的选区：蓝色 + OR_REVERSE 逻辑运算）
     *
     * @param x1 左
     * @param y1 上
     * @param x2 右
     * @param y2 下
     */
    public void fillHighlight(int x1, int y1, int x2, int y2) {
        if (x1 > x2) {
            int t = x1;
            x1 = x2;
            x2 = t;
        }
        if (y1 > y2) {
            int t = y1;
            y1 = y2;
            y2 = t;
        }
        if (x1 == x2 || y1 == y2) {
            return;
        }
        flush();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.color(0.0F, 0.0F, 1.0F, 1.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.enableColorLogic();
        GlStateManager.colorLogicOp(GlStateManager.LogicOp.OR_REVERSE);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        buffer.pos(x1, y2, 0.0D).endVertex();
        buffer.pos(x2, y2, 0.0D).endVertex();
        buffer.pos(x2, y1, 0.0D).endVertex();
        buffer.pos(x1, y1, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.disableColorLogic();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 1 像素边框（同 1.20.1 renderOutline）
     *
     * @param x      左
     * @param y      上
     * @param width  宽
     * @param height 高
     * @param color  ARGB
     */
    public void renderOutline(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /**
     * 一批绘制放在一起（1.20.1 用它少刷几次缓冲；这里色块本来就是攒着画的，直接执行）
     *
     * @param task 绘制
     */
    public void drawManaged(@Nonnull Runnable task) {
        task.run();
    }

    // 裁剪

    /**
     * 开始裁剪（和外层裁剪区取交集，可嵌套）
     *
     * @param x1 左
     * @param y1 上
     * @param x2 右
     * @param y2 下
     */
    public void enableScissor(int x1, int y1, int x2, int y2) {
        int[] rect = {x1, y1, Math.max(x1, x2), Math.max(y1, y2)};
        int[] outer = scissors.peek();
        if (outer != null) {
            int left = Math.max(rect[0], outer[0]);
            int top = Math.max(rect[1], outer[1]);
            int right = Math.min(rect[2], outer[2]);
            int bottom = Math.min(rect[3], outer[3]);
            rect = new int[]{left, top, Math.max(left, right), Math.max(top, bottom)};
        }
        scissors.push(rect);
        applyScissor(rect);
    }

    /**
     * 结束最近一层裁剪
     */
    public void disableScissor() {
        if (scissors.isEmpty()) {
            return;
        }
        scissors.pop();
        applyScissor(scissors.peek());
    }

    /**
     * 点是否在当前裁剪区内（没有裁剪时总是 true）
     *
     * @param x 界面 X
     * @param y 界面 Y
     * @return true = 在里面
     */
    public boolean containsPointInScissor(int x, int y) {
        int[] rect = scissors.peek();
        return rect == null || (x >= rect[0] && x < rect[2] && y >= rect[1] && y < rect[3]);
    }

    private void applyScissor(@Nullable int[] rect) {
        flush();
        if (rect == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }
        int scale = scaleFactor;
        int x = rect[0] * scale;
        int y = minecraft.displayHeight - rect[3] * scale;
        int w = (rect[2] - rect[0]) * scale;
        int h = (rect[3] - rect[1]) * scale;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, Math.max(0, w), Math.max(0, h));
    }

    // 文字

    /**
     * 画文字段（返回值同 1.20.1：结束 x，带阴影时再加 1）
     */
    public int drawRuns(@Nonnull Font font, @Nonnull List<TextRun> runs, int x, int y, int color, boolean shadow) {
        if (runs.isEmpty()) {
            return x + (shadow ? 1 : 0);
        }
        flush();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        float end = font.draw(runs, x, y, color, shadow);
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return (int) end + (shadow ? 1 : 0);
    }

    /**
     * 画带 § 码的文字（默认带阴影，同 1.20.1）
     */
    public int drawString(@Nonnull Font font, @Nullable String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    /**
     * 画带 § 码的文字（§ 码按格式解析）
     *
     * @param font   字体
     * @param text   文字；null 时什么都不画、返回 0（同 1.20.1）
     * @param x      左
     * @param y      上
     * @param color  默认颜色（ARGB；透明度低于 4 的按不透明画，同原版）
     * @param shadow 是否带阴影
     * @return 结束 x（带阴影时 +1）
     */
    public int drawString(@Nonnull Font font, @Nullable String text, int x, int y, int color, boolean shadow) {
        if (text == null) {
            return 0;
        }
        List<TextRun> runs = new ArrayList<>();
        Component.collectFormatted(text, Style.EMPTY, runs);
        return drawRuns(font, runs, x, y, color, shadow);
    }

    public int drawString(@Nonnull Font font, @Nonnull Component text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(@Nonnull Font font, @Nonnull Component text, int x, int y, int color, boolean shadow) {
        return drawRuns(font, text.runs(), x, y, color, shadow);
    }

    /**
     * 画一行已排版文字（默认带阴影，同 1.20.1 drawString(Font, FormattedCharSequence, int, int, int)）
     */
    public int drawString(@Nonnull Font font, @Nonnull FormattedCharSequence text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    /**
     * 画一行已排版文字（同 1.20.1 drawString(Font, FormattedCharSequence, int, int, int, boolean)）
     *
     * @param font   字体
     * @param text   一行
     * @param x      左
     * @param y      上
     * @param color  默认颜色（ARGB；段落自己带颜色的按段落颜色画）
     * @param shadow 是否带阴影
     * @return 结束 x（带阴影时 +1）
     */
    public int drawString(@Nonnull Font font, @Nonnull FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        return drawRuns(font, text.runs(), x, y, color, shadow);
    }

    /**
     * 居中画一行已排版文字（带阴影，同 1.20.1 drawCenteredString(Font, FormattedCharSequence, ...)）
     */
    public void drawCenteredString(@Nonnull Font font, @Nonnull FormattedCharSequence text, int centerX, int y, int color) {
        drawRuns(font, text.runs(), centerX - font.width(text) / 2, y, color, true);
    }

    public void drawCenteredString(@Nonnull Font font, @Nonnull String text, int centerX, int y, int color) {
        drawString(font, text, centerX - font.width(text) / 2, y, color);
    }

    public void drawCenteredString(@Nonnull Font font, @Nonnull Component text, int centerX, int y, int color) {
        List<TextRun> runs = text.runs();
        drawRuns(font, runs, centerX - font.width(runs) / 2, y, color, true);
    }

    /**
     * 自动换行画一段文字（行距 9、不带阴影，同 1.20.1）
     */
    public void drawWordWrap(@Nonnull Font font, @Nonnull Component text, int x, int y, int width, int color) {
        for (Component line : font.split(text, width)) {
            drawString(font, line, x, y, color, false);
            y += 9;
        }
    }

    // 物品

    /**
     * 画物品图标（16x16，跟随 {@link #pose()} 的变换）
     *
     * @param stack 物品
     * @param x     左
     * @param y     上
     */
    public void renderItem(@Nonnull ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        flush();
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        minecraft.getRenderItem().renderItemAndEffectIntoGUI(minecraft.player, stack, x, y);
        RenderHelper.disableStandardItemLighting();
        restoreAfterItem();
    }

    /**
     * 画物品图标，不带持有者（同 1.20.1 renderFakeItem）
     */
    public void renderFakeItem(@Nonnull ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        flush();
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        minecraft.getRenderItem().renderItemAndEffectIntoGUI(null, stack, x, y);
        RenderHelper.disableStandardItemLighting();
        restoreAfterItem();
    }

    /**
     * 画物品的数量、耐久条、冷却
     */
    public void renderItemDecorations(@Nonnull Font font, @Nonnull ItemStack stack, int x, int y) {
        renderItemDecorations(font, stack, x, y, null);
    }

    public void renderItemDecorations(@Nonnull Font font, @Nonnull ItemStack stack, int x, int y, @Nullable String text) {
        if (stack.isEmpty()) {
            return;
        }
        flush();
        GlStateManager.enableDepth();
        minecraft.getRenderItem().renderItemOverlayIntoGUI(font.renderer(), stack, x, y, text);
        restoreAfterItem();
    }

    /**
     * 物品渲染会打开光照、深度、混合等，画完收回到画布的默认状态
     */
    private static void restoreAfterItem() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // 提示框

    /**
     * 画提示框，每个组件一行（不自动换行；同 1.20.1 renderComponentTooltip）
     */
    public void renderComponentTooltip(@Nonnull Font font, @Nonnull List<Component> lines, int mouseX, int mouseY) {
        renderTooltip(font, lines, mouseX, mouseY);
    }

    /**
     * 画单行提示框
     */
    public void renderTooltip(@Nonnull Font font, @Nonnull Component line, int mouseX, int mouseY) {
        renderTooltip(font, Collections.singletonList(line), mouseX, mouseY);
    }

    /**
     * 画提示框（每个元素一行，通常是 font.split 的结果；位置规则同 1.20.1 默认：鼠标右上，放不下就翻到左边、往上挪）
     */
    public void renderTooltip(@Nonnull Font font, @Nonnull List<Component> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        int width = tooltipWidth(font, lines);
        int height = tooltipHeight(lines);
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + width > guiWidth) {
            x = Math.max(x - 24 - width, 4);
        }
        if (y + height + 3 > guiHeight) {
            y = guiHeight - height - 3;
        }
        renderTooltipAt(font, lines, x, y);
    }

    /**
     * 画提示框，每个元素一行（同 1.20.1 renderTooltip(Font, List&lt;FormattedCharSequence&gt;, int, int)）
     * <p>
     * 1.12.2 版：{@link #renderTooltip(Font, List, int, int)} 已经收 {@code List<Component>}，
     * 两个 List 参数擦除后签名相同、不能同名重载，所以换了个名字；位置规则、外观和它完全一样。
     * </p>
     */
    public void renderSequenceTooltip(@Nonnull Font font, @Nonnull List<FormattedCharSequence> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        List<Component> components = new ArrayList<>(lines.size());
        for (FormattedCharSequence line : lines) {
            components.add(line.toComponent());
        }
        renderTooltip(font, components, mouseX, mouseY);
    }

    /**
     * 提示框内容宽度
     */
    public static int tooltipWidth(@Nonnull Font font, @Nonnull List<Component> lines) {
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        return width;
    }

    /**
     * 提示框内容高度（一行 8；多行时第一行下面多空 2，每行 10）
     */
    public static int tooltipHeight(@Nonnull List<Component> lines) {
        return lines.size() == 1 ? 8 : lines.size() * 10;
    }

    /**
     * 在指定位置画提示框（x、y 是文字左上角，背景向外多出 4 像素）
     */
    public void renderTooltipAt(@Nonnull Font font, @Nonnull List<Component> lines, int x, int y) {
        if (lines.isEmpty()) {
            return;
        }
        int width = tooltipWidth(font, lines);
        int height = tooltipHeight(lines);
        int left = x - 3;
        int top = y - 3;
        int boxWidth = width + 6;
        int boxHeight = height + 6;
        fill(left, top - 1, left + boxWidth, top, TOOLTIP_BACKGROUND);
        fill(left, top + boxHeight, left + boxWidth, top + boxHeight + 1, TOOLTIP_BACKGROUND);
        fill(left, top, left + boxWidth, top + boxHeight, TOOLTIP_BACKGROUND);
        fill(left - 1, top, left, top + boxHeight, TOOLTIP_BACKGROUND);
        fill(left + boxWidth, top, left + boxWidth + 1, top + boxHeight, TOOLTIP_BACKGROUND);
        fillGradient(left, top + 1, left + 1, top + boxHeight - 1, TOOLTIP_BORDER_TOP, TOOLTIP_BORDER_BOTTOM);
        fillGradient(left + boxWidth - 1, top + 1, left + boxWidth, top + boxHeight - 1, TOOLTIP_BORDER_TOP, TOOLTIP_BORDER_BOTTOM);
        fill(left, top, left + boxWidth, top + 1, TOOLTIP_BORDER_TOP);
        fill(left, top + boxHeight - 1, left + boxWidth, top + boxHeight, TOOLTIP_BORDER_BOTTOM);
        int lineY = y;
        for (int i = 0; i < lines.size(); i++) {
            drawString(font, lines.get(i), x, lineY, 0xFFFFFFFF, true);
            lineY += 10 + (i == 0 ? 2 : 0);
        }
    }

    /**
     * 画物品的原版提示框（走 Forge 的 GuiUtils，其它模组对提示框的修改都会生效）
     *
     * @param font   字体
     * @param stack  物品
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     */
    public void renderTooltip(@Nonnull Font font, @Nonnull ItemStack stack, int mouseX, int mouseY) {
        if (stack.isEmpty()) {
            return;
        }
        List<String> lines = itemTooltipLines(minecraft, stack);
        flush();
        FontRenderer itemFont = stack.getItem().getFontRenderer(stack);
        GuiUtils.preItemToolTip(stack);
        GuiUtils.drawHoveringText(stack, lines, mouseX, mouseY, guiWidth, guiHeight, -1, itemFont == null ? font.renderer() : itemFont);
        GuiUtils.postItemToolTip();
        restoreAfterItem();
    }

    /**
     * 物品提示框的文字（同 GuiScreen#getItemToolTip：第一行按稀有度上色，其余灰色）
     *
     * @param minecraft 客户端
     * @param stack     物品
     * @return 每行一个带 § 码的字符串
     */
    @Nonnull
    public static List<String> itemTooltipLines(@Nonnull Minecraft minecraft, @Nonnull ItemStack stack) {
        List<String> lines = stack.getTooltip(minecraft.player,
                minecraft.gameSettings.advancedItemTooltips ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL);
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                lines.set(i, stack.getItem().getForgeRarity(stack).getColor() + lines.get(i));
            } else {
                lines.set(i, TextFormatting.GRAY + lines.get(i));
            }
        }
        return lines;
    }
}
