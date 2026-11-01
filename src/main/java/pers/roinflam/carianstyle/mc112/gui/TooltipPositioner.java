package pers.roinflam.carianstyle.mc112.gui;

import pers.roinflam.carianstyle.mc112.math.Mth;
import javax.annotation.Nonnull;

/**
 * 提示框摆放规则（同 1.20.1 ClientTooltipPositioner）
 */
public interface TooltipPositioner {

    /**
     * 默认：鼠标右上方，右边放不下翻到左边，下边放不下往上挪
     */
    TooltipPositioner DEFAULT = (screenWidth, screenHeight, mouseX, mouseY, width, height) -> {
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + width > screenWidth) {
            x = Math.max(x - 24 - width, 4);
        }
        if (y + height + 3 > screenHeight) {
            y = screenHeight - height - 3;
        }
        return new int[]{x, y};
    };

    /**
     * @param screenWidth  界面宽
     * @param screenHeight 界面高
     * @param mouseX       鼠标 X
     * @param mouseY       鼠标 Y
     * @param width        提示框内容宽
     * @param height       提示框内容高
     * @return {x, y}：文字左上角
     */
    @Nonnull
    int[] position(int screenWidth, int screenHeight, int mouseX, int mouseY, int width, int height);

    /**
     * 鼠标悬停在控件上时用：贴着控件上下摆（同 1.20.1 MenuTooltipPositioner）
     *
     * @param widget 控件
     * @return 摆放规则
     */
    @Nonnull
    static TooltipPositioner menu(@Nonnull AbstractWidget widget) {
        return (screenWidth, screenHeight, mouseX, mouseY, width, height) -> {
            int x = mouseX + 12;
            int y = mouseY;
            if (x + width > screenWidth - 5) {
                x = Math.max(mouseX - 12 - width, 9);
            }
            y += 3;
            int boxHeight = height + 3 + 3;
            int below = widget.getY() + widget.getHeight() + 3 + menuOffset(0, 0, widget.getHeight());
            int limit = screenHeight - 5;
            if (below + boxHeight <= limit) {
                y += menuOffset(y, widget.getY(), widget.getHeight());
            } else {
                y -= boxHeight + menuOffset(y, widget.getY() + widget.getHeight(), widget.getHeight());
            }
            return new int[]{x, y};
        };
    }

    /**
     * 键盘把焦点切到控件上时用：放在控件正下方，放不下就放上方（同 1.20.1 BelowOrAboveWidgetTooltipPositioner）
     *
     * @param widget 控件
     * @return 摆放规则
     */
    @Nonnull
    static TooltipPositioner belowOrAbove(@Nonnull AbstractWidget widget) {
        return (screenWidth, screenHeight, mouseX, mouseY, width, height) -> {
            int x = widget.getX() + 3;
            int y = widget.getY() + widget.getHeight() + 3 + 1;
            if (y + height + 3 > screenHeight) {
                y = widget.getY() - height - 3 - 1;
            }
            if (x + width > screenWidth) {
                x = Math.max(widget.getX() + widget.getWidth() - width - 3, 4);
            }
            return new int[]{x, y};
        };
    }

    static int menuOffset(int a, int b, int height) {
        if (height <= 0) {
            return 0;
        }
        int distance = Math.min(Math.abs(a - b), height);
        return Math.round(Mth.lerp((float) distance / (float) height, (float) (height - 3), 5.0F));
    }
}
