package pers.roinflam.carianstyle.mc112.gui;

/**
 * 接收输入的界面元素（同 1.20.1 GuiEventListener）
 * <p>
 * 坐标都是界面坐标（double，带小数）；键码是 GLFW 的值（见 {@link Keys}），扫描码是 LWJGL 2 的原始键码。
 * 鼠标键：0 = 左、1 = 右、2 = 中。滚轮：正 = 向上，一格为 1。
 */
public interface GuiEventListener {

    default void mouseMoved(double mouseX, double mouseY) {
    }

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    default boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    void setFocused(boolean focused);

    boolean isFocused();

    /**
     * Tab 键能不能把焦点切到这里（1.20.1 的 nextFocusPath 返回非 null 的情况）
     *
     * @return true = 能
     */
    default boolean canTabFocus() {
        return false;
    }

    /**
     * Tab 顺序分组（小的先）
     *
     * @return 分组
     */
    default int getTabOrderGroup() {
        return 0;
    }
}
