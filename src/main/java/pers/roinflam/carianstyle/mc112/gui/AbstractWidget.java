package pers.roinflam.carianstyle.mc112.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.GuiScreen;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 控件基类（同 1.20.1 AbstractWidget，去掉了旁白和贴图按钮的部分）
 * <p>
 * 回调的形状和 1.20.1 一样：{@link #render} 先按鼠标位置算悬停（不看 active），再调 {@link #renderWidget}，最后处理提示；
 * 左键按下在控件内时放点击音并调 {@link #onClick}；松开、拖动只认左键。
 */
public abstract class AbstractWidget implements Renderable, GuiEventListener {

    protected int width;
    protected int height;
    private int x;
    private int y;
    @Nonnull
    private Component message;
    protected boolean isHovered;
    public boolean active = true;
    public boolean visible = true;
    protected float alpha = 1.0F;
    private int tabOrderGroup;
    private boolean focused;
    @Nullable
    private Tooltip tooltip;
    private int tooltipMsDelay;
    private long hoverOrFocusedStartTime;
    private boolean wasHoveredOrFocused;

    public AbstractWidget(int x, int y, int width, int height, @Nonnull Component message) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.message = message;
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (visible) {
            isHovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;
            renderWidget(g, mouseX, mouseY, partialTick);
            updateTooltip();
        }
    }

    private void updateTooltip() {
        if (tooltip == null) {
            return;
        }
        boolean shown = isHovered || isFocused() && Screen.isKeyboardLastInput();
        if (shown != wasHoveredOrFocused) {
            if (shown) {
                hoverOrFocusedStartTime = Ui.millis();
            }
            wasHoveredOrFocused = shown;
        }
        if (shown && Ui.millis() - hoverOrFocusedStartTime > (long) tooltipMsDelay) {
            GuiScreen current = Minecraft.getMinecraft().currentScreen;
            if (current instanceof Screen) {
                ((Screen) current).setTooltipForNextRenderPass(tooltip, createTooltipPositioner(), isFocused());
            }
        }
    }

    @Nonnull
    protected TooltipPositioner createTooltipPositioner() {
        return !isHovered && isFocused() && Screen.isKeyboardLastInput()
                ? TooltipPositioner.belowOrAbove(this) : TooltipPositioner.menu(this);
    }

    public void setTooltip(@Nullable Tooltip tooltip) {
        this.tooltip = tooltip;
    }

    @Nullable
    public Tooltip getTooltip() {
        return tooltip;
    }

    public void setTooltipDelay(int delay) {
        this.tooltipMsDelay = delay;
    }

    protected abstract void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick);

    public void onClick(double mouseX, double mouseY) {
    }

    public void onRelease(double mouseX, double mouseY) {
    }

    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible) {
            return false;
        }
        if (isValidClickButton(button) && clicked(mouseX, mouseY)) {
            playDownSound(Minecraft.getMinecraft().getSoundHandler());
            onClick(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isValidClickButton(button)) {
            onRelease(mouseX, mouseY);
            return true;
        }
        return false;
    }

    protected boolean isValidClickButton(int button) {
        return button == 0;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isValidClickButton(button)) {
            onDrag(mouseX, mouseY, dragX, dragY);
            return true;
        }
        return false;
    }

    protected boolean clicked(double mouseX, double mouseY) {
        return active && visible && mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return active && visible && mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;
    }

    /**
     * 点击音（子类可以改成不响）
     *
     * @param handler 声音管理器
     */
    public void playDownSound(@Nonnull SoundHandler handler) {
        Ui.playClick(handler);
    }

    @Override
    public boolean canTabFocus() {
        return active && visible && !isFocused();
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * 1.12.2 这边补的：1.20.1 的 height 只能在子类里改
     */
    public void setHeight(int height) {
        this.height = height;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public void setMessage(@Nonnull Component message) {
        this.message = message;
    }

    @Nonnull
    public Component getMessage() {
        return message;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public boolean isHovered() {
        return isHovered;
    }

    public boolean isHoveredOrFocused() {
        return isHovered() || isFocused();
    }

    public boolean isActive() {
        return visible && active;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int getTabOrderGroup() {
        return tabOrderGroup;
    }

    public void setTabOrderGroup(int group) {
        this.tabOrderGroup = group;
    }
}
