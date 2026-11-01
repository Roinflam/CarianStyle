package pers.roinflam.carianstyle.mc112.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 界面基类：在 1.12.2 的 GuiScreen 上套出 1.20.1 Screen 的写法
 * <p>
 * <ul>
 *   <li>生命周期：{@link #init()}（打开、窗口大小变化时都会先清空控件再调）、{@link #tick()}（每 tick）、
 *   {@link #render}（每帧）、{@link #removed()}（被别的界面替换或关闭时）、{@link #onClose()}（按 Esc）；</li>
 *   <li>输入：LWJGL 2 的事件换成 1.20.1 的回调——坐标是带小数的界面坐标，键码换成 GLFW 的值（{@link Keys}），
 *   扫描码给 LWJGL 原始键码，修饰键给 {@link Keys#MOD_SHIFT} 等位；按住鼠标键移动产生拖动，滚轮一格 = 1；</li>
 *   <li>分发规则同 1.20.1：点击交给第一个接住的子控件并让它获得焦点（点空白处焦点不变），
 *   松开、滚轮给鼠标下面的控件，拖动只给拿着焦点的控件，键盘和字符给焦点控件；没人处理的 Tab 切焦点。</li>
 * </ul>
 * 不要再覆写 GuiScreen 的 initGui / drawScreen / keyTyped / mouseClicked(int...) 这些，它们已经被接管了。
 */
public abstract class Screen extends GuiScreen {

    /**
     * 最近一次输入是不是键盘（控件焦点提示要用；1.20.1 的 Minecraft#getLastInputType）
     */
    private static boolean keyboardLastInput;

    @Nonnull
    protected final Component title;
    @Nullable
    protected final GuiScreen parent;
    protected Minecraft minecraft;
    protected Font font;

    private final List<GuiEventListener> children = new ArrayList<>();
    private final List<Renderable> renderables = new ArrayList<>();
    @Nullable
    private GuiEventListener focused;
    private boolean dragging;

    /**
     * 鼠标状态：按住的键（-1 = 没按）和上一个事件的位置（算拖动距离）
     */
    private int heldButton = -1;
    private double lastMouseX;
    private double lastMouseY;

    @Nullable
    private List<Component> deferredTooltip;
    @Nullable
    private TooltipPositioner deferredPositioner;

    @Nullable
    private GuiGraphics graphics;

    protected Screen(@Nonnull Component title) {
        this(title, null);
    }

    /**
     * @param title  标题
     * @param parent 关闭时回到的界面（null = 回到游戏）
     */
    protected Screen(@Nonnull Component title, @Nullable GuiScreen parent) {
        this.title = title;
        this.parent = parent;
        this.minecraft = Minecraft.getMinecraft();
        this.font = Font.get();
    }

    @Nonnull
    public Component getTitle() {
        return title;
    }

    // 生命周期（子类覆写这些）

    /**
     * 创建控件（打开和窗口大小变化时调用，调之前控件和焦点都已清空）
     */
    protected void init() {
    }

    /**
     * 每 tick 调一次
     */
    public void tick() {
    }

    /**
     * 每帧画界面；默认按添加顺序画所有可画的控件
     */
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        for (Renderable renderable : renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
    }

    /**
     * 界面被替换或关闭时调用
     */
    public void removed() {
    }

    /**
     * 按 Esc 时调用：默认回到 parent
     */
    public void onClose() {
        minecraft.displayGuiScreen(parent);
    }

    public boolean shouldCloseOnEsc() {
        return true;
    }

    /**
     * 单人游戏里打开时要不要暂停
     */
    public boolean isPauseScreen() {
        return true;
    }

    /**
     * 原版的暗色背景（世界里是半透明渐变，主菜单是泥土）
     */
    public void renderBackground(@Nonnull GuiGraphics g) {
        g.flush();
        drawDefaultBackground();
    }

    // 控件

    @Nonnull
    protected <T extends GuiEventListener & Renderable> T addRenderableWidget(@Nonnull T widget) {
        renderables.add(widget);
        children.add(widget);
        return widget;
    }

    @Nonnull
    protected <T extends Renderable> T addRenderableOnly(@Nonnull T renderable) {
        renderables.add(renderable);
        return renderable;
    }

    @Nonnull
    protected <T extends GuiEventListener> T addWidget(@Nonnull T listener) {
        children.add(listener);
        return listener;
    }

    protected void removeWidget(@Nonnull GuiEventListener listener) {
        if (listener instanceof Renderable) {
            renderables.remove(listener);
        }
        children.remove(listener);
        if (focused == listener) {
            focused = null;
        }
    }

    protected void clearWidgets() {
        renderables.clear();
        children.clear();
    }

    /**
     * 子控件（按添加顺序，点击时也按这个顺序找）
     */
    @Nonnull
    public List<? extends GuiEventListener> children() {
        return Collections.unmodifiableList(children);
    }

    @Nullable
    public GuiEventListener getFocused() {
        return focused;
    }

    /**
     * 把焦点交给某个控件（旧的失去焦点）；null = 谁都不要焦点
     */
    public void setFocused(@Nullable GuiEventListener listener) {
        if (focused != null) {
            focused.setFocused(false);
        }
        if (listener != null) {
            listener.setFocused(true);
        }
        focused = listener;
    }

    public void clearFocus() {
        setFocused(null);
    }

    public boolean isDragging() {
        return dragging;
    }

    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    /**
     * 鼠标下面的第一个控件
     */
    @Nonnull
    public Optional<GuiEventListener> getChildAt(double mouseX, double mouseY) {
        for (GuiEventListener child : children) {
            if (child.isMouseOver(mouseX, mouseY)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    // 输入回调（1.20.1 语义，子类可覆写）

    public void mouseMoved(double mouseX, double mouseY) {
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (GuiEventListener child : new ArrayList<>(children)) {
            if (child.mouseClicked(mouseX, mouseY, button)) {
                setFocused(child);
                if (button == 0) {
                    setDragging(true);
                }
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        setDragging(false);
        Optional<GuiEventListener> child = getChildAt(mouseX, mouseY);
        return child.isPresent() && child.get().mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return focused != null && dragging && button == 0 && focused.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Optional<GuiEventListener> child = getChildAt(mouseX, mouseY);
        return child.isPresent() && child.get().mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * 按键：Esc 关闭 → 焦点控件 → Tab / Shift+Tab 切焦点（方向键切焦点没有移植）
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keys.KEY_ESCAPE && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == Keys.KEY_TAB) {
            navigateTab(!hasShiftDown());
        }
        return false;
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return focused != null && focused.keyReleased(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char c, int modifiers) {
        return focused != null && focused.charTyped(c, modifiers);
    }

    /**
     * Tab 切焦点（同 1.20.1：按 tabOrderGroup 稳定排序，从当前焦点往后找第一个能接的，到头了从另一头再找一遍）
     *
     * @param forward true = 往后
     */
    protected void navigateTab(boolean forward) {
        List<GuiEventListener> sorted = new ArrayList<>(children);
        sorted.sort(Comparator.comparingInt(GuiEventListener::getTabOrderGroup));
        GuiEventListener next = findTabTarget(sorted, forward, focused);
        if (next == null) {
            clearFocus();
            next = findTabTarget(sorted, forward, null);
        }
        if (next != null) {
            setFocused(next);
        }
    }

    @Nullable
    private static GuiEventListener findTabTarget(@Nonnull List<GuiEventListener> sorted, boolean forward, @Nullable GuiEventListener current) {
        int index = current == null ? -1 : sorted.indexOf(current);
        int start;
        if (index >= 0) {
            start = index + (forward ? 1 : 0);
        } else {
            start = forward ? 0 : sorted.size();
        }
        if (forward) {
            for (int i = start; i < sorted.size(); i++) {
                if (sorted.get(i).canTabFocus()) {
                    return sorted.get(i);
                }
            }
        } else {
            for (int i = start - 1; i >= 0; i--) {
                if (sorted.get(i).canTabFocus()) {
                    return sorted.get(i);
                }
            }
        }
        return null;
    }

    // 提示框

    /**
     * 这一帧画完后在鼠标处画提示框（一帧只画一个；已经有了就不替换）
     */
    public void setTooltipForNextRenderPass(@Nonnull List<Component> lines) {
        setTooltipForNextRenderPass(lines, TooltipPositioner.DEFAULT, true);
    }

    /**
     * @param lines      每行一个组件
     * @param positioner 摆放规则
     * @param override   true = 已经有提示也替换掉
     */
    public void setTooltipForNextRenderPass(@Nonnull List<Component> lines, @Nonnull TooltipPositioner positioner, boolean override) {
        if (deferredTooltip == null || override) {
            deferredTooltip = lines;
            deferredPositioner = positioner;
        }
    }

    public void setTooltipForNextRenderPass(@Nonnull Tooltip tooltip, @Nonnull TooltipPositioner positioner, boolean override) {
        setTooltipForNextRenderPass(tooltip.lines(), positioner, override);
    }

    /**
     * 一段文字的提示（按界面一半宽、至少 200 像素换行，同 1.20.1）
     */
    public void setTooltipForNextRenderPass(@Nonnull Component text) {
        setTooltipForNextRenderPass(font.split(text, Math.max(width / 2, 200)), TooltipPositioner.DEFAULT, true);
    }

    // 修饰键

    public static boolean hasControlDown() {
        return GuiScreen.isCtrlKeyDown();
    }

    public static boolean hasShiftDown() {
        return GuiScreen.isShiftKeyDown();
    }

    public static boolean hasAltDown() {
        return GuiScreen.isAltKeyDown();
    }

    public static boolean isCut(int keyCode) {
        return keyCode == Keys.KEY_X && hasControlDown() && !hasShiftDown() && !hasAltDown();
    }

    public static boolean isPaste(int keyCode) {
        return keyCode == Keys.KEY_V && hasControlDown() && !hasShiftDown() && !hasAltDown();
    }

    public static boolean isCopy(int keyCode) {
        return keyCode == Keys.KEY_C && hasControlDown() && !hasShiftDown() && !hasAltDown();
    }

    public static boolean isSelectAll(int keyCode) {
        return keyCode == Keys.KEY_A && hasControlDown() && !hasShiftDown() && !hasAltDown();
    }

    /**
     * 最近一次输入是不是键盘
     */
    public static boolean isKeyboardLastInput() {
        return keyboardLastInput;
    }

    // 接管 GuiScreen

    @Override
    public void setWorldAndResolution(@Nonnull Minecraft mc, int width, int height) {
        this.minecraft = mc;
        this.font = Font.get();
        super.setWorldAndResolution(mc, width, height);
    }

    @Override
    public final void initGui() {
        Keyboard.enableRepeatEvents(true);
        clearFocus();
        clearWidgets();
        dragging = false;
        heldButton = -1;
        init();
    }

    @Override
    public final void updateScreen() {
        tick();
    }

    @Override
    public final void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GuiGraphics g = graphics;
        if (g == null) {
            g = new GuiGraphics(minecraft);
            graphics = g;
        }
        g.begin();
        try {
            render(g, mouseX, mouseY, partialTicks);
            List<Component> tooltip = deferredTooltip;
            TooltipPositioner positioner = deferredPositioner;
            if (tooltip != null && positioner != null && !tooltip.isEmpty()) {
                int w = GuiGraphics.tooltipWidth(font, tooltip);
                int h = GuiGraphics.tooltipHeight(tooltip);
                int[] at = positioner.position(width, height, mouseX, mouseY, w, h);
                g.renderTooltipAt(font, tooltip, at[0], at[1]);
            }
        } finally {
            deferredTooltip = null;
            deferredPositioner = null;
            g.end();
        }
    }

    @Override
    public final void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        removed();
    }

    @Override
    public final boolean doesGuiPauseGame() {
        return isPauseScreen();
    }

    /**
     * 输入框拿着焦点时返回 true：原版据此决定 Ctrl+B 要不要切换旁白（打字时不切）
     */
    @Override
    public boolean isFocused() {
        GuiEventListener current = focused;
        return current instanceof EditBox && ((EditBox) current).canConsumeInput() || super.isFocused();
    }

    @Override
    public void handleMouseInput() throws IOException {
        Minecraft mc = minecraft;
        if (mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return;
        }
        double mouseX = Mouse.getEventX() * (double) width / (double) mc.displayWidth;
        double mouseY = (mc.displayHeight - 1 - Mouse.getEventY()) * (double) height / (double) mc.displayHeight;
        int button = Mouse.getEventButton();
        if (button != -1) {
            keyboardLastInput = false;
            if (Mouse.getEventButtonState()) {
                heldButton = button;
                mouseClicked(mouseX, mouseY, button);
            } else {
                if (button == heldButton) {
                    heldButton = -1;
                }
                mouseReleased(mouseX, mouseY, button);
            }
        } else {
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) {
                // Windows 上一格是 120，有的平台一格只报 1
                double delta = Math.abs(wheel) >= 120 ? wheel / 120.0D : Math.signum(wheel);
                mouseScrolled(mouseX, mouseY, delta);
            }
            if (mouseX != lastMouseX || mouseY != lastMouseY) {
                keyboardLastInput = false;
                mouseMoved(mouseX, mouseY);
                if (heldButton != -1) {
                    mouseDragged(mouseX, mouseY, heldButton, mouseX - lastMouseX, mouseY - lastMouseY);
                }
            }
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    /**
     * 键盘事件：先按 GLFW 键码给 keyPressed / keyReleased，再把可打印字符给 charTyped（两者独立，同 1.20.1）
     * <p>
     * 界面打开时原版只通过这里收键盘：F1（隐藏界面）、F3（调试）、F4 都只到这里，没有原版效果；
     * F2 截图、F11 全屏、Ctrl+B 旁白由最后的 dispatchKeypresses 处理，同时也会传到这里。
     */
    @Override
    public void handleKeyboardInput() throws IOException {
        int key = Keyboard.getEventKey();
        char c = Keyboard.getEventCharacter();
        boolean down = Keyboard.getEventKeyState();
        int modifiers = Keys.currentModifiers();
        keyboardLastInput = true;
        if (key != 0) {
            int keyCode = Keys.fromLwjgl(key);
            if (down) {
                keyPressed(keyCode, key, modifiers);
            } else {
                keyReleased(keyCode, key, modifiers);
            }
        }
        if ((down || key == 0) && c >= ' ' && c != 127 && minecraft.currentScreen == this) {
            charTyped(c, modifiers);
        }
        minecraft.dispatchKeypresses();
    }
}
