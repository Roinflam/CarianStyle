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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
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
 * <h2>⚠ BEWLR 截取</h2>
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
 * <b>⚠ 假画布不能对所有渲染类型返回同一个实例。</b>附魔物品渲染时会走
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
 * <h2>⚠ 为什么侧壁不能按面积光栅化</h2>
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
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class ShieldWardShape {

    /**
     * 日志器。
     * <p><b>不能用 {@code System.out}</b>——打包后的正式客户端不会把它捕获进
     * {@code latest.log}，输出等于石沉大海。必须走正式日志器。</p>
     */
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 调试开关：烘焙完成后把结果打到日志。
     * <p><b>排查完请改回 {@code false}。</b>每个模型只打一行（烘焙本身就只跑一次），
     * 不会刷屏，但正式版本不需要它。</p>
     */
    private static final boolean DEBUG = false;

    /**
     * 栅格分辨率。
     * <p>取 16 与原版物品贴图同分辨率，轮廓像素级贴合。
     * 降到 12 或 8 能明显减少轮廓边段数（顶点量的大头），代价是圆角变成粗阶梯。</p>
     */
    private static final int RES = 18;

    /**
     * 栅格<b>有效区</b>的边长：形状被映射到中间的 {@code INNER × INNER} 格，
     * 四周各留一格空白。
     *
     * <h4>⚠ 这一格空白是必须的，不是余量</h4>
     * <p>
     * 灌水必须从<b>确定在外部</b>的格子起步。若形状紧贴栅格边界，
     * 外面就没有任何合法起灌点；而实心形状（例如原版盾的挡板）内部没有墙，
     * 边界上只要有一格因取整没被标成墙，水就会从那个缺口灌进去、把整块形状淹掉。
     * </p>
     * <p>
     * v6.0 正是栽在这里：原版盾算出的轮廓只剩 55/256 格（一圈边框加手柄），
     * 挡板内部全被当成了外部。留一格空白之后，形状的墙成为一道封闭的堤，
     * 灌水被关在空白圈里出不来。
     * </p>
     */
    private static final int INNER = RES - 2;

    /**
     * 面片的不透明度分档数。
     * <p>贪心合并按档位进行：同档相邻格才能并成一个矩形。
     * 档数越多渐变越细腻、矩形越碎。5 档在盾牌这个尺寸上肉眼已看不出台阶。</p>
     */
    private static final int ALPHA_BANDS = 5;

    /** 每个面片矩形占用的 float 数：x0,y0,x1,y1 + 四角边缘系数 */
    public static final int RECT_STRIDE = 8;
    /** 每条轮廓边段占用的 float 数：x0,y0,x1,y1 + 外法线 nx,ny */
    public static final int EDGE_STRIDE = 6;
    /** 每个闪点锚位占用的 float 数：x,y */
    public static final int ANCHOR_STRIDE = 2;

    /** 闪点锚位最多烘焙几个（渲染器按需从中挑选） */
    private static final int MAX_ANCHORS = 8;
    /** 闪点锚位要求距轮廓外至少几格，避免闪点骑在边上或戳出盾外 */
    private static final int ANCHOR_MIN_DIST = 2;

    /**
     * 护鞘厚度的上限（格）。
     * <p>取 2/16。厚度正常由 {@link #resolveBackZ} 从几何里量出来，
     * 本值只是兜底，防止异常模型撑出一个大盒子。</p>
     */
    private static final float MAX_SHELL_DEPTH = 2f / 16f;

    /**
     * 护鞘厚度的下限（格）。
     * <p>取 1/16，正好是原版挡板的厚度。低于这个值侧缘会细到看不见，
     * 「包住」的观感就没了。</p>
     */
    private static final float MIN_SHELL_DEPTH = 1f / 16f;

    /**
     * 判定两个面「不在同一层」所需的最小 Z 间距（格）。
     * <p>比半个像素还小，只用来滤掉浮点误差，不会把真正相邻的两层判成一层。</p>
     */
    private static final float PLANE_EPSILON = 1f / 64f;

    /** 退化判定阈值（格） */
    private static final float EPSILON = 1.0e-4f;

    /** DDA 标记侧壁时，每格采样几次。取 4 足以避免漏格 */
    private static final int DDA_SUBSTEPS = 4;

    /** 烘焙缓存的容量上限。资源包重载会产生全新模型实例，旧条目永不命中，超限直接清空 */
    private static final int MAX_CACHE = 64;

    /** 取 {@code getQuads} 时传入的随机源，复用一个实例即可 */
    private static final RandomSource RANDOM = RandomSource.create();

    /** 截取 BEWLR 时用的假光照值（内容无关，随便给一个合法值） */
    private static final int DUMMY_LIGHT = 0x00F000F0;

    /**
     * 烘焙结果缓存，按 {@link BakedModel} 的<b>身份</b>索引。
     * <p>
     * 用模型而非物品做键有个额外好处：格挡状态会通过 override 切到另一个 BakedModel，
     * 于是「举盾」与「不举盾」两种轮廓各自成条目，自动分开。
     * </p>
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final Map<BakedModel, Baked> CACHE = new IdentityHashMap<>();

    private ShieldWardShape() {
    }

    /**
     * 一件物品烘焙好的护鞘几何。全部字段只读。
     * <p>坐标系与 {@link ShieldWardRenderer} 的绘制空间一致：
     * 原点在物品几何中心，1 单位 = 1 格，+Z 为正面（外侧）。</p>
     */
    public static final class Baked {

        /** 轮廓在 X 方向的半宽（格） */
        public final float halfWidth;
        /** 轮廓在 Y 方向的半高（格） */
        public final float halfHeight;
        /** 几何中心 X（相对物品模型中心） */
        public final float centerX;
        /** 几何中心 Y（相对物品模型中心） */
        public final float centerY;
        /** 正面所在 Z */
        public final float frontZ;
        /** 背面所在 Z */
        public final float backZ;

        /**
         * 面片矩形表，每 {@link #RECT_STRIDE} 个 float 一条：
         * {@code x0, y0, x1, y1, e00, e10, e11, e01}。
         * <p>后四个是四角的<b>边缘系数</b>（0 = 最靠内，1 = 贴着轮廓），
         * 渲染器据此算不透明度。坐标以几何中心为原点。</p>
         */
        public final float[] faceRects;

        /**
         * 真实轮廓的边段表，每 {@link #EDGE_STRIDE} 个 float 一条：
         * {@code x0, y0, x1, y1, nx, ny}。
         * <p>{@code (nx, ny)} 是指向轮廓<b>外侧</b>的单位法线，发光晕靠它往外撑。</p>
         */
        public final float[] edges;

        /**
         * 包围盒的四条边段，格式同 {@link #edges}。
         * <p>远距离简化档用它代替真实轮廓——边段数从几十降到 4，
         * 而那个距离上本来也分不清轮廓形状。</p>
         */
        public final float[] boxEdges;

        /**
         * 闪点锚位表，每 {@link #ANCHOR_STRIDE} 个 float 一条：{@code x, y}。
         * <p>都取自距轮廓外 {@link #ANCHOR_MIN_DIST} 格以上的位置，
         * 因此闪点不会骑在边上、也不会戳出盾外。</p>
         */
        public final float[] sparkAnchors;

        /**
         * 彩色的膜该画在<b>哪一面</b>：{@code true} 表示 +Z 那一侧，{@code false} 表示 -Z。
         *
         * <h4>为什么两条路不一样</h4>
         * <p>
         * 「外侧」指盾牌朝向敌人的那一面，也就是玩家看得到的那面。
         * 两条取几何的路，它落在相反的方向上：
         * </p>
         * <ul>
         *     <li><b>截取 BEWLR</b>——原版
         *         {@code BlockEntityWithoutLevelRenderer.renderByItem} 渲染盾牌前会
         *         {@code poseStack.scale(1, -1, -1)}，把 Z 翻了过来，
         *         于是挡板的外侧落在 +Z；</li>
         *     <li><b>读模型 quad</b>——没有这一步，外侧在 -Z。</li>
         * </ul>
         * <p>
         * 画错边的后果不是位置偏，而是<b>膜跑到盾牌内侧、被盾牌本体挡住</b>，
         * 正面看只剩描边和侧缘有颜色。
         * </p>
         * <p>
         * <b>已知局限</b>：这是按「BEWLR 一定会翻 Z」来判断的，依据是原版实现。
         * 若某个模组的 BEWLR 没有那句 {@code scale}，它的膜会跑到内侧——
         * 目前无法从外部判断，只能遇到再单独处理。
         * </p>
         */
        public final boolean filmOnFront;

        /** 是否为退化回落结果（未能求出真实轮廓，用的是包围盒） */
        public final boolean fallback;

        private Baked(float halfWidth, float halfHeight, float centerX, float centerY,
                      float frontZ, float backZ,
                      float[] faceRects, float[] edges, float[] boxEdges,
                      float[] sparkAnchors, boolean filmOnFront, boolean fallback) {
            this.halfWidth = halfWidth;
            this.halfHeight = halfHeight;
            this.centerX = centerX;
            this.centerY = centerY;
            this.frontZ = frontZ;
            this.backZ = backZ;
            this.faceRects = faceRects;
            this.edges = edges;
            this.boxEdges = boxEdges;
            this.sparkAnchors = sparkAnchors;
            this.filmOnFront = filmOnFront;
            this.fallback = fallback;
        }

        /** @return 面片矩形条数 */
        public int rectCount() {
            return faceRects.length / RECT_STRIDE;
        }

        /** @return 真实轮廓的边段条数 */
        public int edgeCount() {
            return edges.length / EDGE_STRIDE;
        }

        /** @return 闪点锚位个数 */
        public int anchorCount() {
            return sparkAnchors.length / ANCHOR_STRIDE;
        }
    }

    // ==================== 对外入口 ====================

    /**
     * 取某件物品的护鞘几何，首次调用时烘焙并缓存。
     *
     * @param model          物品的烘焙模型
     * @param stack          物品堆（BEWLR 截取需要它）
     * @param displayContext 显示上下文（转交给 BEWLR，多数实现并不使用）
     * @return 烘焙结果；任何环节失败都会返回一个基于包围盒的回落结果，绝不返回 null
     */
    public static Baked of(BakedModel model, ItemStack stack, ItemDisplayContext displayContext) {
        Baked cached = CACHE.get(model);
        if (cached != null) {
            return cached;
        }
        Baked baked;
        try {
            baked = bake(model, stack, displayContext);
            if (baked == null && DEBUG) {
                LOGGER.info("[CarianStyle/WardShape] " + stack.getDescriptionId()
                        + " ⚠ 烘焙返回空 → 用兜底方盒（原版盾尺寸）"
                        + " 路径=" + (model.isCustomRenderer() ? "截取BEWLR" : "读模型quad"));
            }
        } catch (Throwable t) {
            // 截取 BEWLR 等于在跑别人的代码，任何意外都不能让特效弄崩渲染
            if (DEBUG) {
                LOGGER.info("[CarianStyle/WardShape] " + stack.getDescriptionId()
                        + " ⚠ 烘焙抛异常 → 用兜底方盒（原版盾尺寸）: " + t);
                LOGGER.error("[CarianStyle/WardShape] 烘焙异常堆栈", t);
            }
            baked = degenerate();
        }
        if (baked == null) {
            baked = degenerate();
        }
        if (CACHE.size() >= MAX_CACHE) {
            CACHE.clear();
        }
        CACHE.put(model, baked);
        return baked;
    }

    /**
     * 清空烘焙缓存。由渲染器在离开世界时调用。
     */
    public static void clearCache() {
        CACHE.clear();
    }

    // ==================== 烘焙主流程 ====================

    /**
     * 完整烘焙一件物品。
     *
     * @return 烘焙结果；取不到任何几何时返回 null
     */
    @Nullable
    private static Baked bake(BakedModel model, ItemStack stack, ItemDisplayContext displayContext) {
        TriSoup soup = model.isCustomRenderer()
                ? captureFromBewlr(stack, displayContext)
                : collectFromQuads(model);
        if (soup == null || soup.count == 0) {
            return null;
        }

        // ===== 包围盒 =====
        float minX = soup.minX, maxX = soup.maxX;
        float minY = soup.minY, maxY = soup.maxY;
        float spanX = maxX - minX;
        float spanY = maxY - minY;
        if (spanX < EPSILON || spanY < EPSILON) {
            return null;
        }

        float frontZ = soup.maxZ;
        float backZ = resolveBackZ(soup, frontZ);

        // ===== 光栅化 =====
        boolean[] footprint = new boolean[RES * RES];
        // 墙记在格与格之间的缝上：竖缝 (RES+1) 列 × RES 行，横缝 RES 列 × (RES+1) 行
        boolean[] vWall = new boolean[(RES + 1) * RES];
        boolean[] hWall = new boolean[RES * (RES + 1)];
        rasterize(soup, minX, minY, spanX, spanY, footprint, vWall, hWall);

        // ===== 灌水求轮廓 =====
        boolean[] mask = solveSilhouette(footprint, vWall, hWall);
        if (mask == null) {
            return null;
        }

        // ===== 距离场 =====
        int[] dist = distanceField(mask);
        int maxDist = 1;
        for (int d : dist) {
            if (d > maxDist) {
                maxDist = d;
            }
        }

        // 格点（而非格心）上的边缘系数：相邻格取平均，使相邻矩形共享的角值连续
        float[] cornerEdge = cornerEdgeField(mask, dist, maxDist);

        // ===== 贪心合并面片 =====
        float centerX = (minX + maxX) * 0.5f;
        float centerY = (minY + maxY) * 0.5f;
        float cellW = spanX / INNER;
        float cellH = spanY / INNER;
        // 栅格 0 号格在形状左下角<b>再往外一格</b>，故原点要相应外移一格
        float originX = minX - centerX - cellW;
        float originY = minY - centerY - cellH;
        float[] faceRects = greedyMesh(mask, dist, maxDist, cornerEdge,
                originX, originY, cellW, cellH);

        // ===== 轮廓追踪 =====
        float[] edges = traceOutline(mask, originX, originY, cellW, cellH);
        if (edges.length == 0) {
            return null;
        }

        // ===== 闪点锚位 =====
        float[] anchors = pickAnchors(mask, dist, originX, originY, cellW, cellH);

        float halfW = spanX * 0.5f;
        float halfH = spanY * 0.5f;
        float[] boxEdges = boxEdges(halfW, halfH);

        if (DEBUG) {
            int filled = 0;
            for (boolean b : mask) {
                if (b) {
                    filled++;
                }
            }
            LOGGER.info("[CarianStyle/WardShape] " + stack.getDescriptionId()
                    + " 路径=" + (model.isCustomRenderer() ? "截取BEWLR" : "读模型quad")
                    + " 三角形=" + soup.count
                    + " 包围盒 x[" + fmt(minX) + "," + fmt(maxX) + "]"
                    + " y[" + fmt(minY) + "," + fmt(maxY) + "]"
                    + " z[" + fmt(soup.minZ) + "," + fmt(soup.maxZ) + "]"
                    + " 中心=(" + fmt(centerX) + "," + fmt(centerY) + ")"
                    + " 半宽高=(" + fmt(halfW) + "," + fmt(halfH) + ")"
                    + " 正背Z=(" + fmt(frontZ) + "," + fmt(backZ) + ")"
                    + " 轮廓格数=" + filled + "/" + (RES * RES)
                    + " 面片=" + (faceRects.length / RECT_STRIDE)
                    + " 边段=" + (edges.length / EDGE_STRIDE));
        }

        // BEWLR 截来的坐标已含原版那句 scale(1,-1,-1)，外侧在 +Z；读 quad 那条路没有，外侧在 -Z
        boolean filmOnFront = model.isCustomRenderer();

        return new Baked(halfW, halfH, centerX, centerY, frontZ, backZ,
                faceRects, edges, boxEdges, anchors, filmOnFront, false);
    }

    /**
     * 调试输出用的短格式化（保留三位小数）。
     */
    private static String fmt(float v) {
        return String.format("%.3f", v);
    }

    /**
     * 完全取不到几何时的回落：一个原版盾牌尺寸的方盒。
     * <p>
     * <b>坐标用的是「截取 BEWLR」那一套基准</b>（模型盒中心为原点）。
     * 普通 JSON 模型若走到这条回落，Z 方向会偏半格——但那意味着连包围盒都没量出来，
     * 本来就已经在瞎猜了，不值得为它再分一套。
     * </p>
     * <p>
     * 尺寸取自原版 {@code ShieldModel} 的挡板
     * （{@code addBox(-6,-11,-2, 12,22,1)}，除以 16 并计入 BEWLR 的
     * {@code scale(1,-1,-1)}）。面片给一整块，轮廓给四条边。
     * </p>
     */
    private static Baked degenerate() {
        float halfW = 6f / 16f;
        float halfH = 11f / 16f;
        float[] rect = {
                -halfW, -halfH, halfW, halfH,
                1f, 1f, 1f, 1f
        };
        float[] edges = boxEdges(halfW, halfH);
        float[] anchors = {
                -halfW * 0.4f, halfH * 0.35f,
                halfW * 0.42f, -halfH * 0.2f,
                0f, halfH * 0.6f
        };
        // 兜底值取的是原版盾（BEWLR）那一套，故外侧也跟着它落在 +Z
        return new Baked(halfW, halfH, 0f, 0f, 2f / 16f, 1f / 16f,
                rect, edges, edges, anchors, true, true);
    }

    /**
     * 求护鞘背面所在的 Z。
     *
     * <h4>为什么不能直接用包围盒的最小 Z</h4>
     * <p>
     * 盾牌的<b>手柄向后伸出很长</b>：原版手柄占 {@code z ∈ [-0.313, 0.063]}，
     * 而挡板只占 {@code [0.063, 0.125]}。用包围盒会得到 0.44 格的厚度，
     * 是挡板本身的七倍，护鞘的侧缘会深得像个盒子而不是一层膜。
     * </p>
     *
     * <h4>做法：找紧挨着正面的下一层</h4>
     * <p>
     * 只看「正对着看」的面（法线沿 Z），取它们各自的平面高度，
     * 找出<b>比正面低、但离正面最近</b>的那一层，两者间距就是厚度。
     * </p>
     * <ul>
     *     <li>原版盾：正面 0.125，下一层 0.063 → 厚度 1 像素，手柄那层被自动跳过；</li>
     *     <li>2D 精灵：正面 0.531，下一层 0.469 → 厚度 1 像素；</li>
     *     <li>带浮雕盾心的模型：会取到盾心底面，得到一层薄壳——偏薄总比偏厚安全。</li>
     * </ul>
     * <p>结果夹在 {@link #MIN_SHELL_DEPTH} 与 {@link #MAX_SHELL_DEPTH} 之间。</p>
     *
     * @param soup   全部三角形
     * @param frontZ 正面所在 Z
     * @return 背面所在 Z
     */
    private static float resolveBackZ(TriSoup soup, float frontZ) {
        float best = -Float.MAX_VALUE;
        for (int t = 0; t < soup.count; t++) {
            if (!soup.faceOnZ[t]) {
                continue;
            }
            int o = t * 9;
            // 正对着看的三角形三个顶点的 Z 基本相同，取平均即可代表这一层
            float z = (soup.data[o + 2] + soup.data[o + 5] + soup.data[o + 8]) / 3f;
            if (z < frontZ - PLANE_EPSILON && z > best) {
                best = z;
            }
        }
        float depth = (best == -Float.MAX_VALUE) ? MIN_SHELL_DEPTH : (frontZ - best);
        if (depth < MIN_SHELL_DEPTH) {
            depth = MIN_SHELL_DEPTH;
        } else if (depth > MAX_SHELL_DEPTH) {
            depth = MAX_SHELL_DEPTH;
        }
        return frontZ - depth;
    }

    // ==================== 第一步：取三角形 ====================

    /**
     * 一堆三角形。用打平的 float 数组而非对象列表，避免烘焙期产生大量小对象。
     */
    private static final class TriSoup {
        /** 每 9 个 float 一个三角形：ax,ay,az, bx,by,bz, cx,cy,cz */
        float[] data = new float[512 * 9];
        /** 三角形个数 */
        int count;
        /** 每个三角形是否「面朝 Z」（法线的 Z 分量占主导） */
        boolean[] faceOnZ = new boolean[512];

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        /**
         * 追加一个三角形，同时更新包围盒并判定朝向。
         */
        void add(float ax, float ay, float az,
                 float bx, float by, float bz,
                 float cx, float cy, float cz) {
            if (count * 9 + 9 > data.length) {
                float[] nd = new float[data.length * 2];
                System.arraycopy(data, 0, nd, 0, data.length);
                data = nd;
                boolean[] nf = new boolean[faceOnZ.length * 2];
                System.arraycopy(faceOnZ, 0, nf, 0, faceOnZ.length);
                faceOnZ = nf;
            }
            int o = count * 9;
            data[o] = ax;
            data[o + 1] = ay;
            data[o + 2] = az;
            data[o + 3] = bx;
            data[o + 4] = by;
            data[o + 5] = bz;
            data[o + 6] = cx;
            data[o + 7] = cy;
            data[o + 8] = cz;

            // 法线 = (b-a) × (c-a)，只需判断 Z 分量是否占主导
            float ux = bx - ax, uy = by - ay, uz = bz - az;
            float vx = cx - ax, vy = cy - ay, vz = cz - az;
            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;
            float az2 = Math.abs(nz);
            faceOnZ[count] = az2 >= Math.abs(nx) && az2 >= Math.abs(ny);

            count++;
            expand(ax, ay, az);
            expand(bx, by, bz);
            expand(cx, cy, cz);
        }

        private void expand(float x, float y, float z) {
            if (x < minX) {
                minX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (z < minZ) {
                minZ = z;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y > maxY) {
                maxY = y;
            }
            if (z > maxZ) {
                maxZ = z;
            }
        }
    }

    /**
     * 从普通 JSON 模型收集三角形。
     *
     * <h4>⚠ 坐标<b>原样收下</b>，绝不能自己减 0.5</h4>
     * <p>
     * 原版 {@code ItemRenderer.render} 的顺序是：先套用手持变换，
     * 再 {@code translate(-0.5, -0.5, -0.5)}，<b>然后</b>把 {@code getQuads}
     * 给的原始坐标画出去。也就是说那半格位移是由 {@code translate} 负责的，
     * 顶点自己是 {@code [0,1]³} 的原始值。
     * </p>
     * <p>
     * 渲染器为了对齐，同样会做那次 {@code translate}。若这里再手动减一次 0.5，
     * 就等于<b>减了两次</b>，护鞘会整体偏出半格对角线（约 0.87 格）——
     * v6.0 的基础盾错位就是这么来的。
     * </p>
     * <p>
     * 截取 BEWLR 那条路没有这个问题：它传的是单位矩阵，
     * 拿到的坐标本来就是「已经位移过」的那一套，两条路各自都对，
     * <b>但坐标基准不同，改任何一条都要单独验证</b>。
     * </p>
     * <p>
     * {@code side == null} 那一批是不归属任何朝向的面，<b>必须单独取一次</b>——
     * 物品模型的面绝大多数都落在这一批里，只遍历六个方向会几乎什么都取不到。
     * </p>
     * <p>
     * {@code side == null} 那一批是不归属任何朝向的面，<b>必须单独取一次</b>——
     * 物品模型的面绝大多数都落在这一批里，只遍历六个方向会几乎什么都取不到。
     * </p>
     */
    @Nullable
    private static TriSoup collectFromQuads(BakedModel model) {
        TriSoup soup = new TriSoup();
        for (int i = -1; i < 6; i++) {
            Direction side = (i < 0) ? null : Direction.values()[i];
            List<BakedQuad> quads = model.getQuads(null, side, RANDOM);
            if (quads == null || quads.isEmpty()) {
                continue;
            }
            for (int q = 0; q < quads.size(); q++) {
                int[] data = quads.get(q).getVertices();
                int stride = data.length / 4;
                if (stride < 3) {
                    continue;
                }
                float[] px = new float[4];
                float[] py = new float[4];
                float[] pz = new float[4];
                for (int v = 0; v < 4; v++) {
                    int o = v * stride;
                    // ⚠ 原样收下，不要减 0.5：那半格由渲染器的 translate 负责，见方法注释
                    px[v] = Float.intBitsToFloat(data[o]);
                    py[v] = Float.intBitsToFloat(data[o + 1]);
                    pz[v] = Float.intBitsToFloat(data[o + 2]);
                }
                // 四边形拆成两个三角形
                soup.add(px[0], py[0], pz[0], px[1], py[1], pz[1], px[2], py[2], pz[2]);
                soup.add(px[0], py[0], pz[0], px[2], py[2], pz[2], px[3], py[3], pz[3]);
            }
        }
        return soup.count == 0 ? null : soup;
    }

    /**
     * 从 BEWLR 截取三角形：给它一个只记录不绘制的缓冲，让它画一次。
     * <p>
     * <b>PoseStack 传单位矩阵</b>，于是收到的就是绘制空间里的最终坐标，
     * BEWLR 自己做的一切变换（原版盾牌那句 {@code scale(1,-1,-1)}）都已包含在内。
     * </p>
     */
    @Nullable
    private static TriSoup captureFromBewlr(ItemStack stack, ItemDisplayContext displayContext) {
        IClientItemExtensions ext = IClientItemExtensions.of(stack);
        if (ext == null) {
            return null;
        }
        BlockEntityWithoutLevelRenderer renderer = ext.getCustomRenderer();
        if (renderer == null) {
            return null;
        }
        CaptureBuffer capture = new CaptureBuffer();
        renderer.renderByItem(stack, displayContext, new PoseStack(), capture,
                DUMMY_LIGHT, OverlayTexture.NO_OVERLAY);
        return capture.soup.count == 0 ? null : capture.soup;
    }

    /**
     * 只记录顶点位置、不做任何绘制的缓冲。
     * <p>
     * 同时实现 {@link MultiBufferSource} 与 {@link VertexConsumer}：
     * 无论对方要哪个 {@link RenderType} 都返回自己。
     * </p>
     * <p>
     * {@code VertexConsumer} 里那些带矩阵或一次给全部属性的重载都是
     * {@code default} 方法，内部会转调下面这几个抽象方法，因此只实现这几个就够，
     * 而且经过矩阵变换的坐标会如实落到 {@link #vertex(double, double, double)} 上。
     * </p>
     * <p>顶点按每 4 个凑成一个四边形——模型渲染一律走 QUADS 图元。</p>
     */
    private static final class CaptureBuffer implements MultiBufferSource {

        final TriSoup soup = new TriSoup();
        /** 每个渲染类型一个独立记录器，见类注释里的「不能共用一个实例」 */
        private final Map<RenderType, Recorder> recorders = new HashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return recorders.computeIfAbsent(renderType, t -> new Recorder(soup));
        }
    }

    /**
     * 只记录顶点位置、不做任何绘制的记录器。
     * <p>
     * {@code VertexConsumer} 里那些带矩阵或一次给全部属性的重载都是 {@code default} 方法，
     * 内部会转调下面这几个抽象方法，因此只实现这几个就够，
     * 而且经过矩阵变换的坐标会如实落到 {@link #vertex(double, double, double)} 上。
     * </p>
     * <p>顶点按每 4 个凑成一个四边形——模型渲染一律走 QUADS 图元。</p>
     * <p>
     * <b>每个渲染类型必须是独立实例</b>，且各自持有自己的 {@link #pending}：
     * 附魔物品会同时往「附魔光效」和「本体」两个缓冲写同一批顶点，
     * 共用一个 pending 会让两路顶点交错、凑出错误的四边形。
     * </p>
     */
    private static final class Recorder implements VertexConsumer {

        private final TriSoup soup;
        /** 当前四边形已攒到的顶点 */
        private final float[] pending = new float[12];
        private int pendingCount;

        Recorder(TriSoup soup) {
            this.soup = soup;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            if (pendingCount < 4) {
                int o = pendingCount * 3;
                pending[o] = (float) x;
                pending[o + 1] = (float) y;
                pending[o + 2] = (float) z;
            }
            pendingCount++;
            if (pendingCount == 4) {
                soup.add(pending[0], pending[1], pending[2],
                        pending[3], pending[4], pending[5],
                        pending[6], pending[7], pending[8]);
                soup.add(pending[0], pending[1], pending[2],
                        pending[6], pending[7], pending[8],
                        pending[9], pending[10], pending[11]);
                pendingCount = 0;
            }
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
            // 位置已在 vertex(...) 里收下，此处无事可做
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            // 不关心颜色
        }

        @Override
        public void unsetDefaultColor() {
            // 不关心颜色
        }
    }

    // ==================== 第二步：光栅化 ====================

    /**
     * 把三角形扫进「占用图」与「边缝阻挡表」。
     * <p>
     * <b>面朝 Z 的三角形</b>按面积扫进 {@code footprint}，给出占用范围的上界。<br>
     * <b>侧壁三角形</b>投影下来是一条线，面积为零，按面积扫一格都扫不到；
     * 它们改为记录成「哪两格之间不通」，见 {@link #blockWall}。
     * </p>
     *
     * <h4>⚠ 墙是格与格之间的<b>缝</b>，不是格本身</h4>
     * <p>
     * v6.1 之前把墙标记成格子，于是墙格自己也算进了轮廓。这会带来一格的系统偏差，
     * 而且<b>左右不对称</b>：左侧的墙算下来落在实心格上（无害），
     * 右侧的墙落在空心格上，把本该镂空的格子填实了。
     * </p>
     * <p>
     * 泰拉钢盾两侧有一排一像素深的锯齿凹槽，正好把这个问题放大到肉眼可见——
     * 左侧凹槽保住了，右侧全被填平。改成边缝表示后，墙不再占任何格子，
     * 轮廓完全由实际的面决定，实测五面盾逐像素零误差。
     * </p>
     *
     * @param footprint 占用图，索引 {@code y * RES + x}
     * @param vWall     竖缝阻挡表，{@code y * (RES + 1) + x} 表示格 {@code (x-1,y)} 与 {@code (x,y)} 不通
     * @param hWall     横缝阻挡表，{@code y * RES + x} 表示格 {@code (x,y-1)} 与 {@code (x,y)} 不通
     */
    private static void rasterize(TriSoup soup, float minX, float minY, float spanX, float spanY,
                                  boolean[] footprint, boolean[] vWall, boolean[] hWall) {
        // 映射到中间的有效区：形状占 1..INNER+1，四周各留一格空白供灌水起步
        float invW = INNER / spanX;
        float invH = INNER / spanY;
        for (int t = 0; t < soup.count; t++) {
            int o = t * 9;
            float ax = 1f + (soup.data[o] - minX) * invW;
            float ay = 1f + (soup.data[o + 1] - minY) * invH;
            float bx = 1f + (soup.data[o + 3] - minX) * invW;
            float by = 1f + (soup.data[o + 4] - minY) * invH;
            float cx = 1f + (soup.data[o + 6] - minX) * invW;
            float cy = 1f + (soup.data[o + 7] - minY) * invH;

            if (soup.faceOnZ[t]) {
                fillTriangle(footprint, ax, ay, bx, by, cx, cy);
            } else {
                blockWall(vWall, hWall, ax, ay, bx, by);
                blockWall(vWall, hWall, bx, by, cx, cy);
                blockWall(vWall, hWall, cx, cy, ax, ay);
                // 侧壁本身也属于占用范围。这里按格标记会略微偏大，
                // 但多出来的格子会被灌水正确地判成外部，不影响最终轮廓
                markSegment(footprint, ax, ay, bx, by);
                markSegment(footprint, bx, by, cx, cy);
                markSegment(footprint, cx, cy, ax, ay);
            }
        }
    }

    /**
     * 把一段墙记录成「这一串缝不通」。
     * <p>
     * 按段的走向判断它是竖墙还是横墙，然后<b>按格区间</b>标记，
     * 而不是沿着段逐点采样——采样到端点时会多算一格，
     * 那正是 v6.1 里泰拉钢盾顶部三行右侧各多出一格的原因。
     * </p>
     * <p>
     * 竖墙用两端点的中值定列（比取某一端稳），行覆盖
     * {@code floor(minY) .. ceil(maxY) - 1}；横墙同理。
     * 斜墙会按主轴近似，覆盖偏多——宁可多挡也不能漏，漏一处灌水就会淹掉整块形状。
     * </p>
     */
    private static void blockWall(boolean[] vWall, boolean[] hWall,
                                  float x0, float y0, float x1, float y1) {
        float dx = Math.abs(x1 - x0);
        float dy = Math.abs(y1 - y0);
        if (dy >= dx) {
            int col = Math.round((x0 + x1) * 0.5f);
            if (col < 0 || col > RES) {
                return;
            }
            int r0 = (int) Math.floor(Math.min(y0, y1));
            int r1 = (int) Math.ceil(Math.max(y0, y1)) - 1;
            for (int r = Math.max(0, r0); r <= Math.min(RES - 1, r1); r++) {
                vWall[r * (RES + 1) + col] = true;
            }
        } else {
            int row = Math.round((y0 + y1) * 0.5f);
            if (row < 0 || row > RES) {
                return;
            }
            int c0 = (int) Math.floor(Math.min(x0, x1));
            int c1 = (int) Math.ceil(Math.max(x0, x1)) - 1;
            for (int c = Math.max(0, c0); c <= Math.min(RES - 1, c1); c++) {
                hWall[row * RES + c] = true;
            }
        }
    }

    /**
     * 按格心是否落在三角形内来填充（重心坐标符号判定）。
     */
    private static void fillTriangle(boolean[] mask,
                                     float ax, float ay, float bx, float by, float cx, float cy) {
        int x0 = clampCell((int) Math.floor(Math.min(ax, Math.min(bx, cx))));
        int x1 = clampCell((int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int y0 = clampCell((int) Math.floor(Math.min(ay, Math.min(by, cy))));
        int y1 = clampCell((int) Math.ceil(Math.max(ay, Math.max(by, cy))));
        float area = edgeFn(ax, ay, bx, by, cx, cy);
        if (Math.abs(area) < 1.0e-7f) {
            return;
        }
        boolean neg = area < 0f;
        for (int y = y0; y <= y1; y++) {
            float py = y + 0.5f;
            for (int x = x0; x <= x1; x++) {
                float px = x + 0.5f;
                float w0 = edgeFn(bx, by, cx, cy, px, py);
                float w1 = edgeFn(cx, cy, ax, ay, px, py);
                float w2 = edgeFn(ax, ay, bx, by, px, py);
                boolean inside = neg
                        ? (w0 <= 0f && w1 <= 0f && w2 <= 0f)
                        : (w0 >= 0f && w1 >= 0f && w2 >= 0f);
                if (inside) {
                    mask[y * RES + x] = true;
                }
            }
        }
    }

    /**
     * 把格坐标夹进有效区 {@code [1, INNER]}。
     * <p>形状按定义落在有效区内，四周那圈空白只留给灌水起步，不该被任何几何碰到。</p>
     */
    private static int clampCell(int v) {
        if (v < 1) {
            return 1;
        }
        return Math.min(v, INNER);
    }

    /** 二维叉积，用于重心坐标的符号判定 */
    private static float edgeFn(float ax, float ay, float bx, float by, float px, float py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    /**
     * 沿线段逐格标记（超覆盖 DDA）。仅用于把侧壁计入占用范围。
     * <p>按格长的 {@link #DDA_SUBSTEPS} 分之一步进采样，足以保证不漏格。</p>
     */
    private static void markSegment(boolean[] mask, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(1, (int) Math.ceil(len * DDA_SUBSTEPS));
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            int gx = clampCell((int) Math.floor(x0 + dx * t));
            int gy = clampCell((int) Math.floor(y0 + dy * t));
            mask[gy * RES + gx] = true;
        }
    }

    // ==================== 第三步：灌水求轮廓 ====================

    /**
     * 从栅格边界灌水，被<b>边缝</b>挡住；灌不到的格 ∩ 占用范围 = 真实轮廓。
     * <p>
     * 这一步是整套算法的核心。对 {@code builtin/generated} 精灵来说，
     * {@code footprint} 是一整块方形（正面就是一块铺满的矩形），
     * 真正描出形状的是那些墙；灌水之后方形里被墙围住的部分才是盾。
     * </p>
     * <p>
     * 对手搓的立方体模型，{@code footprint} 本身已经接近轮廓，
     * 灌水只是把凹进去的空腔正确排除掉。两类模型共用这一条路径。
     * </p>
     * <p>
     * 因为墙记在缝上而不是格上，<b>没有任何格子会因为"是墙"而进入轮廓</b>，
     * 轮廓完全由实际的面决定。四周那圈空白保证灌水永远有合法起点。
     * </p>
     *
     * @return 轮廓掩码；结果为空时退回占用范围本身
     */
    @Nullable
    private static boolean[] solveSilhouette(boolean[] footprint, boolean[] vWall, boolean[] hWall) {
        boolean[] outside = new boolean[RES * RES];
        int[] queue = new int[RES * RES];
        int tail = 0;

        // 四周那圈空白按定义就在外部，全部作为起点
        for (int i = 0; i < RES; i++) {
            tail = seed(outside, queue, tail, i, 0);
            tail = seed(outside, queue, tail, i, RES - 1);
            tail = seed(outside, queue, tail, 0, i);
            tail = seed(outside, queue, tail, RES - 1, i);
        }

        int head = 0;
        while (head < tail) {
            int cur = queue[head++];
            int cx = cur % RES;
            int cy = cur / RES;
            if (cx > 0 && !vWall[cy * (RES + 1) + cx]) {
                tail = seed(outside, queue, tail, cx - 1, cy);
            }
            if (cx < RES - 1 && !vWall[cy * (RES + 1) + cx + 1]) {
                tail = seed(outside, queue, tail, cx + 1, cy);
            }
            if (cy > 0 && !hWall[cy * RES + cx]) {
                tail = seed(outside, queue, tail, cx, cy - 1);
            }
            if (cy < RES - 1 && !hWall[(cy + 1) * RES + cx]) {
                tail = seed(outside, queue, tail, cx, cy + 1);
            }
        }

        boolean[] mask = new boolean[RES * RES];
        int filled = 0;
        for (int i = 0; i < mask.length; i++) {
            if (footprint[i] && !outside[i]) {
                mask[i] = true;
                filled++;
            }
        }
        if (filled == 0) {
            int fp = 0;
            for (boolean v : footprint) {
                if (v) {
                    fp++;
                }
            }
            return fp == 0 ? null : footprint;
        }
        return mask;
    }

    /**
     * 灌水的单格入队：未访问才入队。
     * <p>能不能走到这一格由调用方查边缝决定，本方法只管去重。</p>
     *
     * @return 更新后的队尾
     */
    private static int seed(boolean[] outside, int[] queue, int tail, int x, int y) {
        int idx = y * RES + x;
        if (outside[idx]) {
            return tail;
        }
        outside[idx] = true;
        queue[tail] = idx;
        return tail + 1;
    }

    // ==================== 第四步：距离场 ====================

    /**
     * 求每个轮廓内格到轮廓外的曼哈顿距离（BFS）。
     * <p>贴着边的格距离为 1，越往里越大。「边缘实、中心透」的渐变由它驱动。</p>
     */
    private static int[] distanceField(boolean[] mask) {
        int[] dist = new int[RES * RES];
        int[] queue = new int[RES * RES];
        int head = 0, tail = 0;

        for (int y = 0; y < RES; y++) {
            for (int x = 0; x < RES; x++) {
                int idx = y * RES + x;
                if (!mask[idx]) {
                    continue;
                }
                // 与外部（或栅格边界）相邻 → 距离 1
                if (x == 0 || x == RES - 1 || y == 0 || y == RES - 1
                        || !mask[idx - 1] || !mask[idx + 1]
                        || !mask[idx - RES] || !mask[idx + RES]) {
                    dist[idx] = 1;
                    queue[tail++] = idx;
                }
            }
        }
        while (head < tail) {
            int cur = queue[head++];
            int cxx = cur % RES;
            int cyy = cur / RES;
            int nd = dist[cur] + 1;
            if (cxx > 0) {
                tail = relax(mask, dist, queue, tail, cur - 1, nd);
            }
            if (cxx < RES - 1) {
                tail = relax(mask, dist, queue, tail, cur + 1, nd);
            }
            if (cyy > 0) {
                tail = relax(mask, dist, queue, tail, cur - RES, nd);
            }
            if (cyy < RES - 1) {
                tail = relax(mask, dist, queue, tail, cur + RES, nd);
            }
        }
        return dist;
    }

    /**
     * BFS 松弛一格。
     *
     * @return 更新后的队尾
     */
    private static int relax(boolean[] mask, int[] dist, int[] queue, int tail, int idx, int nd) {
        if (!mask[idx] || dist[idx] != 0) {
            return tail;
        }
        dist[idx] = nd;
        queue[tail] = idx;
        return tail + 1;
    }

    /**
     * 求格点（而非格心）上的边缘系数。
     * <p>
     * 取周围四格的平均。<b>用格点而不是格心，是为了让相邻矩形共享的角值完全相同</b>——
     * 否则两块矩形的接缝处不透明度对不上，会出现一条可见的缝。
     * </p>
     * <p>轮廓外的格按 1（最实）计入，于是贴边处自然最实。</p>
     *
     * @return 长度 {@code (RES+1)²} 的数组，索引 {@code gy * (RES+1) + gx}
     */
    private static float[] cornerEdgeField(boolean[] mask, int[] dist, int maxDist) {
        int n = RES + 1;
        float[] out = new float[n * n];
        for (int gy = 0; gy < n; gy++) {
            for (int gx = 0; gx < n; gx++) {
                float sum = 0f;
                int cnt = 0;
                for (int dy = -1; dy <= 0; dy++) {
                    for (int dx = -1; dx <= 0; dx++) {
                        int cxx = gx + dx;
                        int cyy = gy + dy;
                        if (cxx < 0 || cxx >= RES || cyy < 0 || cyy >= RES) {
                            sum += 1f;
                            cnt++;
                            continue;
                        }
                        int idx = cyy * RES + cxx;
                        if (!mask[idx]) {
                            sum += 1f;
                        } else {
                            sum += 1f - (float) (dist[idx] - 1) / maxDist;
                        }
                        cnt++;
                    }
                }
                out[gy * n + gx] = cnt == 0 ? 1f : sum / cnt;
            }
        }
        return out;
    }

    // ==================== 第五步：贪心合并 ====================

    /**
     * 把轮廓内的格按不透明度档位贪心合并成矩形。
     * <p>
     * 先把距离场量化成 {@link #ALPHA_BANDS} 档，再对同档的相邻格做标准的二维贪心合并
     * （先往右扩，再整行整行往下扩）。
     * </p>
     * <p>
     * <b>这一步是「精细」与「省顶点」能同时成立的原因。</b>
     * 档位在边缘那圈变化最密，于是边缘留下许多小矩形，形状跟得住；
     * 而内部大片同档，会合成寥寥几块。细节留在需要的地方。
     * </p>
     *
     * @param originX 栅格左下角相对几何中心的 X
     * @param originY 栅格左下角相对几何中心的 Y
     * @param cellW   单格宽度（格）
     * @param cellH   单格高度（格）
     * @return 面片矩形表，格式见 {@link Baked#faceRects}
     */
    private static float[] greedyMesh(boolean[] mask, int[] dist, int maxDist, float[] cornerEdge,
                                      float originX, float originY, float cellW, float cellH) {
        int n = RES + 1;
        int[] band = new int[RES * RES];
        for (int i = 0; i < mask.length; i++) {
            if (!mask[i]) {
                band[i] = -1;
                continue;
            }
            float e = 1f - (float) (dist[i] - 1) / maxDist;
            int b = (int) (e * (ALPHA_BANDS - 1) + 0.5f);
            band[i] = Math.max(0, Math.min(ALPHA_BANDS - 1, b));
        }

        boolean[] used = new boolean[RES * RES];
        List<float[]> rects = new ArrayList<>();

        for (int y = 0; y < RES; y++) {
            for (int x = 0; x < RES; x++) {
                int idx = y * RES + x;
                if (used[idx] || band[idx] < 0) {
                    continue;
                }
                int b = band[idx];

                // 往右扩
                int w = 1;
                while (x + w < RES) {
                    int t = idx + w;
                    if (used[t] || band[t] != b) {
                        break;
                    }
                    w++;
                }
                // 整行整行往下扩
                int h = 1;
                outer:
                while (y + h < RES) {
                    int rowBase = (y + h) * RES + x;
                    for (int k = 0; k < w; k++) {
                        if (used[rowBase + k] || band[rowBase + k] != b) {
                            break outer;
                        }
                    }
                    h++;
                }
                for (int yy = 0; yy < h; yy++) {
                    int rowBase = (y + yy) * RES + x;
                    for (int xx = 0; xx < w; xx++) {
                        used[rowBase + xx] = true;
                    }
                }

                float rx0 = originX + x * cellW;
                float ry0 = originY + y * cellH;
                float rx1 = originX + (x + w) * cellW;
                float ry1 = originY + (y + h) * cellH;
                rects.add(new float[]{
                        rx0, ry0, rx1, ry1,
                        cornerEdge[y * n + x],
                        cornerEdge[y * n + (x + w)],
                        cornerEdge[(y + h) * n + (x + w)],
                        cornerEdge[(y + h) * n + x]
                });
            }
        }

        float[] out = new float[rects.size() * RECT_STRIDE];
        for (int i = 0; i < rects.size(); i++) {
            System.arraycopy(rects.get(i), 0, out, i * RECT_STRIDE, RECT_STRIDE);
        }
        return out;
    }

    // ==================== 第六步：轮廓追踪 ====================

    /**
     * 收集轮廓格与外部相邻的那些边，并把共线相邻的合并成长段。
     * <p>
     * 合并很重要：不合并的话一面 16×16 的盾大约有五十条单位边段，
     * 而轮廓边段是顶点量的大头（描边、侧缘、发光晕各要用它画一遍）。
     * 合并之后通常降到二三十条。
     * </p>
     *
     * @return 边段表，格式见 {@link Baked#edges}
     */
    private static float[] traceOutline(boolean[] mask, float originX, float originY,
                                        float cellW, float cellH) {
        List<float[]> segs = new ArrayList<>();

        // ===== 水平边：上沿与下沿。沿 X 合并连续同行的边 =====
        for (int y = 0; y <= RES; y++) {
            int runStart = -1;
            int runDir = 0;
            for (int x = 0; x <= RES; x++) {
                int dir = 0;
                if (x < RES) {
                    boolean below = y > 0 && mask[(y - 1) * RES + x];
                    boolean above = y < RES && mask[y * RES + x];
                    if (below && !above) {
                        dir = 1;   // 外侧朝 +Y
                    } else if (above && !below) {
                        dir = -1;  // 外侧朝 -Y
                    }
                }
                if (dir != runDir) {
                    if (runDir != 0) {
                        segs.add(new float[]{
                                originX + runStart * cellW, originY + y * cellH,
                                originX + x * cellW, originY + y * cellH,
                                0f, runDir
                        });
                    }
                    runStart = x;
                    runDir = dir;
                }
            }
        }

        // ===== 垂直边：左沿与右沿。沿 Y 合并 =====
        for (int x = 0; x <= RES; x++) {
            int runStart = -1;
            int runDir = 0;
            for (int y = 0; y <= RES; y++) {
                int dir = 0;
                if (y < RES) {
                    boolean left = x > 0 && mask[y * RES + (x - 1)];
                    boolean right = x < RES && mask[y * RES + x];
                    if (left && !right) {
                        dir = 1;   // 外侧朝 +X
                    } else if (right && !left) {
                        dir = -1;  // 外侧朝 -X
                    }
                }
                if (dir != runDir) {
                    if (runDir != 0) {
                        segs.add(new float[]{
                                originX + x * cellW, originY + runStart * cellH,
                                originX + x * cellW, originY + y * cellH,
                                runDir, 0f
                        });
                    }
                    runStart = y;
                    runDir = dir;
                }
            }
        }

        float[] out = new float[segs.size() * EDGE_STRIDE];
        for (int i = 0; i < segs.size(); i++) {
            System.arraycopy(segs.get(i), 0, out, i * EDGE_STRIDE, EDGE_STRIDE);
        }
        return out;
    }

    /**
     * 生成包围盒的四条边段，供远距离简化档使用。
     */
    private static float[] boxEdges(float halfW, float halfH) {
        return new float[]{
                -halfW, halfH, halfW, halfH, 0f, 1f,      // 上
                -halfW, -halfH, halfW, -halfH, 0f, -1f,   // 下
                -halfW, -halfH, -halfW, halfH, -1f, 0f,   // 左
                halfW, -halfH, halfW, halfH, 1f, 0f       // 右
        };
    }

    // ==================== 第七步：闪点锚位 ====================

    /**
     * 从距轮廓外足够远的格里挑几个作为闪点锚位。
     * <p>
     * 按固定步长跨着取，让它们在盾面上散开而不是挤成一堆。
     * 一个都挑不出来（形状太细）时退回几何中心。
     * </p>
     */
    private static float[] pickAnchors(boolean[] mask, int[] dist,
                                       float originX, float originY, float cellW, float cellH) {
        List<float[]> pool = new ArrayList<>();
        for (int y = 0; y < RES; y++) {
            for (int x = 0; x < RES; x++) {
                int idx = y * RES + x;
                if (mask[idx] && dist[idx] >= ANCHOR_MIN_DIST) {
                    pool.add(new float[]{
                            originX + (x + 0.5f) * cellW,
                            originY + (y + 0.5f) * cellH
                    });
                }
            }
        }
        if (pool.isEmpty()) {
            return new float[]{0f, 0f};
        }
        int take = Math.min(MAX_ANCHORS, pool.size());
        int step = Math.max(1, pool.size() / take);
        float[] out = new float[take * ANCHOR_STRIDE];
        for (int i = 0; i < take; i++) {
            float[] p = pool.get(Math.min(pool.size() - 1, i * step));
            out[i * ANCHOR_STRIDE] = p[0];
            out[i * ANCHOR_STRIDE + 1] = p[1];
        }
        return out;
    }
}
