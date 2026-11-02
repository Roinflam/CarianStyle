package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * PORT-STUB(V3)
 *
 * 把一件物品的模型烘焙成「护鞘几何」：真实轮廓、按距离场分级的面片、以及轮廓边段。
 * <p>
 * 每个 {@link BakedModel} 只烘焙一次，结果由 {@link #of} 缓存。
 * 烘焙本身较重（要跑一遍光栅化与灌水），但绝不在每帧路径上。
 * {@link ShieldWardRenderer} 每帧只是遍历烘焙好的数组填顶点。
 * </p>
 *
 * <h2>为什么需要这一整套，而不是一个包围盒</h2>
 * <p>
 * 包围盒是长方体。真实盾牌是上圆下尖的，用方盒套上去，
 * 底部收窄的两侧会露出大片空白护鞘。要贴合就必须拿到<b>轮廓</b>而不是<b>范围</b>。
 * </p>
 *
 * <h2>管线</h2>
 * <ol>
 *     <li><b>取三角形</b>——JSON 模型走 {@link BakedModel#getQuads}；
 *         BEWLR 走假缓冲截取（见下）。两条路合流成同一批三角形；</li>
 *     <li><b>光栅化</b>——面朝 Z 的三角形按面积扫成「占用图」，
 *         不朝 Z 的（侧壁）沿边做 DDA 扫成「墙图」；</li>
 *     <li><b>灌水</b>——从栅格边界灌，被墙挡住。灌不到 ∩ 占用 = <b>真实轮廓</b>；</li>
 *     <li><b>距离场</b>——BFS 求每格到轮廓外的距离，供「边缘实、中心透」用；</li>
 *     <li><b>贪心合并</b>——把距离场量化成若干档，同档相邻格合并成大矩形。
 *         边缘那圈档位变化密、格子小，内部大片合成几块，
 *         <b>细节留在需要的地方</b>；</li>
 *     <li><b>轮廓追踪</b>——轮廓格与外部相邻的那条边收集起来，共线的合并成长段。</li>
 * </ol>
 *
 * <h2>BEWLR 截取</h2>
 * <p>
 * {@code getQuads()} 对 BEWLR 模型返回空表——几何在 {@code ModelPart} 里，不是 {@code BakedQuad}。
 * 但 {@code renderByItem} 是公开入口：传一个只记录不绘制的 {@link CaptureBuffer}，
 * 让它画一次就能把顶点全收下来。
 * </p>
 * <p>
 * <b>传进去的 PoseStack 必须是单位矩阵。</b>这样收到的坐标就是
 * 「{@code applyTransform} + {@code translate(-0.5,-0.5,-0.5)} 之后」那个空间里的最终坐标，
 * 已经包含 BEWLR 自己做的一切变换（原版盾牌那句 {@code scale(1,-1,-1)} 也在内）。
 * 这条恰好绕开了「某个模组的 BEWLR 有没有照抄那句 scale」这个从外部无法判断的问题。
 * </p>
 * <p>
 * <b>假画布不能对所有渲染类型返回同一个实例。</b>附魔物品渲染时会走
 * {@code VertexMultiConsumer.create(附魔光效缓冲, 本体缓冲)}，
 * 而该方法一旦发现两个参数是同一个对象就直接抛
 * {@code IllegalArgumentException: Duplicate delegates}。
 * 本特效只对附魔盾生效，所以这一条<b>百分之百会触发</b>——
 * v6.0 就栽在这里，原版盾与塔盾的截取从未成功过，一直在吃兜底方盒。
 * 现在按渲染类型各发一个 {@link Recorder}。
 * </p>
 * <p>
 * 副作用是附魔物品的顶点会被记录两遍（光效一遍、本体一遍）。
 * 三角形数翻倍，但包围盒与轮廓完全相同，且烘焙只跑一次，不值得为它去重。
 * </p>
 * <p>
 * <b>这是在跑别人的代码</b>，理论上对方可能在 {@code renderByItem} 里做别的事。
 * 因此整段用 try/catch 包住，任何异常都退回包围盒，绝不让特效弄崩渲染。
 * </p>
 *
 * <h2>为什么侧壁不能按面积光栅化</h2>
 * <p>
 * {@code builtin/generated}（2D 精灵物品）的正面是<b>一整块铺满的矩形</b>——
 * 轮廓不在几何里而在贴图 alpha 里。真正描出轮廓的是
 * {@code ItemModelGenerator} 沿 alpha 边界生成的<b>侧壁</b>。
 * </p>
 * <p>
 * 而侧壁是垂直于 XY 平面的，投影下来是<b>一条线</b>，面积为零。
 * 按面积扫会一格都扫不到。所以侧壁必须沿边做 DDA 逐格标记。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class ShieldWardShape {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG = false;
    private static final int RES = 18;
    private static final int INNER = RES - 2;
    private static final int ALPHA_BANDS = 5;
    public static final int RECT_STRIDE = 8;
    public static final int EDGE_STRIDE = 6;
    public static final int ANCHOR_STRIDE = 2;
    private static final int MAX_ANCHORS = 8;
    private static final int ANCHOR_MIN_DIST = 2;
    private static final float MAX_SHELL_DEPTH = 2f / 16f;
    private static final float MIN_SHELL_DEPTH = 1f / 16f;
    private static final float PLANE_EPSILON = 1f / 64f;
    private static final float EPSILON = 1.0e-4f;
    private static final int DDA_SUBSTEPS = 4;
    private static final int MAX_CACHE = 64;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int DUMMY_LIGHT = 0x00F000F0;
    private static final Map<BakedModel, Baked> CACHE = new IdentityHashMap<>();
    private ShieldWardShape() {
    }

    public static final class Baked {
        public float halfWidth;
        public float halfHeight;
        public float centerX;
        public float centerY;
        public float frontZ;
        public float backZ;
        public float[] faceRects;
        public float[] edges;
        public float[] boxEdges;
        public float[] sparkAnchors;
        public boolean filmOnFront;
        public boolean fallback;
        private Baked(float halfWidth, float halfHeight, float centerX, float centerY, float frontZ, float backZ, float[] faceRects, float[] edges, float[] boxEdges, float[] sparkAnchors, boolean filmOnFront, boolean fallback) {
        }
        public int rectCount() {
            return 0;
        }
        public int edgeCount() {
            return 0;
        }
        public int anchorCount() {
            return 0;
        }
    }
    public static Baked of(BakedModel model, ItemStack stack, ItemDisplayContext displayContext) {
        return null;
    }
    public static void clearCache() {
    }

    private static final class TriSoup {
        float[] data;
        int count;
        boolean[] faceOnZ;
        float minX;
        float minY;
        float minZ;
        float maxX;
        float maxY;
        float maxZ;
        void add(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz) {
        }
    }

    private static final class CaptureBuffer implements MultiBufferSource {
        TriSoup soup;
        public VertexConsumer getBuffer(RenderType renderType) {
            return null;
        }
    }
}

