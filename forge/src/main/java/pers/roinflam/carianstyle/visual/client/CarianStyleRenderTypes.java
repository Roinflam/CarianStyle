package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 本模组自定义的 {@link RenderType}（纯客户端）。
 *
 * <h3>为什么需要这个类</h3>
 * <p>
 * {@link VisualBatch} 那条渲染路径是「在 {@code RenderLevelStageEvent} 里自己开一个
 * {@link com.mojang.blaze3d.vertex.Tesselator} 批次」，它只在世界渲染阶段可用。
 * 而把特效画到<b>手持物品</b>上时，我们处在实体渲染阶段，
 * 拿到的是原版给的 {@link net.minecraft.client.renderer.MultiBufferSource}，
 * 必须向它索取一个 {@code RenderType} 才能写顶点。
 * </p>
 * <p>
 * <b>原版没有现成的可用类型。</b>需要的组合是
 * {@code POSITION_COLOR}（无贴图、顶点色）+ {@code TRIANGLES}（三角形列表）
 * + 半透明 + 关闭背面剔除，原版的 {@code RenderType.debugTriangleFan} 之类都不满足。
 * </p>
 * <p>
 * <b>为什么必须是 TRIANGLES 而不是 QUADS：</b>本模组现有的全部几何基元
 * （{@code planeLine}、{@code ellipseDisc}、{@code ellipseRing}、{@code wall}…）
 * 都是按「每三个顶点一个三角形」写的。选 {@code QUADS} 意味着把这些基元全部重写一遍，
 * 而选 {@code TRIANGLES} 则一行都不用改——
 * 因为 {@link com.mojang.blaze3d.vertex.BufferBuilder} 本身就实现了
 * {@link com.mojang.blaze3d.vertex.VertexConsumer}，
 * 把基元的形参类型从前者放宽到后者即可两条路径通用。
 * </p>
 *
 * <h3>实现手法</h3>
 * <p>
 * {@code RenderType.create(...)} 与 {@code RenderStateShard.POSITION_COLOR_SHADER}
 * 都是 {@code protected}，只有 {@link RenderType} 的子类能访问。
 * 因此本类<b>继承 RenderType 纯粹是为了拿到访问权限</b>，
 * 从不实例化——构造函数存在只是因为 Java 要求，调用它是错误用法。
 * 这是 Minecraft 模组里注册自定义 RenderType 的标准做法。
 * </p>
 *
 * <h3>状态选择的理由</h3>
 * <ul>
 *     <li><b>TRANSLUCENT_TRANSPARENCY</b>：特效全部依赖 alpha 混合；</li>
 *     <li><b>NO_CULL</b>：护膜与石壁都是单层面片，从背面看也必须可见；</li>
 *     <li><b>COLOR_WRITE</b>（不写深度）：半透明层之间不互相遮挡，
 *         避免同一个护膜的内外层因深度写入而互相剔除出黑边；</li>
 *     <li><b>LEQUAL_DEPTH_TEST</b>：仍然接受深度测试，
 *         这样护膜会被墙体正确遮挡，不会穿墙可见；</li>
 *     <li><b>sortOnUpload = false</b>：特效层数少且绘制顺序由代码显式控制，
 *         排序反而会打乱我们刻意安排的「填充 → 流纹 → 轮廓」层次。</li>
 * </ul>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class CarianStyleRenderTypes extends RenderType {

    /**
     * 护盾类特效专用类型：无贴图顶点色三角形、半透明、双面、不写深度。
     * <p>供 {@link ShieldWardRenderer} 在物品局部空间绘制护膜 / 石壁时使用。</p>
     */
    public static final RenderType SHIELD_WARD = RenderType.create(
            "carianstyle_shield_ward",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * <b>禁止调用。</b>
     * <p>本类继承 {@link RenderType} 只为取得对 {@code protected} 的
     * {@code create} 与各 {@code RenderStateShard} 常量的访问权限，从不实例化。
     * 构造函数存在仅因 Java 语法要求。</p>
     *
     * @throws UnsupportedOperationException 恒抛出
     */
    private CarianStyleRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                                   int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                                   Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        throw new UnsupportedOperationException("CarianStyleRenderTypes 不应被实例化");
    }
}
