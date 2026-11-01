package pers.roinflam.carianstyle.mc112.gui;

import net.minecraft.client.renderer.GlStateManager;

import javax.annotation.Nonnull;

/**
 * 变换栈（同 1.20.1 GuiGraphics#pose() 的用法）
 * <p>
 * 直接改 GL 的模型视图矩阵；每次变换前先把缓冲里的色块画出去，否则它们会套上新的变换。
 * 只影响画出来的位置，不影响裁剪区（裁剪区按界面坐标算，同 1.20.1）。
 */
public final class PoseStack {

    @Nonnull
    private final GuiGraphics graphics;
    private int depth;

    PoseStack(@Nonnull GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public void pushPose() {
        graphics.flush();
        GlStateManager.pushMatrix();
        depth++;
    }

    public void popPose() {
        if (depth <= 0) {
            return;
        }
        graphics.flush();
        GlStateManager.popMatrix();
        depth--;
    }

    public void translate(double x, double y, double z) {
        graphics.flush();
        GlStateManager.translate(x, y, z);
    }

    public void scale(float x, float y, float z) {
        graphics.flush();
        GlStateManager.scale(x, y, z);
    }

    /**
     * 绕 Z 轴旋转（屏幕平面内，角度制，正 = 顺时针）
     *
     * @param degrees 角度
     */
    public void rotateZ(float degrees) {
        graphics.flush();
        GlStateManager.rotate(degrees, 0.0F, 0.0F, 1.0F);
    }

    /**
     * 帧末把没配对的 push 全部弹掉，避免把矩阵栈漏给原版
     */
    void popAll() {
        while (depth > 0) {
            popPose();
        }
    }
}
