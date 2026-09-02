package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 护盾类附魔的<b>物品局部空间</b>特效渲染器（纯客户端自绘）。
 * <p>
 * 负责魔力盾牌（魔力蓝护鞘）与不变盾牌（黄金树金护鞘）两个演出。
 * 由 {@code MixinItemInHandLayer}（第三人称）与
 * {@code MixinItemInHandRenderer}（第一人称）共同调用，两边视觉完全一致。
 * </p>
 *
 * <h2>v6.0：护鞘沿真实轮廓，不再是方盒</h2>
 * <p>
 * 几何全部由 {@link ShieldWardShape} 预先烘焙：真实轮廓、按距离场分级合并的面片、
 * 轮廓边段、闪点锚位。<b>每个模型只烘焙一次</b>，本类每帧只是遍历烘焙好的数组填顶点。
 * </p>
 * <p>
 * 之前是按包围盒画一个长方体壳。盾牌大多上圆下尖，方盒套上去底部两侧会露出大片空白护鞘。
 * 现在护鞘沿着盾的实际边缘走。
 * </p>
 *
 * <h3>余量用「绕中心缩放」而不是「按边外扩」</h3>
 * <p>
 * 护鞘要比盾大一圈（{@link #SHELL_MARGIN}），否则会与盾面 z-fighting。
 * 若把每条轮廓边段各自沿法线往外推，<b>凸角处相邻两段会裂开</b>，
 * 盾的每个折角都留一道缺口。改为对整个轮廓做绕中心的缩放，天然不裂。
 * </p>
 * <p>
 * 代价是余量在窄处略小于宽处（同一个缩放比例作用在不同半径上），
 * 但盾牌的长宽比不极端，肉眼看不出。
 * </p>
 *
 * <h3>配色</h3>
 * <ul>
 *     <li>只有魔力盾牌 —— 全程魔力蓝；</li>
 *     <li>只有不变盾牌 —— 全程黄金树金；</li>
 *     <li><b>两个都有</b> —— 在金与蓝之间来回插值，像呼吸灯一样缓慢切换。</li>
 * </ul>
 * <p>
 * 不变盾牌原来的「完全静止石壁」在 v5.0 换掉了，两个附魔的区分<b>只剩颜色</b>。
 * 金蓝色相差得够远，日常够用，但色弱或强环境光下辨识度不如从前。
 * </p>
 *
 * <h3>性能：这是常驻开销</h3>
 * <p>
 * 触发条件是「拿在手上」而非「举盾」，视野里每个持附魔盾的实体每帧都要画一份。
 * 沿轮廓画比方盒贵得多——轮廓边段有几十条，而描边、侧缘、背面描边、发光晕
 * 各要用它画一遍。控制手段：
 * </p>
 * <ul>
 *     <li>几何烘焙一次即缓存，每帧只填顶点，CPU 那头几乎不涨；</li>
 *     <li>{@link #SIMPLIFY_DISTANCE} 外<b>改用包围盒的四条边</b>，
 *         边段数从几十掉到 4，并去掉面片、辉光与闪点；</li>
 *     <li>{@link #CULL_DISTANCE} 外整个不画。</li>
 * </ul>
 * <p>
 * <b>嫌近距离太贵</b>：把 {@code ShieldWardShape.RES} 从 16 降到 12，
 * 轮廓边段数大约掉三分之一；或把 {@link #BACK_OUTLINE_RATIO} 设为 0，
 * 直接省掉一整遍描边。
 * </p>
 *
 * @author FlameForge
 * @version 6.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ShieldWardRenderer {

    /** 魔力盾牌附魔的注册 id */
    private static final String SCHOLAR_SHIELD_ID = "scholar_shield";
    /** 不变盾牌附魔的注册 id */
    private static final String IMMUTABLE_SHIELD_ID = "immutable_shield";

    private static final float TAU = (float) (Math.PI * 2.0);

    /**
     * 渲染器起始墙钟毫秒（类加载时固定）。
     * <p>动画时间必须用差值再转 float：直接 {@code currentTimeMillis()/1000f} 数值约 1.7e9，
     * 超出 float 有效精度，逐帧算出的时间会完全相同、动画彻底静止。</p>
     */
    private static final long START_MILLIS = System.currentTimeMillis();

    // ==================== 距离分级 ====================

    /**
     * 超过这个距离（格）改用包围盒的四条边，并去掉面片、辉光与闪点。
     * <p>轮廓在这个距离外已经分不清形状，而它恰恰是最贵的部分。
     * 亮线与发光晕留着——远距离靠它们传达「带附魔、什么颜色」。</p>
     * <p>取 12 格：近战交手距离之内保持完整效果。</p>
     */
    private static final float SIMPLIFY_DISTANCE = 12f;
    /** {@link #SIMPLIFY_DISTANCE} 的平方，用于免开方比较 */
    private static final double SIMPLIFY_DISTANCE_SQR = SIMPLIFY_DISTANCE * SIMPLIFY_DISTANCE;

    /** 超过这个距离（格）整个特效不画。这只是特效自己的剔除，实体视距由原版控制 */
    private static final float CULL_DISTANCE = 40f;
    /** {@link #CULL_DISTANCE} 的平方，用于免开方比较 */
    private static final double CULL_DISTANCE_SQR = CULL_DISTANCE * CULL_DISTANCE;

    // ==================== 余量与登场 ====================

    /**
     * 护鞘相对盾牌表面向外扩出的余量（格）。
     * <p>取 0.038（约 0.6 个模型单位）。<b>嫌护鞘比盾大一圈就调小</b>，
     * 0.022 左右会贴得紧不少。</p>
     */
    private static final float SHELL_MARGIN = 0.038f;
    /** 展开度为 0 时仍保留的最小余量比例（纯 0 会与盾面共面、z-fighting） */
    private static final float MARGIN_FLOOR_RATIO = 0.18f;

    /** 切出盾牌后展开到满所需时间（秒） */
    private static final float APPEAR_SECONDS = 0.3f;
    /** 单帧允许推进的最大时间（秒）：防止卡顿后一帧跳完动画 */
    private static final float MAX_STEP_SECONDS = 0.1f;

    // ==================== 配色 ====================

    /** 魔力白心：辉光条纹、闪点与轮廓高光 */
    private static final int MANA_CORE = 0xE4F2FF;
    /** 魔力蓝：主色。比卡利亚辉石蓝 0x8FD2FF 更深更饱和 */
    private static final int MANA_MAIN = 0x4E9BFF;
    /** 黄金白心：偏暖的高光，不用纯白，否则和魔力那套的白心分不开 */
    private static final int GOLD_CORE = 0xFFF3D2;
    /** 黄金树金：主色 */
    private static final int GOLD_MAIN = 0xF0C246;

    /** 金蓝切换的完整周期（秒）：金 → 蓝 → 金算一轮 */
    private static final float SWITCH_PERIOD_SECONDS = 5f;
    /** 由周期换算出的角速度（弧度/秒） */
    private static final float SWITCH_OMEGA = TAU / SWITCH_PERIOD_SECONDS;

    // ==================== 不透明度与线宽 ====================

    /** 面片底色在最靠内处的不透明度 */
    private static final float FACE_ALPHA = 0.2f;
    /** 侧缘带不透明度：整个演出里最亮的一圈，「包住」的观感全靠它 */
    private static final float RIM_ALPHA = 0.55f;
    /** 侧缘后缘相对前缘的亮度比：前亮后暗，侧看才有光泽 */
    private static final float RIM_BACK_RATIO = 0.35f;
    /** 轮廓亮线不透明度 */
    private static final float OUTLINE_ALPHA = 0.85f;
    /**
     * 背面轮廓相对正面的亮度比。
     * <p>斜看时前后两圈会分离成两条平行亮线。不想要就设 0——
     * 顺带省掉一整遍描边，是最见效的减负开关。</p>
     */
    private static final float BACK_OUTLINE_RATIO = 0.5f;
    /** 轮廓线半宽（格） */
    private static final float OUTLINE_HALF = 0.016f;

    // ==================== 外发光晕 ====================

    /** 发光晕向外延伸的宽度（格） */
    private static final float HALO_WIDTH = 0.07f;
    /**
     * 发光晕贴着护鞘那一侧的不透明度，向外衰减到 0。
     * <p>刻意压得比侧缘低不少：它的作用是软化边界，不是再加一圈亮边。
     * 调高会盖掉盾牌本体的轮廓。</p>
     */
    private static final float HALO_ALPHA = 0.3f;

    // ==================== 辉石闪点 ====================

    /** 盾面上同时存在的闪点数量。多了会变成满天星，失去「偶尔闪一下」的贵重感 */
    private static final int SPARK_COUNT = 3;
    /** 单个闪点的臂长（格） */
    private static final float SPARK_ARM = 0.075f;
    /** 闪点线半宽（格） */
    private static final float SPARK_HALF = 0.008f;
    /** 闪点峰值亮度 */
    private static final float SPARK_ALPHA = 0.95f;
    /** 闪点完整明灭周期（秒） */
    private static final float SPARK_PERIOD_SECONDS = 1.9f;
    /** 由周期换算出的角速度（弧度/秒） */
    private static final float SPARK_OMEGA = TAU / SPARK_PERIOD_SECONDS;

    // ==================== 辉光条纹 ====================

    /** 辉光条纹的空间周期（格）：越小条纹越密 */
    private static final float GLINT_PERIOD = 0.62f;
    /** 辉光条纹流动速度（周期/秒） */
    private static final float GLINT_SPEED = 0.42f;
    /** 第二层条纹的相对速度：与第一层不成整数比，叠出来才不像单调的扫描线 */
    private static final float GLINT_SPEED_2 = -0.27f;
    /** 条纹峰值亮度 */
    private static final float GLINT_STRENGTH = 0.75f;
    /** 第二层条纹的权重 */
    private static final float GLINT_SECOND_WEIGHT = 0.6f;
    /** 条纹强度低于此值的小块不提交顶点 */
    private static final float GLINT_CUTOFF = 0.03f;
    /**
     * 辉光取样的步长（格）。
     * <p>
     * 面片经过贪心合并，大小很不均匀——内部可能是一整块大矩形。
     * 若按矩形四角取辉光，大矩形里的条纹会被拉成一片模糊。
     * 故在<b>渲染时</b>按固定步长把每块矩形再切一遍，
     * 保证条纹细度与矩形大小无关。
     * </p>
     */
    private static final float GLINT_STEP = 0.12f;
    /** 单块面片在一个方向上最多切几段，防止异常大的矩形炸出过多顶点 */
    private static final int GLINT_MAX_SPLIT = 8;

    /** 边缘呼吸速度（与金蓝切换无关，这是亮度上的轻微起伏） */
    private static final float BREATH_SPEED = 1.5f;

    /**
     * 彩色的膜是否正反两面都画。
     *
     * <h4>默认只画外侧</h4>
     * <p>
     * 膜固定画在盾牌<b>外侧</b>（模型的 +Z 面），不随视角翻转。
     * 好处是同一面盾在任何角度下表现完全一致，不会出现「转个角度就没了」；
     * 代价是从盾牌背面看只有描边和侧缘。魔力裹在盾面上、背面是手臂那一侧，
     * 这个取舍在观感上也说得通。
     * </p>
     * <p>
     * <b>什么时候需要打开</b>：某些物品的手持参数会把模型整个转过去，
     * 使 +Z 背对观察者——那时正面看也会没有膜，只剩一圈边有颜色。
     * 遇到这种盾把本值改成 {@code true}，两面各画一层，从哪边看都有。
     * </p>
     * <p>
     * 代价是面片与辉光各翻一倍，近距离每面盾从约 180 个图元涨到约 240。
     * </p>
     */
    private static final boolean FILM_BOTH_SIDES = false;

    /**
     * 膜内部各层之间的 Z 间距（格）。
     * <p>辉光要压在面片之上、闪点又要压在辉光之上，否则会被糊掉。
     * 取值只需大到能避免 z-fighting，太大会让分层肉眼可见。</p>
     */
    private static final float LAYER_STEP = 0.0015f;

    // ==================== 调试 ====================

    /**
     * 调试开关：在护鞘的<b>坐标原点</b>画一个红色十字准星。
     * <p>
     * 用来把「变换错了」和「数据错了」分开：
     * </p>
     * <ul>
     *     <li>十字<b>落在盾牌中心</b> → 变换链是对的，问题在轮廓数据怎么用；</li>
     *     <li>十字<b>跟着护鞘一起跑偏</b> → 变换本身就错了，轮廓再准也没用。</li>
     * </ul>
     * <p><b>排查完请改回 {@code false}。</b></p>
     */
    private static final boolean DEBUG_ORIGIN_MARKER = false;
    /** 准星颜色：正红，与蓝金两套配色都不撞，一眼能认出来 */
    private static final float[] DEBUG_MARKER_COLOR = {1f, 0.12f, 0.12f};
    /** 准星单臂长度（格） */
    private static final float DEBUG_MARKER_ARM = 0.16f;
    /** 准星线半宽（格） */
    private static final float DEBUG_MARKER_HALF = 0.012f;

    // ===== 预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    private static final float[] C_MANA_CORE = VisualColor.constant(MANA_CORE);
    private static final float[] C_MANA_MAIN = VisualColor.constant(MANA_MAIN);
    private static final float[] C_GOLD_CORE = VisualColor.constant(GOLD_CORE);
    private static final float[] C_GOLD_MAIN = VisualColor.constant(GOLD_MAIN);

    // ===== 每帧复用的暂存区（⚠ 可写）=====
    // 绘制只在渲染线程发生，读完即用完，不存在跨帧持有，复用即可避免每帧分配。
    /** 金蓝插值后的高光色 */
    private static final float[] MIX_CORE = new float[3];
    /** 金蓝插值后的主色 */
    private static final float[] MIX_MAIN = new float[3];

    /** 魔力盾牌附魔懒解析缓存 */
    private static Enchantment scholarCache;
    private static boolean scholarResolved;
    /** 不变盾牌附魔懒解析缓存 */
    private static Enchantment immutableCache;
    private static boolean immutableResolved;

    /**
     * 每面盾的展开状态。
     * <p>
     * <b>键是「实体 id × 16 + 显示上下文序号」而不是单纯的实体 id</b>：
     * 主手与副手可能同时各挂一面附魔盾，共用条目会让展开速度翻倍。
     * {@code ItemDisplayContext} 的序号恒小于 16，乘 16 后不会与相邻实体串号。
     * </p>
     */
    private static final Map<Integer, WardState> STATE = new HashMap<>();

    /** 状态条目多久没被访问就算过期（毫秒） */
    private static final long STATE_EXPIRY_MS = 3000L;

    /** 一面盾的展开状态 */
    private static final class WardState {
        /** 展开度 0~1 */
        float value;
        /** 上次更新的墙钟毫秒 */
        long lastUpdate;
    }

    private ShieldWardRenderer() {
    }

    /**
     * 由 Mixin 调用：若该物品带护盾类附魔，就在物品局部空间画上包住盾牌的护鞘。
     * <p>
     * <b>不要求正在举盾</b>——带附魔且拿在手上即可。「拿在手上」由调用点保证：
     * 两个 Mixin 分别注入第一 / 第三人称的手持物品渲染，
     * 物品栏、掉落物、展示框等路径根本不会调到这里。
     * </p>
     * <p>
     * <b>调用时 PoseStack 必须仍处于「手臂空间」</b>
     * （两个 Mixin 都注入在 {@code PoseStack.popPose()} 之前）。
     * 内部会自己套用物品模型变换，并自行保证 push / pop 配平。
     * </p>
     *
     * @param entity         持有者
     * @param stack          正在渲染的物品
     * @param displayContext 显示上下文（第一 / 第三人称、左 / 右手）
     * @param leftHand       是否为左手（模型变换需要据此镜像）
     * @param poseStack      当前姿态栈（手臂空间）
     * @param buffer         缓冲源
     */
    public static void renderOnItem(LivingEntity entity, ItemStack stack,
                                    ItemDisplayContext displayContext, boolean leftHand,
                                    PoseStack poseStack, MultiBufferSource buffer) {
        if (stack.isEmpty() || !stack.isEnchanted()) {
            return;
        }

        Enchantment scholar = resolveScholar();
        Enchantment immutable = resolveImmutable();
        boolean hasScholar = scholar != null
                && EnchantmentHelper.getItemEnchantmentLevel(scholar, stack) > 0;
        boolean hasImmutable = immutable != null
                && EnchantmentHelper.getItemEnchantmentLevel(immutable, stack) > 0;
        if (!hasScholar && !hasImmutable) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        // ===== 距离分级：第一人称是玩家自己的盾，永远走完整档 =====
        boolean firstPerson = displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean detailed = true;
        if (!firstPerson) {
            double distSqr = cameraDistanceSqr(mc, entity);
            if (distSqr > CULL_DISTANCE_SQR) {
                // 太远，整个不画。提前返回不会推进状态机——条目自然过期，
                // 重新靠近时从头展开一次，可以接受
                return;
            }
            detailed = distSqr <= SIMPLIFY_DISTANCE_SQR;
        }

        int stateKey = entity.getId() * 16 + displayContext.ordinal();
        float value = advanceState(stateKey);
        if (value <= 0.004f) {
            return;
        }

        BakedModel model = mc.getItemRenderer().getModel(stack, entity.level(), entity, entity.getId());
        if (model == null) {
            return;
        }
        ShieldWardShape.Baked shape = ShieldWardShape.of(model, stack, displayContext);
        if (shape.halfWidth <= 0.005f || shape.halfHeight <= 0.005f) {
            return;
        }

        poseStack.pushPose();
        try {
            // 与原版 ItemRenderer.render 的前两步完全一致（BEWLR 路径同样走这两步）。
            // ⚠ translate(-0.5,-0.5,-0.5) 是把模型「居中到原点」
            model.applyTransform(displayContext, poseStack, leftHand);
            poseStack.translate(-0.5f, -0.5f, -0.5f);
            // 挪到物品自身的几何中心：烘焙出来的坐标都是相对这里的
            poseStack.translate(shape.centerX, shape.centerY, 0f);

            // 余量：绕中心整体放大。按边外扩会在凸角处裂开，缩放不会
            float margin = SHELL_MARGIN * (MARGIN_FLOOR_RATIO + (1f - MARGIN_FLOOR_RATIO) * value);
            float sx = (shape.halfWidth + margin) / shape.halfWidth;
            float sy = (shape.halfHeight + margin) / shape.halfHeight;
            poseStack.scale(sx, sy, 1f);

            VertexConsumer vc = buffer.getBuffer(CarianStyleRenderTypes.SHIELD_WARD);
            Matrix4f matrix = poseStack.last().pose();
            float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;
            int seedId = entity.getId();

            float blend = resolveBlend(hasScholar, hasImmutable, time, seedId);
            mixPalette(blend);
            drawWard(vc, matrix, shape, value, time, seedId, detailed, margin);
        } finally {
            // 无论中途发生什么都必须配平，否则整个实体渲染的姿态栈会错乱
            poseStack.popPose();
        }
    }

    /**
     * 求实体到相机的距离平方（格）。用平方比较可以省掉每帧的开方。
     *
     * @return 距离平方；取不到相机时返回 0（保守地按最近处理）
     */
    private static double cameraDistanceSqr(Minecraft mc, LivingEntity entity) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) {
            return 0.0;
        }
        Vec3 pos = camera.getPosition();
        return entity.distanceToSqr(pos.x, pos.y, pos.z);
    }

    // ==================== 绘制总调度 ====================

    /**
     * 按当前档位画出整套护鞘。
     * <p>
     * 配色取自 {@link #MIX_CORE} / {@link #MIX_MAIN}，
     * <b>调用前必须先调 {@link #mixPalette}</b>。
     * </p>
     *
     * @param shape    烘焙好的几何（只读）
     * @param value    展开度 0~1
     * @param time     动画时间（秒）
     * @param seedId   实体网络 id（错开各实体的动画相位）
     * @param detailed 完整档（近距离 / 第一人称）为 true
     * @param margin   当前余量（格），仅用于把 Z 方向也撑开
     */
    private static void drawWard(VertexConsumer vc, Matrix4f m, ShieldWardShape.Baked shape,
                                 float value, float time, int seedId,
                                 boolean detailed, float margin) {
        float zf = shape.frontZ + margin;
        float zb = shape.backZ - margin;
        float alpha = value;
        // 边缘极缓慢呼吸，只是让护鞘「活着」，不是在跳动
        float breath = 0.88f + 0.12f * Mth.sin(time * BREATH_SPEED + seedId * 0.7f);

        // 完整档沿真实轮廓，简化档退回包围盒的四条边
        float[] edges = detailed ? shape.edges : shape.boxEdges;

        // 外侧在哪一面由烘焙时定，两条取几何的路结论相反，见 Baked#filmOnFront
        float outerZ = shape.filmOnFront ? zf : zb;
        float innerZ = shape.filmOnFront ? zb : zf;
        // 叠层方向要背离盾面，膜的各层才是往外堆而不是往盾里陷
        float step = shape.filmOnFront ? LAYER_STEP : -LAYER_STEP;

        // ===== 发光晕：最外层也最淡，先画，压在别的元素下面 =====
        drawHalo(vc, m, edges, outerZ, MIX_MAIN, HALO_ALPHA * alpha * breath);

        if (detailed) {
            drawFilm(vc, m, shape, outerZ, step, alpha, time, seedId);
            if (FILM_BOTH_SIDES) {
                drawFilm(vc, m, shape, innerZ, -step, alpha, time, seedId);
            }
        }

        // ===== 侧缘：把正背两面连起来，护鞘才有厚度。远近都画 =====
        // 外侧那一头更亮，侧看才有光泽
        drawRim(vc, m, edges, outerZ, innerZ, MIX_CORE,
                RIM_ALPHA * alpha * breath, RIM_ALPHA * RIM_BACK_RATIO * alpha);
        // ===== 描边：勾出轮廓，内外各一圈，外侧那圈更亮 =====
        drawOutline(vc, m, edges, outerZ, MIX_CORE, OUTLINE_ALPHA * alpha * breath);
        drawOutline(vc, m, edges, innerZ, MIX_MAIN,
                OUTLINE_ALPHA * BACK_OUTLINE_RATIO * alpha * breath);

        if (DEBUG_ORIGIN_MARKER) {
            drawOriginMarker(vc, m, outerZ + step * 8f);
        }
    }

    /**
     * 调试用：在坐标原点画一个红色十字准星。
     * <p>它画在护鞘的局部原点上，也就是物品几何中心应该在的位置。
     * 见 {@link #DEBUG_ORIGIN_MARKER}。</p>
     */
    private static void drawOriginMarker(VertexConsumer vc, Matrix4f m, float z) {
        line(vc, m, -DEBUG_MARKER_ARM, 0f, DEBUG_MARKER_ARM, 0f,
                z, DEBUG_MARKER_HALF, DEBUG_MARKER_COLOR, 1f, 1f);
        line(vc, m, 0f, -DEBUG_MARKER_ARM, 0f, DEBUG_MARKER_ARM,
                z, DEBUG_MARKER_HALF, DEBUG_MARKER_COLOR, 1f, 1f);
    }

    // ==================== 各元素 ====================

    /**
     * 画一层完整的「膜」：面片 + 辉光条纹 + 辉石闪点。
     * <p>三者必须按这个顺序、并逐层往外叠一点点，否则后画的会被先画的糊掉。</p>
     *
     * @param baseZ  这一层贴着的 Z 平面
     * @param step   叠层方向（外侧那层为正，背面那层要传负值）
     * @param alpha  展开度
     * @param time   动画时间（秒）
     * @param seedId 实体网络 id
     */
    private static void drawFilm(VertexConsumer vc, Matrix4f m, ShieldWardShape.Baked shape,
                                 float baseZ, float step, float alpha, float time, int seedId) {
        // ===== 面片：中心透、边缘实 =====
        drawFace(vc, m, shape, baseZ, MIX_MAIN, FACE_ALPHA * alpha);
        // ===== 辉光条纹：流体感 =====
        drawGlint(vc, m, shape, baseZ + step, MIX_CORE,
                GLINT_STRENGTH * alpha, time, seedId * 0.137f);
        // ===== 辉石闪点：结晶感。压在条纹之上，否则会被条纹糊掉 =====
        drawSparks(vc, m, shape, baseZ + step * 2f, MIX_CORE,
                SPARK_ALPHA * alpha, time, seedId);
    }

    /**
     * 铺面片。
     * <p>
     * 直接遍历烘焙好的矩形表，四角的不透明度由烘焙时算好的<b>边缘系数</b>决定
     * ——越靠近轮廓越实。
     * </p>
     * <p>
     * <b>边缘比中心实</b>是「膜」这个读法成立的关键：肥皂泡、能量罩、护盾，
     * 凡是薄膜类的东西都是边缘厚、中间透。若做成中心最亮，
     * 立刻就读回「一块发光的板子」了。
     * </p>
     *
     * @param baseAlpha 最靠内处的不透明度
     */
    private static void drawFace(VertexConsumer vc, Matrix4f m, ShieldWardShape.Baked shape,
                                 float z, float[] col, float baseAlpha) {
        if (baseAlpha <= 0.004f) {
            return;
        }
        float[] r = shape.faceRects;
        for (int i = 0; i + ShieldWardShape.RECT_STRIDE <= r.length; i += ShieldWardShape.RECT_STRIDE) {
            float x0 = r[i], y0 = r[i + 1], x1 = r[i + 2], y1 = r[i + 3];
            quad3(vc, m,
                    x0, y0, z, shade(r[i + 4], baseAlpha),
                    x1, y0, z, shade(r[i + 5], baseAlpha),
                    x1, y1, z, shade(r[i + 6], baseAlpha),
                    x0, y1, z, shade(r[i + 7], baseAlpha),
                    col);
        }
    }

    /**
     * 把边缘系数换算成不透明度。
     * <p>平方曲线让「实」集中在很靠边的一小圈，中间大面积保持通透——
     * 线性衰减会让整片都半亮，看着像一块毛玻璃而不是一层膜。</p>
     *
     * @param edge      边缘系数（0 = 最靠内，1 = 贴着轮廓）
     * @param baseAlpha 最靠内处的不透明度
     */
    private static float shade(float edge, float baseAlpha) {
        return baseAlpha * (0.5f + 1.3f * edge * edge);
    }

    /**
     * 在面片上叠一层流动的辉光条纹。
     * <p>
     * 面片经过贪心合并，大小很不均匀。若按矩形四角取辉光，
     * 大矩形里的条纹会被拉成一片模糊，所以这里按 {@link #GLINT_STEP}
     * 把每块矩形再切一遍，让条纹细度与矩形大小无关。
     * </p>
     * <p>只在强度足够时才提交顶点——大部分区域处在条纹之间的暗区。</p>
     *
     * @param strength 条纹峰值亮度（已含展开度）
     * @param seed     相位种子
     */
    private static void drawGlint(VertexConsumer vc, Matrix4f m, ShieldWardShape.Baked shape,
                                  float z, float[] col, float strength, float time, float seed) {
        if (strength <= 0.004f) {
            return;
        }
        float[] r = shape.faceRects;
        for (int i = 0; i + ShieldWardShape.RECT_STRIDE <= r.length; i += ShieldWardShape.RECT_STRIDE) {
            float rx0 = r[i], ry0 = r[i + 1], rx1 = r[i + 2], ry1 = r[i + 3];
            int nx = clampSplit((rx1 - rx0) / GLINT_STEP);
            int ny = clampSplit((ry1 - ry0) / GLINT_STEP);
            float stepX = (rx1 - rx0) / nx;
            float stepY = (ry1 - ry0) / ny;

            for (int gy = 0; gy < ny; gy++) {
                float y0 = ry0 + stepY * gy;
                float y1 = y0 + stepY;
                for (int gx = 0; gx < nx; gx++) {
                    float x0 = rx0 + stepX * gx;
                    float x1 = x0 + stepX;
                    float g00 = glint(x0, y0, time, seed);
                    float g10 = glint(x1, y0, time, seed);
                    float g11 = glint(x1, y1, time, seed);
                    float g01 = glint(x0, y1, time, seed);
                    float peak = Math.max(Math.max(g00, g10), Math.max(g11, g01));
                    if (peak <= GLINT_CUTOFF) {
                        continue;
                    }
                    quad3(vc, m,
                            x0, y0, z, g00 * strength,
                            x1, y0, z, g10 * strength,
                            x1, y1, z, g11 * strength,
                            x0, y1, z, g01 * strength,
                            col);
                }
            }
        }
    }

    /**
     * 把切分段数夹到 {@code [1, GLINT_MAX_SPLIT]}。
     */
    private static int clampSplit(float raw) {
        int n = (int) Math.ceil(raw);
        if (n < 1) {
            return 1;
        }
        return Math.min(GLINT_MAX_SPLIT, n);
    }

    /**
     * 沿轮廓画侧缘：把正面与背面沿每条轮廓边段连起来。
     * <p>
     * <b>这是「包住」观感的来源。</b>没有它，护鞘只是一片贴在盾面上的贴纸；
     * 有了它才读作一个套在盾外面的壳。也因此它是整个演出最亮的部分，
     * 并且在简化档里被保留。
     * </p>
     * <p>前缘更亮、后缘更暗，从侧面看有光泽。</p>
     */
    private static void drawRim(VertexConsumer vc, Matrix4f m, float[] edges,
                                float zNear, float zFar, float[] col,
                                float alphaFront, float alphaBack) {
        if (alphaFront <= 0.004f && alphaBack <= 0.004f) {
            return;
        }
        for (int i = 0; i + ShieldWardShape.EDGE_STRIDE <= edges.length; i += ShieldWardShape.EDGE_STRIDE) {
            float x0 = edges[i], y0 = edges[i + 1];
            float x1 = edges[i + 2], y1 = edges[i + 3];
            quad3(vc, m,
                    x0, y0, zFar, alphaBack,
                    x0, y0, zNear, alphaFront,
                    x1, y1, zNear, alphaFront,
                    x1, y1, zFar, alphaBack, col);
        }
    }

    /**
     * 沿轮廓画一圈亮线。
     */
    private static void drawOutline(VertexConsumer vc, Matrix4f m, float[] edges,
                                    float z, float[] col, float alpha) {
        if (alpha <= 0.004f) {
            return;
        }
        for (int i = 0; i + ShieldWardShape.EDGE_STRIDE <= edges.length; i += ShieldWardShape.EDGE_STRIDE) {
            line(vc, m, edges[i], edges[i + 1], edges[i + 2], edges[i + 3],
                    z, OUTLINE_HALF, col, alpha, alpha);
        }
    }

    /**
     * 沿轮廓向外铺一圈发光晕，向外衰减到全透明。
     * <p>
     * <b>作用是软化边界，不是再加一圈亮边。</b>护盾之所以看着「硬」，
     * 是因为轮廓线之外立刻就是背景，没有过渡。补一层向外散开的雾，
     * 盾就从「一块发光的板」变成「一团裹着盾的魔力」。
     * </p>
     * <p>
     * <b>已知取舍</b>：每条边段各自沿自己的法线往外铺，
     * 因此凸角处相邻两片之间会留一个楔形缺口。
     * 由于外沿不透明度为 0，实际几乎看不出来；
     * 要补上就得额外生成角块，顶点量会明显上去，不值。
     * </p>
     *
     * @param alpha 贴着轮廓那一侧的不透明度
     */
    private static void drawHalo(VertexConsumer vc, Matrix4f m, float[] edges,
                                 float z, float[] col, float alpha) {
        if (alpha <= 0.004f) {
            return;
        }
        for (int i = 0; i + ShieldWardShape.EDGE_STRIDE <= edges.length; i += ShieldWardShape.EDGE_STRIDE) {
            float x0 = edges[i], y0 = edges[i + 1];
            float x1 = edges[i + 2], y1 = edges[i + 3];
            float nx = edges[i + 4] * HALO_WIDTH;
            float ny = edges[i + 5] * HALO_WIDTH;
            quad3(vc, m,
                    x0, y0, z, alpha,
                    x1, y1, z, alpha,
                    x1 + nx, y1 + ny, z, 0f,
                    x0 + nx, y0 + ny, z, 0f,
                    col);
        }
    }

    /**
     * 在盾面上画几个明灭的辉石闪点（十字星）。
     * <p>
     * <b>这是「结晶感」的唯一来源。</b>流动条纹表达的是流体——连续、有方向、不间断；
     * 而辉石是固体，视觉特征是<b>棱面偶然对上光时的一瞬爆闪</b>。
     * 两者叠在一起，护鞘才既像能量又像结晶。
     * </p>
     * <p>
     * 亮度用「正弦的正半周再取六次方」：一个周期里只有很短一段亮着。
     * <b>刻意不做成呼吸</b>——连续起伏读成「灯」，短促爆闪才读成「反光」。
     * </p>
     * <p>
     * 位置从烘焙好的锚位表里挑，那些锚位都在轮廓内部足够深的地方，
     * 因此闪点不会骑在边上、也不会戳出盾外。
     * </p>
     * <p>
     * 每个闪点是四条从中心射向四方的短线（中心亮、尖端透）。
     * 用四条半线而不是两条整线，是因为 {@link #line} 的不透明度只能给两个端点，
     * 整线做不出「中间最亮」。
     * </p>
     *
     * @param maxAlpha 闪点峰值不透明度
     */
    private static void drawSparks(VertexConsumer vc, Matrix4f m, ShieldWardShape.Baked shape,
                                   float z, float[] col, float maxAlpha, float time, int seedId) {
        if (maxAlpha <= 0.004f) {
            return;
        }
        int anchors = shape.anchorCount();
        if (anchors <= 0) {
            return;
        }
        for (int i = 0; i < SPARK_COUNT; i++) {
            // 相位错开，几个点不会一起闪
            float phase = hash01(seedId * 3 + i * 541) * TAU;
            float s = Mth.sin(time * SPARK_OMEGA + phase);
            if (s <= 0f) {
                // 正弦负半周整段不亮：暗的时间远长于亮的时间，这正是要的节奏
                continue;
            }
            float bright = pow6(s) * maxAlpha;
            if (bright <= 0.004f) {
                continue;
            }
            int pick = (int) (hash01(seedId * 7 + i * 131) * anchors);
            if (pick >= anchors) {
                pick = anchors - 1;
            }
            float cx = shape.sparkAnchors[pick * ShieldWardShape.ANCHOR_STRIDE];
            float cy = shape.sparkAnchors[pick * ShieldWardShape.ANCHOR_STRIDE + 1];

            line(vc, m, cx, cy, cx, cy + SPARK_ARM, z, SPARK_HALF, col, bright, 0f);
            line(vc, m, cx, cy, cx, cy - SPARK_ARM, z, SPARK_HALF, col, bright, 0f);
            line(vc, m, cx, cy, cx + SPARK_ARM, cy, z, SPARK_HALF, col, bright, 0f);
            line(vc, m, cx, cy, cx - SPARK_ARM, cy, z, SPARK_HALF, col, bright, 0f);
        }
    }

    // ==================== 数学小工具 ====================

    /**
     * 把一个整数打散成 {@code [0, 1)} 的伪随机数。
     * <p>要的是<b>确定性</b>——同一个输入永远得到同一个输出，
     * 这样闪点在帧与帧之间才是稳的；用 {@code Random} 反而要维护实例状态。</p>
     */
    private static float hash01(int n) {
        n = (n ^ 61) ^ (n >>> 16);
        n = n + (n << 3);
        n = n ^ (n >>> 4);
        n = n * 0x27D4EB2D;
        n = n ^ (n >>> 15);
        return (n & 0xFFFF) / 65536f;
    }

    /**
     * 六次方，展开成三次乘法（{@code x² → x⁴ → x⁶}）。
     * <p>替掉热路径上的 {@code Math.pow}——后者要处理任意实数指数，
     * 在每帧几十次调用的位置上是笔不必要的开销。
     * <b>条纹锐度与闪点明灭曲线都固定用它，改锐度就得改这个方法。</b></p>
     */
    private static float pow6(float x) {
        float x2 = x * x;
        float x4 = x2 * x2;
        return x4 * x2;
    }

    /**
     * 计算某点上的辉光条纹强度（0~1）。
     * <p>
     * 两层斜向条纹叠加：一层向右上流、一层反向且更慢。两者速度不成整数比，
     * 叠出来的图样永远不重复，观感就是原版附魔光效那种「说不清在往哪流」的闪动。
     * </p>
     * <p>单条纹用正弦的六次幂做窄峰——直接用 {@code sin} 会糊成一片渐变，读不出「条纹」。</p>
     */
    private static float glint(float x, float y, float time, float seed) {
        float p1 = (x * 0.7f + y) / GLINT_PERIOD - time * GLINT_SPEED + seed;
        float s1 = Math.abs(Mth.sin((float) Math.PI * p1));
        float p2 = (-x * 0.5f + y * 1.3f) / (GLINT_PERIOD * 1.7f) - time * GLINT_SPEED_2 + seed * 0.6f;
        float s2 = Math.abs(Mth.sin((float) Math.PI * p2));
        return Math.min(1f, pow6(s1) + GLINT_SECOND_WEIGHT * pow6(s2));
    }

    // ==================== 配色插值 ====================

    /**
     * 求当前的金蓝混合度。
     * <p>
     * <b>只有两个附魔同时存在时才会随时间变化。</b>单独装备任何一个都是恒定色——
     * 让单附魔也呼吸变色会白白牺牲「一眼认出是哪个附魔」的能力。
     * </p>
     * <p>
     * 正弦本身在两端就比中间停留得久，再叠一层 smoothstep 把这个特性放大：
     * 金和蓝各自停留更久，中间那段脏兮兮的青绿色过渡尽快掠过。
     * </p>
     * <p>
     * 相位混入实体 id，各玩家的切换不同步——否则一群人会齐刷刷一起变色，像同一个开关控的。
     * </p>
     *
     * @return 0 = 纯魔力蓝，1 = 纯黄金树金
     */
    private static float resolveBlend(boolean hasScholar, boolean hasImmutable,
                                      float time, int seedId) {
        if (!hasScholar) {
            return 1f;
        }
        if (!hasImmutable) {
            return 0f;
        }
        float t = 0.5f + 0.5f * Mth.sin(time * SWITCH_OMEGA + seedId * 0.41f);
        return t * t * (3f - 2f * t);
    }

    /**
     * 按混合度把配色插值进暂存区。
     * <p>必须在每次绘制前调用——暂存区是复用的，里面留的是上一次的结果。</p>
     */
    private static void mixPalette(float blend) {
        lerpColor(MIX_CORE, C_MANA_CORE, C_GOLD_CORE, blend);
        lerpColor(MIX_MAIN, C_MANA_MAIN, C_GOLD_MAIN, blend);
    }

    /**
     * 逐分量线性插值两个颜色。
     * <p>在线性 RGB 上插值即可：中间那段本来就要快速掠过，
     * 不值得为它引入 HSV 转换的开销。</p>
     *
     * @param dst 结果暂存区（会被覆写）
     */
    private static void lerpColor(float[] dst, float[] a, float[] b, float t) {
        dst[0] = a[0] + (b[0] - a[0]) * t;
        dst[1] = a[1] + (b[1] - a[1]) * t;
        dst[2] = a[2] + (b[2] - a[2]) * t;
    }

    // ==================== 状态机 ====================

    /**
     * 推进并返回某面盾的展开度。
     * <p>
     * 用<b>墙钟差值</b>而非 tick 驱动——TPS 波动时展开速度不该跟着变。
     * 单帧推进量夹在 {@link #MAX_STEP_SECONDS} 以内，防止卡顿后一帧跳完。
     * </p>
     * <p>
     * <b>只升不降。</b>能走到这里就意味着盾正拿在手上；
     * 而收起盾牌时 {@code renderOnItem} 根本不会被调用，
     * 没有任何时机可以驱动淡出——条目由 {@link #onClientTick} 按过期时间清掉。
     * </p>
     *
     * @param stateKey 状态键（实体 id × 16 + 显示上下文序号）
     * @return 展开度 0~1
     */
    private static float advanceState(int stateKey) {
        long now = System.currentTimeMillis();
        WardState st = STATE.get(stateKey);
        if (st == null) {
            st = new WardState();
            st.value = 0f;
            st.lastUpdate = now;
            STATE.put(stateKey, st);
        }
        float dt = (now - st.lastUpdate) / 1000f;
        if (dt < 0f) {
            dt = 0f;
        } else if (dt > MAX_STEP_SECONDS) {
            dt = MAX_STEP_SECONDS;
        }
        st.lastUpdate = now;

        if (st.value < 1f) {
            st.value = Math.min(1f, st.value + dt / APPEAR_SECONDS);
        }
        return st.value;
    }

    /**
     * 客户端 tick：离开世界时清空全部缓存；否则定期清理长时间未被访问的状态条目。
     * <p>实体死亡 / 卸载、玩家换下盾牌、或走出 {@link #CULL_DISTANCE} 之后
     * 都不会再触发渲染，其条目会永远留在表里，故需要这条兜底清理。</p>
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            STATE.clear();
            ShieldWardShape.clearCache();
            return;
        }
        if (STATE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, WardState>> it = STATE.entrySet().iterator();
        while (it.hasNext()) {
            WardState st = it.next().getValue();
            if (now - st.lastUpdate > STATE_EXPIRY_MS) {
                it.remove();
            }
        }
    }

    // ==================== 附魔解析 ====================

    /**
     * 懒解析魔力盾牌附魔（注册表在 mod 加载后才可用）。
     * <p>仅在成功解析后才标记完成，否则下次重试——避免把 null 固化下来。</p>
     *
     * @return 附魔对象；未注册（如被配置禁用）时返回 null
     */
    @Nullable
    private static Enchantment resolveScholar() {
        if (!scholarResolved) {
            scholarCache = ForgeRegistries.ENCHANTMENTS.getValue(
                    new ResourceLocation(Reference.MOD_ID, SCHOLAR_SHIELD_ID));
            scholarResolved = (scholarCache != null);
        }
        return scholarCache;
    }

    /**
     * 懒解析不变盾牌附魔。
     *
     * @return 附魔对象；未注册时返回 null
     */
    @Nullable
    private static Enchantment resolveImmutable() {
        if (!immutableResolved) {
            immutableCache = ForgeRegistries.ENCHANTMENTS.getValue(
                    new ResourceLocation(Reference.MOD_ID, IMMUTABLE_SHIELD_ID));
            immutableResolved = (immutableCache != null);
        }
        return immutableCache;
    }

    // ==================== 基础几何基元 ====================
    // 坐标以物品几何中心为原点，1 单位 = 1 格，+Z 为正面（外侧）。

    /**
     * 任意三维四边形，四个顶点的不透明度可分别指定。
     * <p>渲染类型已开启双面（{@code NO_CULL}），缠绕方向无所谓。</p>
     */
    private static void quad3(VertexConsumer vc, Matrix4f m,
                              float x1, float y1, float z1, float a1,
                              float x2, float y2, float z2, float a2,
                              float x3, float y3, float z3, float a3,
                              float x4, float y4, float z4, float a4,
                              float[] col) {
        if (a1 <= 0.004f && a2 <= 0.004f && a3 <= 0.004f && a4 <= 0.004f) {
            return;
        }
        float r = col[0], g = col[1], b = col[2];
        vc.vertex(m, x1, y1, z1).color(r, g, b, a1).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, a2).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, a3).endVertex();

        vc.vertex(m, x1, y1, z1).color(r, g, b, a1).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, a3).endVertex();
        vc.vertex(m, x4, y4, z4).color(r, g, b, a4).endVertex();
    }

    /**
     * 同一 Z 平面上带宽度的线段（两端不透明度可不同）。
     *
     * @param half 线半宽
     */
    private static void line(VertexConsumer vc, Matrix4f m,
                             float x1, float y1, float x2, float y2, float z,
                             float half, float[] col, float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-6f) {
            return;
        }
        // 平面内的法线 × 半宽
        float nx = -dy / len * half;
        float ny = dx / len * half;
        quad3(vc, m,
                x1 + nx, y1 + ny, z, a1,
                x2 + nx, y2 + ny, z, a2,
                x2 - nx, y2 - ny, z, a2,
                x1 - nx, y1 - ny, z, a1,
                col);
    }
}
