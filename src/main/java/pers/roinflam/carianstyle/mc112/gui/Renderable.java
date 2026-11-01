package pers.roinflam.carianstyle.mc112.gui;

import javax.annotation.Nonnull;

/**
 * 能画出来的东西（同 1.20.1 Renderable）
 */
public interface Renderable {

    /**
     * @param g           画布
     * @param mouseX      鼠标 X
     * @param mouseY      鼠标 Y
     * @param partialTick 帧间插值
     */
    void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick);
}
