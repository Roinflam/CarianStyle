package pers.roinflam.carianstyle.visual.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.mc112.render.GlPreset;
import pers.roinflam.carianstyle.mc112.render.GlPresets;

/**
 * 本模组自定义的渲染状态（纯客户端）。
 *
 * <h3>为什么需要这个类</h3>
 * <p>
 * 把特效画到<b>手持物品</b>上时处在实体渲染阶段，不能借 {@link VisualBatch} 那条世界批次的路径，
 * 要单独一套状态：{@code POSITION_COLOR}（无贴图、顶点色）+ {@code TRIANGLES}（三角形列表）
 * + 半透明 + 关闭背面剔除。
 * </p>
 *
 * <h3>状态选择的理由</h3>
 * <ul>
 *     <li><b>半透明混合</b>（颜色 {@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA}，透明度 {@code ONE, ONE_MINUS_SRC_ALPHA}）：
 *         特效全部依赖 alpha 混合；</li>
 *     <li><b>不剔除</b>：护膜与石壁都是单层面片，从背面看也必须可见；</li>
 *     <li><b>只写颜色、不写深度</b>：半透明层之间不互相遮挡，
 *         避免同一个护膜的内外层因深度写入而互相剔除出黑边；</li>
 *     <li><b>深度测试 LEQUAL</b>：仍然接受深度测试，
 *         这样护膜会被墙体正确遮挡，不会穿墙可见。</li>
 * </ul>
 *
 * <h3>1.12.2 版</h3>
 * <p>
 * 1.20.1 继承 {@code RenderType} 拿到 protected 的 {@code create}，注册一个自定义渲染类型。
 * 1.12.2 没有渲染类型，状态用固定管线设：这里只留 {@link #SHIELD_WARD} 一个常量，指向
 * {@link GlPresets#SHIELD_WARD}（记下当前状态、设预设，画完 {@code restore()} 原样还原），
 * {@link ShieldWardRenderer} 的写法因此和 1.20.1 一行对一行。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@SideOnly(Side.CLIENT)
public final class CarianStyleRenderTypes {

    /**
     * 护盾类特效专用状态：无贴图顶点色三角形、半透明、双面、不写深度。
     * <p>供 {@link ShieldWardRenderer} 在物品局部空间绘制护膜 / 石壁时使用。</p>
     */
    public static final GlPreset SHIELD_WARD = GlPresets.SHIELD_WARD;

    private CarianStyleRenderTypes() {
    }
}
