package pers.roinflam.carianstyle.mc112.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import javax.annotation.Nonnull;
import java.nio.FloatBuffer;

/**
 * 一次 GL 状态快照
 * <p>
 * 记的是真实的 GL 状态（{@code glIsEnabled} / {@code glGet*}），不是 {@code GlStateManager} 的缓存：
 * {@code RenderWorldLastEvent} 也有别的模组订阅，进来时的状态不能当成定值。
 * 还原只改预设动过的那几项，而且一律走 {@code GlStateManager}（光照贴图走 {@code EntityRenderer}），
 * 不用 {@code glPushAttrib / glPopAttrib}：那两个绕过 {@code GlStateManager} 的缓存，缓存和真实状态一错位，
 * 后面别人调 {@code GlStateManager.enableX} 会被当成「已经开着」跳过。
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class GlStateSnapshot {

    static final int FOG = 1;
    static final int LIGHTING = 1 << 1;
    static final int ALPHA_TEST = 1 << 2;
    static final int BLEND = 1 << 3;
    static final int CULL = 1 << 4;
    static final int TEXTURE = 1 << 5;
    static final int DEPTH_TEST = 1 << 6;
    static final int DEPTH_MASK = 1 << 7;
    static final int SHADE_MODEL = 1 << 8;
    static final int DEPTH_FUNC = 1 << 9;
    static final int BLEND_FUNC = 1 << 10;
    static final int LIGHTMAP = 1 << 11;
    static final int COLOR = 1 << 12;
    static final int ALPHA_FUNC = 1 << 13;
    static final int ALL = (1 << 14) - 1;

    /** glGetFloat 要 16 个元素的缓冲 */
    private static final FloatBuffer COLOR_BUFFER = BufferUtils.createFloatBuffer(16);

    private final boolean fog;
    private final boolean lighting;
    private final boolean alphaTest;
    private final boolean blend;
    private final boolean cull;
    private final boolean texture;
    private final boolean depthTest;
    private final boolean depthMask;
    private final int shadeModel;
    private final int depthFunc;
    private final int blendSrcRgb;
    private final int blendDstRgb;
    private final int blendSrcAlpha;
    private final int blendDstAlpha;
    private final int alphaFunc;
    private final float alphaRef;
    private final boolean lightmap;
    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    /** 预设改过的项；只还原这些 */
    private final int changed;

    private GlStateSnapshot(int changed) {
        this.changed = changed;
        fog = GL11.glIsEnabled(GL11.GL_FOG);
        lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        shadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        // 光照贴图在第二个贴图单元上，查完切回默认单元
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        lightmap = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        COLOR_BUFFER.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, COLOR_BUFFER);
        red = COLOR_BUFFER.get(0);
        green = COLOR_BUFFER.get(1);
        blue = COLOR_BUFFER.get(2);
        alpha = COLOR_BUFFER.get(3);
    }

    /**
     * 记下当前状态
     *
     * @param changedMask 之后会被改动、还原时要写回的项
     * @return 快照
     */
    @Nonnull
    static GlStateSnapshot capture(int changedMask) {
        return new GlStateSnapshot(changedMask);
    }

    /**
     * 把预设改过的项还原成快照里的值
     */
    public void restore() {
        int c = changed;
        if ((c & FOG) != 0) {
            if (fog) {
                GlStateManager.enableFog();
            } else {
                GlStateManager.disableFog();
            }
        }
        if ((c & LIGHTING) != 0) {
            if (lighting) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
        }
        if ((c & ALPHA_FUNC) != 0) {
            GlStateManager.alphaFunc(alphaFunc, alphaRef);
        }
        if ((c & ALPHA_TEST) != 0) {
            if (alphaTest) {
                GlStateManager.enableAlpha();
            } else {
                GlStateManager.disableAlpha();
            }
        }
        if ((c & BLEND_FUNC) != 0) {
            GlStateManager.tryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        }
        if ((c & BLEND) != 0) {
            if (blend) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
        }
        if ((c & CULL) != 0) {
            if (cull) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
        }
        if ((c & TEXTURE) != 0) {
            if (texture) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
        }
        if ((c & DEPTH_FUNC) != 0) {
            GlStateManager.depthFunc(depthFunc);
        }
        if ((c & DEPTH_TEST) != 0) {
            if (depthTest) {
                GlStateManager.enableDepth();
            } else {
                GlStateManager.disableDepth();
            }
        }
        if ((c & DEPTH_MASK) != 0) {
            GlStateManager.depthMask(depthMask);
        }
        if ((c & SHADE_MODEL) != 0) {
            GlStateManager.shadeModel(shadeModel);
        }
        if ((c & LIGHTMAP) != 0) {
            if (lightmap) {
                Minecraft.getMinecraft().entityRenderer.enableLightmap();
            } else {
                Minecraft.getMinecraft().entityRenderer.disableLightmap();
            }
        }
        if ((c & COLOR) != 0) {
            GlStateManager.color(red, green, blue, alpha);
        }
    }
}
