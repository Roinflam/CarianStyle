package pers.roinflam.carianstyle.mc112.gui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 1.20.1 RenderSystem 里界面用到的几个方法
 * <p>
 * 1.20.1 的着色器颜色会乘到之后画的色块、文字和贴图上。这里色块是攒着按顶点上色的，所以颜色记在这里，
 * 由 {@link GuiGraphics} 在写顶点、画字、贴图时乘进去，效果和 1.20.1 一样；物品图标不受影响，同 1.20.1。
 */
@SideOnly(Side.CLIENT)
public final class RenderSystem {

    static float red = 1.0F;
    static float green = 1.0F;
    static float blue = 1.0F;
    static float alpha = 1.0F;

    private RenderSystem() {
    }

    public static void setShaderColor(float r, float g, float b, float a) {
        red = r;
        green = g;
        blue = b;
        alpha = a;
    }

    static boolean isWhite() {
        return red == 1.0F && green == 1.0F && blue == 1.0F && alpha == 1.0F;
    }

    /**
     * 把着色器颜色乘进一个 ARGB 颜色
     */
    static int modulate(int argb) {
        if (isWhite()) {
            return argb;
        }
        int a = Math.round(((argb >>> 24) & 255) * clamp(alpha));
        int r = Math.round(((argb >> 16) & 255) * clamp(red));
        int g = Math.round(((argb >> 8) & 255) * clamp(green));
        int b = Math.round((argb & 255) * clamp(blue));
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static float clamp(float v) {
        return v < 0.0F ? 0.0F : Math.min(1.0F, v);
    }

    public static void enableBlend() {
        GlStateManager.enableBlend();
    }

    public static void disableBlend() {
        GlStateManager.disableBlend();
    }

    public static void defaultBlendFunc() {
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
    }
}
