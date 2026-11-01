package pers.roinflam.carianstyle.mc112.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 本模组用到的几套 GL 状态，对应 1.20.1 的几个 {@code RenderType}
 * <p>
 * 用法：{@code GlStateSnapshot s = GlPresets.X.enter(); ……画……; s.restore();}。只包住真正画的那一小段，
 * 不 {@code pushMatrix}，也不碰矩阵。
 * </p>
 * <ul>
 *   <li>{@link #WORLD_POSITION_COLOR}：世界里的纯色几何（1.20.1 的 position_color 着色器：没有贴图、没有雾、逐顶点渐变色）。
 *       alpha 测试要关，不然低透明度的顶点会被砍掉；雾要关，不然固定管线的雾会把远处的特效染成雾色；
 *       光照贴图要关，开着会把颜色乘暗。</li>
 *   <li>{@link #SHIELD_WARD}：护鞘。和上面一样，只是 alpha 通道的混合因子是 {@code ONE, ONE_MINUS_SRC_ALPHA}
 *       （照 1.20.1 护鞘 RenderType 的 TRANSLUCENT_TRANSPARENCY）。</li>
 *   <li>{@link #FLAME}：自定义火焰（世界里 / 第三人称）。方块图集贴图、cutout（alpha &gt; 0.1）、全亮，雾不动。</li>
 *   <li>{@link #FLAME_FIRST_PERSON}：第一人称手上的火焰，不做深度测试、不写深度，永远画在手上。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class GlPresets {

    /** 世界里的纯色几何 */
    public static final GlPreset WORLD_POSITION_COLOR = new Spec()
            .fog(false).lighting(false).alphaTest(false).blend(true)
            .blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
            .cull(false).texture(false).smooth()
            .depthTest(true).depthFunc(GL11.GL_LEQUAL).depthMask(false)
            .lightmap(false).white();

    /** 护鞘 */
    public static final GlPreset SHIELD_WARD = new Spec()
            .fog(false).lighting(false).alphaTest(false).blend(true)
            .blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA)
            .cull(false).texture(false).smooth()
            .depthTest(true).depthFunc(GL11.GL_LEQUAL).depthMask(false)
            .lightmap(false).white();

    /** 自定义火焰（世界里 / 第三人称） */
    public static final GlPreset FLAME = new Spec()
            .texture(true).bindBlockAtlas()
            .alphaTest(true).alphaFunc(GL11.GL_GREATER, 0.1F)
            .blend(true).blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
            .cull(false).lighting(false).lightmap(false)
            .depthTest(true).depthFunc(GL11.GL_LEQUAL).depthMask(true)
            .white();

    /** 第一人称手上的火焰 */
    public static final GlPreset FLAME_FIRST_PERSON = new Spec()
            .texture(true).bindBlockAtlas()
            .alphaTest(true).alphaFunc(GL11.GL_GREATER, 0.1F)
            .blend(true).blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
            .cull(false).lighting(false).lightmap(false)
            .depthTest(true).depthFunc(GL11.GL_ALWAYS).depthMask(false)
            .white();

    private GlPresets() {
    }

    /**
     * 只记下当前状态、不设任何东西；{@code restore()} 会把记下的每一项都写回去
     *
     * @return 快照
     */
    @Nonnull
    public static GlStateSnapshot capture() {
        return GlStateSnapshot.capture(GlStateSnapshot.ALL);
    }

    /**
     * 预设的具体内容：每一项为 null 表示不动
     */
    private static final class Spec implements GlPreset {
        @Nullable
        private Boolean fog;
        @Nullable
        private Boolean lighting;
        @Nullable
        private Boolean alphaTest;
        @Nullable
        private Boolean blend;
        @Nullable
        private Boolean cull;
        @Nullable
        private Boolean texture;
        @Nullable
        private Boolean depthTest;
        @Nullable
        private Boolean depthMask;
        @Nullable
        private Boolean lightmap;
        @Nullable
        private Integer shadeModel;
        @Nullable
        private Integer depthFunc;
        @Nullable
        private int[] blendFunc;
        @Nullable
        private Integer alphaFunc;
        private float alphaRef;
        private boolean white;
        private boolean bindAtlas;
        private int mask;

        Spec fog(boolean v) {
            fog = v;
            mask |= GlStateSnapshot.FOG;
            return this;
        }

        Spec lighting(boolean v) {
            lighting = v;
            mask |= GlStateSnapshot.LIGHTING;
            return this;
        }

        Spec alphaTest(boolean v) {
            alphaTest = v;
            mask |= GlStateSnapshot.ALPHA_TEST;
            return this;
        }

        Spec alphaFunc(int func, float ref) {
            alphaFunc = func;
            alphaRef = ref;
            mask |= GlStateSnapshot.ALPHA_FUNC;
            return this;
        }

        Spec blend(boolean v) {
            blend = v;
            mask |= GlStateSnapshot.BLEND;
            return this;
        }

        Spec blendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
            blendFunc = new int[]{srcRgb, dstRgb, srcAlpha, dstAlpha};
            mask |= GlStateSnapshot.BLEND_FUNC;
            return this;
        }

        Spec cull(boolean v) {
            cull = v;
            mask |= GlStateSnapshot.CULL;
            return this;
        }

        Spec texture(boolean v) {
            texture = v;
            mask |= GlStateSnapshot.TEXTURE;
            return this;
        }

        /** 绑方块图集。绑定的贴图不还原：谁要画贴图都会自己先绑 */
        Spec bindBlockAtlas() {
            bindAtlas = true;
            return this;
        }

        Spec smooth() {
            shadeModel = GL11.GL_SMOOTH;
            mask |= GlStateSnapshot.SHADE_MODEL;
            return this;
        }

        Spec depthTest(boolean v) {
            depthTest = v;
            mask |= GlStateSnapshot.DEPTH_TEST;
            return this;
        }

        Spec depthFunc(int func) {
            depthFunc = func;
            mask |= GlStateSnapshot.DEPTH_FUNC;
            return this;
        }

        Spec depthMask(boolean v) {
            depthMask = v;
            mask |= GlStateSnapshot.DEPTH_MASK;
            return this;
        }

        Spec lightmap(boolean v) {
            lightmap = v;
            mask |= GlStateSnapshot.LIGHTMAP;
            return this;
        }

        Spec white() {
            white = true;
            mask |= GlStateSnapshot.COLOR;
            return this;
        }

        @Override
        public GlStateSnapshot enter() {
            GlStateSnapshot snapshot = GlStateSnapshot.capture(mask);
            if (fog != null) {
                if (fog) {
                    GlStateManager.enableFog();
                } else {
                    GlStateManager.disableFog();
                }
            }
            if (lighting != null) {
                if (lighting) {
                    GlStateManager.enableLighting();
                } else {
                    GlStateManager.disableLighting();
                }
            }
            if (alphaFunc != null) {
                GlStateManager.alphaFunc(alphaFunc, alphaRef);
            }
            if (alphaTest != null) {
                if (alphaTest) {
                    GlStateManager.enableAlpha();
                } else {
                    GlStateManager.disableAlpha();
                }
            }
            if (blendFunc != null) {
                GlStateManager.tryBlendFuncSeparate(blendFunc[0], blendFunc[1], blendFunc[2], blendFunc[3]);
            }
            if (blend != null) {
                if (blend) {
                    GlStateManager.enableBlend();
                } else {
                    GlStateManager.disableBlend();
                }
            }
            if (cull != null) {
                if (cull) {
                    GlStateManager.enableCull();
                } else {
                    GlStateManager.disableCull();
                }
            }
            if (texture != null) {
                if (texture) {
                    GlStateManager.enableTexture2D();
                } else {
                    GlStateManager.disableTexture2D();
                }
            }
            if (bindAtlas) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            }
            if (depthFunc != null) {
                GlStateManager.depthFunc(depthFunc);
            }
            if (depthTest != null) {
                if (depthTest) {
                    GlStateManager.enableDepth();
                } else {
                    GlStateManager.disableDepth();
                }
            }
            if (depthMask != null) {
                GlStateManager.depthMask(depthMask);
            }
            if (shadeModel != null) {
                GlStateManager.shadeModel(shadeModel);
            }
            if (lightmap != null) {
                if (lightmap) {
                    Minecraft.getMinecraft().entityRenderer.enableLightmap();
                } else {
                    Minecraft.getMinecraft().entityRenderer.disableLightmap();
                }
            }
            if (white) {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
            return snapshot;
        }
    }
}
