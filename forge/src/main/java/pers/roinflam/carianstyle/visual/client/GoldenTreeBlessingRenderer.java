package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 黄金树祝福（立誓 / 恩惠 / 庇护）客户端渲染器（纯客户端自绘）。
 * <p>
 * 三个效果均为「攻击或被攻击时触发，持续 [等级]×2.5 秒」的短时增益，判定依据分别为：
 * {@code hasEffect(GOLDEN_VOW)}、{@code hasEffect(BLESSING_OF_THE_ERDTREE)}、
 * {@code hasEffect(PROTECTION_OF_THE_ERDTREE)}，三者可同时生效。本渲染器统一绘制一套「神圣金光」
 * 基础视觉——脚下金色光晕、环绕降下的柔光光柱、飘落 / 升起的金色叶片、脚下缓慢旋转的符文刻度环、
 * 中央十字圣徽（沿用 {@code AuraGroundRenderer#motifHoly} 里「圣域」已经定好的十字圣徽母题，
 * 与自建光环视觉语言保持一致）；再按各自的激活状态叠加细节：
 * <ul>
 *     <li><b>黄金树立誓</b> —— 胸口处随心跳节奏脉动的白金光核；</li>
 *     <li><b>黄金树恩惠</b> —— 额外增加上升光尘的数量，表现「生息」感；</li>
 *     <li><b>黄金树庇护</b> —— 环绕身周的近白冷调护盾双环微光，与主体金色区分开来。</li>
 * </ul>
 * <p>
 * <b>v5（概念修正）：</b>此前版本用「头顶悬浮光环」表现神圣感，复盘后发现这是照搬西方宗教
 * 圣像的「光轮」符号，不是艾尔登法环黄金树 / 黄金律法自己的视觉语言——法环里反复出现、
 * 一眼就能认出「黄金树」的符号是<b>从地面蔓延生长的金色根须</b>，而不是头顶一个环。本版：
 * <ol>
 *     <li>撤掉头顶光环，改为 {@link #drawRootVeins}——从脚下向外递归分叉生长的金色根须纹样
 *         （带自然弯曲、逐级变细），直接对应法环环境美术里反复出现的黄金树根脉意象；</li>
 *     <li>{@link #drawRisingMotes} / {@link #drawDescendingMotes} 的光尘形状从正圆改为
 *         {@link #emitLeafMote} 水滴形金叶（带缓慢翻转），呼应「黄金树落叶」这一具体意象，
 *         而不是通用的「光点」；</li>
 *     <li>中央十字圣徽（{@link #drawCrossEmblem}）保留不动——它抄的是本项目
 *         {@code AuraGroundRenderer} 里「圣域」已经确立的母题，不是我另起的通用符号，
 *         还原度上没有问题。</li>
 * </ol>
 * </p>
 * <p>
 * <b>v6（补足"神圣感"）：</b>反馈是 v5 之后完全感受不到神圣氛围——根须 / 金叶都是偏「自然」
 * 的意象，缺一个让角色本体「由内而外发光」的元素；此前的「降下光柱」（绕在外圈自己转的
 * 几根竖线）被指出没有意义，予以整体移除。改为 {@link #drawRadiantGlow}——集中在胸口位置、
 * 由内而外三层叠加的柔光（外层大而淡 → 中层小而亮 → 核心一点纯白），直接表达「这个人正被
 * 神圣之力笼罩」，而不是让光效脱离角色本体自己在外面转。
 * </p>
 * <p>
 * <b>叠加强度：</b>同时生效的祝福数量 {@code activeCount ∈ [1,3]} 会整体放大视觉——光晕范围/
 * 透明度、胸口光辉尺寸、光尘数量、符文环转速与根须长度均随 {@code activeCount} 提升，主题色
 * 也从纯金逐渐向纯白过渡（象征祝福纯度更高）。三者同时生效时（{@code activeCount == 3}），
 * 额外触发脚下的「神圣脉冲」冲击波光环 + 长短交替光芒射线。
 * </p>
 * <p>
 * <b>v7（性能，视觉零变化）：</b>接入 {@link VisualBatch} 与 {@link SharedEntityQuery}——
 * <ul>
 *     <li>不再自行设置 / 恢复 GL 状态、不再自行 {@code begin/end} 顶点缓冲，改为向
 *         {@link VisualBatch} 提供的共享缓冲写顶点，由其在本帧末统一提交；</li>
 *     <li>不再自行做范围实体查询，改为遍历 {@link SharedEntityQuery} 的每帧共享列表，
 *         把原先的查询判定条件下沉为循环内的 {@code continue}（见 {@link #hasAnyBlessing}）。</li>
 * </ul>
 * 判定条件、{@code activeCount} 计算、精确平方距离裁剪、绘制顺序与全部几何参数均未改动。
 * </p>
 * <p>
 * 渲染管线与 {@code ScarletRotMistRenderer} 同款：{@link RenderLevelStageEvent} 的
 * {@code AFTER_TRANSLUCENT_BLOCKS} 阶段，{@code POSITION_COLOR} 纯顶点绘制，无贴图、无原版粒子；
 * 顶点格式与着色器现由 {@link VisualBatch} 统一设置。
 * </p>
 *
 * @author FlameForge
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class GoldenTreeBlessingRenderer {

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final int MOTE_SEGMENTS = 10;
    private static final long START_MILLIS = System.currentTimeMillis();

    // ===== 配色（0xRRGGBB）=====
    private static final int HOLY_GOLD = 0xFFC23A;
    private static final int HOLY_WHITE = 0xFFF6DC;
    private static final int HOLY_DEEP = 0xB8791A;
    /** 庇护护盾专用冷白色，与主体金色区分 */
    private static final int HOLY_SHIELD = 0xF2F7FF;

    // ===== 脚下金色光晕 =====
    private static final int HALO_SEGMENTS = 28;
    private static final float HALO_RADIUS_FACTOR = 0.9f;
    private static final float HALO_BASE_ALPHA = 0.24f;
    /** 每多一层祝福，光晕半径额外放大的比例 */
    private static final float HALO_STACK_RADIUS_STEP = 0.10f;
    /** 每多一层祝福，光晕透明度额外放大的比例 */
    private static final float HALO_STACK_ALPHA_STEP = 0.3f;

    // ===== 胸口神圣光辉（v6 替代直线光柱）：由内而外分层柔光，
    // 直接让角色本体"发光"，比绕在外圈的几根线更能读出"被神圣之力笼罩" =====
    private static final float GLOW_HEIGHT_FACTOR = 0.55f;
    private static final float GLOW_SIZE_FACTOR = 0.5f;
    private static final float GLOW_BASE_ALPHA = 0.4f;
    private static final float GLOW_STACK_ALPHA_STEP = 0.18f;
    private static final float GLOW_PULSE_SPEED = 1.3f;

    // ===== 上升金叶 =====
    private static final int BASE_MOTES = 9;
    private static final int BLESSING_EXTRA_MOTES = 4;
    private static final int MOTE_STACK_STEP = 4;
    private static final float RISE_SPEED = 0.16f;
    private static final float MOTE_SIZE = 0.09f;
    private static final float RISE_HEIGHT_FACTOR = 1.3f;
    private static final float SPREAD_FACTOR = 0.6f;
    private static final float MOTE_STACK_ALPHA_STEP = 0.25f;
    /** 金叶翻转速度（模拟叶片飘落时的自然转动） */
    private static final float LEAF_SPIN_SPEED = 2.2f;

    // ===== 脚下旋转符文环 =====
    private static final int RUNE_TICK_COUNT = 12;
    private static final float RUNE_ROT_SPEED = 0.25f;
    private static final float RUNE_BASE_ALPHA = 0.6f;
    private static final float RUNE_STACK_ALPHA_STEP = 0.12f;

    // ===== 中央十字圣徽（沿用「圣域」母题）=====
    private static final float CROSS_PULSE_SPEED = 2.0f;

    // ===== 黄金树根须纹样（v5 替代头顶光环，核心标志物）=====
    /** 主根数量（随祝福数量小幅增加） */
    private static final int ROOT_MAIN_COUNT = 5;
    private static final int ROOT_STACK_STEP = 1;
    /** 根须基础长度系数（× 实体宽度） */
    private static final float ROOT_LENGTH_FACTOR = 0.95f;
    /** 主根每段折线数（越多越自然弯曲，但顶点也越多） */
    private static final int ROOT_SEGMENTS = 4;
    /** 支根长度相对主根的比例 */
    private static final float ROOT_BRANCH_LENGTH_RATIO = 0.45f;
    private static final float ROOT_BASE_ALPHA = 0.42f;
    private static final float ROOT_STACK_ALPHA_STEP = 0.1f;

    // ===== 立誓：胸口脉动光核 =====
    private static final float VOW_PULSE_SPEED = 3.0f;

    // ===== 庇护：护盾双环 =====
    private static final int SHIELD_SEGMENTS = 32;
    private static final float SHIELD_PULSE_SPEED = 1.6f;

    // ===== 环绕的星芒微光（尺寸很小，不遮挡视野，只提升细节质感）=====
    private static final int SPARKLE_COUNT = 6;
    private static final int SPARKLE_STACK_STEP = 2;
    private static final float SPARKLE_ORBIT_SPEED = 0.4f;

    // ===== 三重祝福同时生效时的额外「神圣脉冲」（扩张环 + 长短交替光芒）=====
    private static final float PULSE_PERIOD = 1.8f;
    private static final int PULSE_WAVE_COUNT = 2;
    private static final float PULSE_MAX_RADIUS_FACTOR = 2.0f;
    private static final int PULSE_RAY_COUNT = 12;

    private GoldenTreeBlessingRenderer() {
    }

    /**
     * 世界渲染回调：在半透明方块之后，绘制相机附近所有携带黄金树祝福生物的神圣光效。
     * <p>
     * v7：GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理，实体列表取自
     * {@link SharedEntityQuery} 的每帧共享查询；本方法只负责筛选与写顶点。
     * </p>
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        // 共享批次未开启（世界未加载等）：直接跳过
        BufferBuilder builder = VisualBatch.builder();
        if (builder == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Vec3 cam = VisualBatch.cameraPosition();
        if (cam == null) {
            return;
        }
        List<LivingEntity> candidates = SharedEntityQuery.livingEntitiesNearCamera(mc, cam);
        if (candidates.isEmpty()) {
            return;
        }

        MobEffect vow = CarianStylePotion.GOLDEN_VOW.get();
        MobEffect blessing = CarianStylePotion.BLESSING_OF_THE_ERDTREE.get();
        MobEffect protection = CarianStylePotion.PROTECTION_OF_THE_ERDTREE.get();

        Matrix4f matrix = VisualBatch.matrix();
        float rightX = VisualBatch.rightX();
        float rightY = VisualBatch.rightY();
        float rightZ = VisualBatch.rightZ();
        float upX = VisualBatch.upX();
        float upY = VisualBatch.upY();
        float upZ = VisualBatch.upZ();

        float partial = VisualBatch.partialTick();
        float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;

        for (LivingEntity entity : candidates) {
            // v7：原先作为查询谓词的判定，现下沉为循环内筛选（共享列表已保证 isAlive）
            if (!hasAnyBlessing(entity, vow, blessing, protection)) {
                continue;
            }

            double ex = Mth.lerp((double) partial, entity.xo, entity.getX());
            double ey = Mth.lerp((double) partial, entity.yo, entity.getY());
            double ez = Mth.lerp((double) partial, entity.zo, entity.getZ());

            double dx = ex - cam.x;
            double dy = ey - cam.y;
            double dz = ez - cam.z;
            if (dx * dx + dy * dy + dz * dz > CULL_SQR) {
                continue;
            }

            boolean hasVow = vow != null && entity.hasEffect(vow);
            boolean hasBlessing = blessing != null && entity.hasEffect(blessing);
            boolean hasProtection = protection != null && entity.hasEffect(protection);
            // 同时生效的祝福数量（1~3），驱动整体视觉强度叠加
            int activeCount = (hasVow ? 1 : 0) + (hasBlessing ? 1 : 0) + (hasProtection ? 1 : 0);

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;

            drawGoldenHalo(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(), activeCount);
            drawRootVeins(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(), activeCount);
            drawRuneRing(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(), activeCount);
            drawCrossEmblem(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(), activeCount);
            drawRadiantGlow(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(), activeCount,
                    rightX, rightY, rightZ, upX, upY, upZ);
            drawRisingMotes(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(), hasBlessing,
                    activeCount, rightX, rightY, rightZ, upX, upY, upZ);
            drawDescendingMotes(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(), activeCount,
                    rightX, rightY, rightZ, upX, upY, upZ);

            if (hasVow) {
                drawVowCore(builder, matrix, rx, ryFoot, rz, height, time, entity.getId(),
                        rightX, rightY, rightZ, upX, upY, upZ);
            }
            if (hasProtection) {
                drawProtectionShield(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId());
            }
            drawSparkles(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(), activeCount,
                    rightX, rightY, rightZ, upX, upY, upZ);
            if (activeCount >= 3) {
                drawRadiantPulse(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId());
            }
        }
    }

    /**
     * 判断实体是否携带任一黄金树祝福（与优化前的查询谓词逐条一致）。
     *
     * @param entity     待判定实体
     * @param vow        黄金树立誓效果对象（可能为 null）
     * @param blessing   黄金树恩惠效果对象（可能为 null）
     * @param protection 黄金树庇护效果对象（可能为 null）
     * @return 携带任一祝福返回 true
     */
    private static boolean hasAnyBlessing(LivingEntity entity,
                                          @Nullable MobEffect vow,
                                          @Nullable MobEffect blessing,
                                          @Nullable MobEffect protection) {
        if (vow != null && entity.hasEffect(vow)) {
            return true;
        }
        if (blessing != null && entity.hasEffect(blessing)) {
            return true;
        }
        return protection != null && entity.hasEffect(protection);
    }

    /**
     * 脚下金色光晕：径向渐变圆盘（两层），缓慢呼吸。半径/透明度随 {@code activeCount} 小幅放大，
     * 主题色随 {@code activeCount} 从纯金向纯白过渡（象征祝福纯度更高）。
     */
    private static void drawGoldenHalo(BufferBuilder b, Matrix4f m,
                                       float cx, float cy, float cz, float width,
                                       float time, int seedId, int activeCount) {
        float stackAlphaMul = 1f + (activeCount - 1) * HALO_STACK_ALPHA_STEP;
        float stackRadiusMul = 1f + (activeCount - 1) * HALO_STACK_RADIUS_STEP;
        float breath = 0.85f + 0.15f * Mth.sin(time * 1.1f + seedId * 0.5f);
        float radius = width * HALO_RADIUS_FACTOR * stackRadiusMul * breath;

        int primary = lerpRgb(HOLY_GOLD, HOLY_WHITE, purityFactor(activeCount));
        float[] gold = unpack(primary);
        drawDisc(b, m, cx, cy, cz, radius, HALO_SEGMENTS, gold[0], gold[1], gold[2],
                clamp01(HALO_BASE_ALPHA * stackAlphaMul));

        float[] deep = unpack(HOLY_DEEP);
        drawDisc(b, m, cx, cy, cz, radius * 0.55f, HALO_SEGMENTS, deep[0], deep[1], deep[2],
                clamp01(HALO_BASE_ALPHA * stackAlphaMul * 0.55f));
    }

    /**
     * 黄金树根须纹样：从脚下向外递归分叉生长的金色根脉线条（主根 + 中途分出的支根，
     * 均带轻微自然弯曲、逐级变细变淡），直接对应法环环境美术里反复出现的黄金树根系意象——
     * 这是「黄金树」在法环里最具体、最容易一眼认出的符号，用来取代此前照抄西方宗教圣像
     * 「光轮」画出来的头顶光环。根须长度、数量、亮度随 {@code activeCount} 小幅提升。
     */
    private static void drawRootVeins(BufferBuilder b, Matrix4f m,
                                      float cx, float cy, float cz, float width,
                                      float time, int seedId, int activeCount) {
        int mainCount = ROOT_MAIN_COUNT + (activeCount - 1) * ROOT_STACK_STEP;
        float maxLen = width * ROOT_LENGTH_FACTOR * (1f + (activeCount - 1) * 0.1f);
        float alpha = clamp01((ROOT_BASE_ALPHA + (activeCount - 1) * ROOT_STACK_ALPHA_STEP)
                * (0.75f + 0.25f * Mth.sin(time * 0.7f + seedId)));
        float hw = Math.max(0.02f, width * 0.012f);
        int primary = lerpRgb(HOLY_GOLD, HOLY_WHITE, purityFactor(activeCount));
        float[] gold = unpack(primary);

        for (int i = 0; i < mainCount; i++) {
            long s = seedFor(seedId, i + 2000);
            float baseAngle = i * (TAU / mainCount) + rngFloat(s) * 0.4f;
            s = rngNext(s);
            drawRootBranch(b, m, cx, cz, cy, baseAngle, maxLen, 1, hw, gold, alpha, s);
        }
    }

    /**
     * 绘制一条根须（主干为带轻微随机弯曲的折线，中段可能分出一条更细更暗的支根）。
     * 支根通过 {@code depth} 控制最多递归一层，避免顶点数失控。
     */
    private static void drawRootBranch(BufferBuilder b, Matrix4f m, float cx, float cz, float cy,
                                       float angle, float length, int depth, float hw,
                                       float[] col, float alpha, long seed) {
        float px = cx, pz = cz;
        long s = seed;
        for (int i = 1; i <= ROOT_SEGMENTS; i++) {
            float t = (float) i / ROOT_SEGMENTS;
            s = rngNext(s);
            float wobble = (rngFloat(s) - 0.5f) * 0.35f;
            float ang = angle + wobble * t;
            float r = length * t;
            float x = cx + r * (float) Math.cos(ang);
            float z = cz + r * (float) Math.sin(ang);
            float segAlpha = alpha * (1f - t * 0.65f);
            float segHw = hw * (1f - t * 0.5f);
            lineF(b, m, px, pz, x, z, cy, segHw, col, segAlpha, segAlpha);
            px = x;
            pz = z;

            // 中段分出一条更细更暗的支根，只递归一层，避免顶点数爆炸
            if (depth > 0 && i == ROOT_SEGMENTS / 2) {
                s = rngNext(s);
                float branchAngle = ang + (rngFloat(s) - 0.5f) * 1.7f;
                drawRootBranch(b, m, x, z, cy, branchAngle, length * ROOT_BRANCH_LENGTH_RATIO,
                        depth - 1, hw * 0.6f, col, alpha * 0.7f, s);
            }
        }
    }

    /** 脚下缓慢旋转的金色符文刻度环，转速与亮度随 {@code activeCount} 小幅提升。 */
    private static void drawRuneRing(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz, float width,
                                     float time, int seedId, int activeCount) {
        float radius = width * HALO_RADIUS_FACTOR * 0.9f * (1f + (activeCount - 1) * HALO_STACK_RADIUS_STEP);
        float speedMul = 1f + (activeCount - 1) * 0.3f;
        float rot = time * RUNE_ROT_SPEED * speedMul + seedId * 0.3f;
        float alpha = clamp01(RUNE_BASE_ALPHA + (activeCount - 1) * RUNE_STACK_ALPHA_STEP);
        float[] c = unpack(HOLY_WHITE);
        float hw = Math.max(0.022f, width * 0.01f);
        for (int i = 0; i < RUNE_TICK_COUNT; i++) {
            double base = rot + TAU * i / RUNE_TICK_COUNT;
            float ix = cx + (float) Math.cos(base) * radius * 0.85f;
            float iz = cz + (float) Math.sin(base) * radius * 0.85f;
            float ox = cx + (float) Math.cos(base) * radius;
            float oz = cz + (float) Math.sin(base) * radius;
            line(b, m, ix, iz, ox, oz, cy, hw, c, alpha);
        }
    }

    /**
     * 脚下中央十字圣徽：沿用 {@code AuraGroundRenderer#motifHoly} 中「圣域」的十字圣徽母题
     * （两条垂直相交的粗线），随心跳缓慢明灭；长度、粗细、亮度随 {@code activeCount} 小幅提升，
     * 是三个祝福共享的核心标志性图案。此元素抄的是本项目已有的圣域母题，不是另起的通用符号。
     */
    private static void drawCrossEmblem(BufferBuilder b, Matrix4f m,
                                        float cx, float cy, float cz, float width,
                                        float time, int seedId, int activeCount) {
        float pulse = 0.6f + 0.4f * Mth.sin(time * CROSS_PULSE_SPEED + seedId);
        float len = width * (0.36f + (activeCount - 1) * 0.06f);
        float hw = Math.max(0.032f, width * 0.016f) * (1f + (activeCount - 1) * 0.15f);
        float alpha = clamp01((0.55f + (activeCount - 1) * 0.12f) * pulse);
        float[] c = unpack(HOLY_WHITE);

        line(b, m, cx - len, cz, cx + len, cz, cy, hw, c, alpha);
        line(b, m, cx, cz - len, cx, cz + len, cy, hw, c, alpha);
    }

    /**
     * 胸口神圣光辉（v6 替代「降下的光柱」）：由内而外三层叠加的柔光——外层大而淡、
     * 中层小而亮、核心一点纯白——集中在角色胸口位置，直接表达「这个人本身正被神圣之力
     * 笼罩、由内而外发光」，而不是像光柱那样只是几根线绕在外圈自己转，跟角色本体脱节。
     * 尺寸与亮度随 {@code activeCount} 小幅提升，主题色随 {@code activeCount} 从金向白过渡。
     */
    private static void drawRadiantGlow(BufferBuilder b, Matrix4f m,
                                        float cx, float cyFoot, float cz, float width, float height,
                                        float time, int seedId, int activeCount,
                                        float rightX, float rightY, float rightZ,
                                        float upX, float upY, float upZ) {
        float chestY = cyFoot + height * GLOW_HEIGHT_FACTOR;
        float pulse = 0.7f + 0.3f * Mth.sin(time * GLOW_PULSE_SPEED + seedId);
        float alpha = clamp01((GLOW_BASE_ALPHA + (activeCount - 1) * GLOW_STACK_ALPHA_STEP) * pulse);
        float size = width * GLOW_SIZE_FACTOR * (1f + (activeCount - 1) * 0.12f);

        int primary = lerpRgb(HOLY_GOLD, HOLY_WHITE, 0.4f + purityFactor(activeCount) * 0.4f);
        float[] gold = unpack(primary);
        float[] white = unpack(HOLY_WHITE);

        // 外层：大而淡，铺垫整体光晕范围
        emitSoftMote(b, m, cx, chestY, cz, size, gold[0], gold[1], gold[2], alpha * 0.45f,
                rightX, rightY, rightZ, upX, upY, upZ);
        // 中层：更小更亮，收拢焦点
        emitSoftMote(b, m, cx, chestY, cz, size * 0.55f, gold[0], gold[1], gold[2], alpha * 0.8f,
                rightX, rightY, rightZ, upX, upY, upZ);
        // 核心：一点纯白高光，是"由内而外发光"的视觉锚点
        emitSoftMote(b, m, cx, chestY, cz, size * 0.22f, white[0], white[1], white[2], alpha,
                rightX, rightY, rightZ, upX, upY, upZ);
    }

    /**
     * 徐徐升起的金叶（{@link #emitLeafMote} 水滴形，非正圆）；拥有「黄金树恩惠」时额外增加
     * 数量（表现生息感），同时数量与亮度随 {@code activeCount} 小幅提升。
     */
    private static void drawRisingMotes(BufferBuilder b, Matrix4f m,
                                        float cx, float cyFoot, float cz, float width, float height,
                                        float time, int seedId, boolean hasBlessing, int activeCount,
                                        float rightX, float rightY, float rightZ,
                                        float upX, float upY, float upZ) {
        int count = BASE_MOTES + (hasBlessing ? BLESSING_EXTRA_MOTES : 0) + (activeCount - 1) * MOTE_STACK_STEP;
        float alphaMul = 1f + (activeCount - 1) * MOTE_STACK_ALPHA_STEP;
        float riseHeight = height * RISE_HEIGHT_FACTOR;
        float spread = width * SPREAD_FACTOR;
        int primary = lerpRgb(HOLY_GOLD, HOLY_WHITE, purityFactor(activeCount));

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = 0.3f + 0.7f * rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.7f + 0.6f * rngFloat(s);
            s = rngNext(s);
            float twPhase = rngFloat(s) * TAU;
            s = rngNext(s);
            float spinPhase = rngFloat(s) * TAU;

            float t = frac(time * RISE_SPEED + phase);

            float env;
            if (t < 0.15f) {
                env = t / 0.15f;
            } else if (t > 0.65f) {
                env = 1f - (t - 0.65f) / 0.35f;
            } else {
                env = 1f;
            }
            if (env <= 0f) {
                continue;
            }
            float twinkle = 0.7f + 0.3f * Mth.sin(time * 2.2f + twPhase);
            float alpha = clamp01(0.5f * env * twinkle * alphaMul);
            if (alpha <= 0.01f) {
                continue;
            }

            float curRad = spread * radFactor * (0.6f + 0.4f * t);
            float px = cx + (float) Math.cos(ang) * curRad;
            float pz = cz + (float) Math.sin(ang) * curRad;
            float py = cyFoot + t * riseHeight + Y_OFFSET;

            int col = lerpRgb(primary, HOLY_WHITE, t);
            float[] c = unpack(col);
            float size = MOTE_SIZE * sizeRand * (1.1f - 0.3f * t);
            float rot = time * LEAF_SPIN_SPEED + spinPhase;

            emitLeafMote(b, m, px, py, pz, size, rot, c[0], c[1], c[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ);
        }
    }

    /**
     * 飘落金叶：自高处缓缓飘落的金色叶片（{@link #emitLeafMote}），速度、飘荡幅度均比
     * 脚下升起的更慢更柔，还原「黄金树周围落叶飘散」的经典法环意象——与脚下上升的叶片方向
     * 相反、一升一降，让整体光柱更有层次。数量随 {@code activeCount} 小幅增加。
     */
    private static void drawDescendingMotes(BufferBuilder b, Matrix4f m,
                                            float cx, float cyFoot, float cz, float width, float height,
                                            float time, int seedId, int activeCount,
                                            float rightX, float rightY, float rightZ,
                                            float upX, float upY, float upZ) {
        int count = 8 + (activeCount - 1) * 3;
        float startHeight = height * 1.9f;
        float spread = width * 0.65f;

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i + 1200);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = 0.3f + 0.7f * rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.6f + 0.7f * rngFloat(s);
            s = rngNext(s);
            float swayPhase = rngFloat(s) * TAU;
            s = rngNext(s);
            float spinPhase = rngFloat(s) * TAU;

            float t = frac(time * 0.1f + phase); // 0=高处 1=落地

            float env;
            if (t < 0.1f) {
                env = t / 0.1f;
            } else if (t > 0.85f) {
                env = 1f - (t - 0.85f) / 0.15f;
            } else {
                env = 1f;
            }
            if (env <= 0f) {
                continue;
            }

            float sway = (float) Math.sin(time * 0.8f + swayPhase) * 0.15f;
            float curRad = spread * radFactor;
            float px = cx + (float) Math.cos(ang) * curRad + sway;
            float pz = cz + (float) Math.sin(ang) * curRad;
            float py = cyFoot + startHeight * (1f - t) + Y_OFFSET;

            float twinkle = 0.6f + 0.4f * Mth.sin(time * 3f + i * 1.1f);
            float alpha = clamp01(0.45f * env * twinkle);
            int col = lerpRgb(HOLY_GOLD, HOLY_WHITE, 0.3f + 0.4f * twinkle);
            float[] c = unpack(col);
            float size = 0.075f * sizeRand;
            // 飘落的叶片翻转比升起的更慢，更有"打着转飘下来"的感觉
            float rot = time * (LEAF_SPIN_SPEED * 0.5f) + spinPhase;

            emitLeafMote(b, m, px, py, pz, size, rot, c[0], c[1], c[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ);
        }
    }

    /** 立誓：胸口处随心跳节奏脉动的白金光核。 */
    private static void drawVowCore(BufferBuilder b, Matrix4f m,
                                    float cx, float cyFoot, float cz, float height,
                                    float time, int seedId,
                                    float rightX, float rightY, float rightZ,
                                    float upX, float upY, float upZ) {
        float chestY = cyFoot + height * 0.6f;
        float pulse = 0.6f + 0.4f * Mth.sin(time * VOW_PULSE_SPEED + seedId);
        float size = 0.15f + 0.05f * pulse;
        float alpha = 0.5f + 0.3f * pulse;
        float[] c = unpack(HOLY_WHITE);
        emitSoftMote(b, m, cx, chestY, cz, size, c[0], c[1], c[2], alpha,
                rightX, rightY, rightZ, upX, upY, upZ);
    }

    /** 庇护：环绕身周的护盾双环微光（近白冷调，与主体金色区分开来）。 */
    private static void drawProtectionShield(BufferBuilder b, Matrix4f m,
                                             float cx, float cyFoot, float cz, float width, float height,
                                             float time, int seedId) {
        float midY = cyFoot + height * 0.5f;
        float pulse = 0.5f + 0.5f * Mth.sin(time * SHIELD_PULSE_SPEED + seedId * 0.8f);
        float radius = width * 0.7f + 0.05f * pulse;
        float[] c = unpack(HOLY_SHIELD);
        ringVertical(b, m, cx, cz, midY, radius, SHIELD_SEGMENTS, 0.04f, c, clamp01(0.32f * pulse + 0.14f));
    }

    /**
     * 环绕脚下的星芒微光：一圈缓慢公转、随机闪烁的小光点，尺寸很小（不遮挡视野），
     * 用来补足细节亮度、提升精致感，不会重新占用屏幕空间。数量随 {@code activeCount} 小幅增加。
     */
    private static void drawSparkles(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz, float width,
                                     float time, int seedId, int activeCount,
                                     float rightX, float rightY, float rightZ,
                                     float upX, float upY, float upZ) {
        float radius = width * HALO_RADIUS_FACTOR * 0.95f;
        float rot = time * SPARKLE_ORBIT_SPEED + seedId * 0.4f;
        int count = SPARKLE_COUNT + (activeCount - 1) * SPARKLE_STACK_STEP;
        float[] c = unpack(HOLY_WHITE);
        for (int i = 0; i < count; i++) {
            double ang = rot + TAU * i / count;
            float px = cx + (float) Math.cos(ang) * radius;
            float pz = cz + (float) Math.sin(ang) * radius;
            float twinkle = 0.4f + 0.6f * (0.5f + 0.5f * Mth.sin(time * 5f + i * 1.3f + seedId));
            float size = 0.045f + 0.025f * twinkle;
            emitSoftMote(b, m, px, cy + 0.05f, pz, size, c[0], c[1], c[2], 0.75f * twinkle,
                    rightX, rightY, rightZ, upX, upY, upZ);
        }
    }

    /**
     * 立誓 + 恩惠 + 庇护三重同时生效时的额外「神圣脉冲」：脚下周期性向外扩张并快速淡出的
     * 冲击波光环，首波额外叠加一圈长短交替的光芒射线（沿用「圣域」母题里长短交替光芒的手法），
     * 用于强调「三重祝福」这一叠加状态明显区别于单个或两个祝福。
     */
    private static void drawRadiantPulse(BufferBuilder b, Matrix4f m,
                                         float cx, float cy, float cz, float width,
                                         float time, int seedId) {
        float[] c = unpack(HOLY_WHITE);
        for (int i = 0; i < PULSE_WAVE_COUNT; i++) {
            float phase = (float) i / PULSE_WAVE_COUNT;
            float t = frac(time / PULSE_PERIOD + phase + seedId * 0.05f);
            float radius = width * PULSE_MAX_RADIUS_FACTOR * easeOutCubic(t);
            float alpha = clamp01((1f - t) * 0.45f);
            if (alpha <= 0.01f || radius <= 0.05f) {
                continue;
            }
            ringVertical(b, m, cx, cz, cy, radius, HALO_SEGMENTS, 0.07f, c, alpha);

            // 长短交替光芒：仅首波叠加射线，强化「神圣爆发」的观感
            if (i == 0) {
                float hw = Math.max(0.035f, width * 0.016f);
                for (int r = 0; r < PULSE_RAY_COUNT; r++) {
                    float rayLen = radius * ((r % 2 == 0) ? 1f : 0.6f);
                    double ang = TAU * r / PULSE_RAY_COUNT + seedId * 0.3f;
                    float ox = cx + (float) Math.cos(ang) * rayLen;
                    float oz = cz + (float) Math.sin(ang) * rayLen;
                    line(b, m, cx, cz, ox, oz, cy, hw, c, alpha * 0.6f);
                }
            }
        }
    }

    /** 一圈水平护盾/脉冲环（简化为固定高度处的圆环，随脉冲呼吸）。 */
    private static void ringVertical(BufferBuilder b, Matrix4f m,
                                     float cx, float cz, float cy, float radius, int segments,
                                     float halfWidth, float[] col, float alpha) {
        float rInner = Math.max(0f, radius - halfWidth);
        float rOuter = radius + halfWidth;
        for (int i = 0; i < segments; i++) {
            double a0 = (TAU * i) / segments;
            double a1 = (TAU * (i + 1)) / segments;
            float ox0 = cx + rOuter * (float) Math.cos(a0);
            float oz0 = cz + rOuter * (float) Math.sin(a0);
            float ox1 = cx + rOuter * (float) Math.cos(a1);
            float oz1 = cz + rOuter * (float) Math.sin(a1);
            float ix0 = cx + rInner * (float) Math.cos(a0);
            float iz0 = cz + rInner * (float) Math.sin(a0);
            float ix1 = cx + rInner * (float) Math.cos(a1);
            float iz1 = cz + rInner * (float) Math.sin(a1);

            b.vertex(m, ox0, cy, oz0).color(col[0], col[1], col[2], alpha).endVertex();
            b.vertex(m, ox1, cy, oz1).color(col[0], col[1], col[2], alpha).endVertex();
            b.vertex(m, ix1, cy, iz1).color(col[0], col[1], col[2], alpha).endVertex();

            b.vertex(m, ox0, cy, oz0).color(col[0], col[1], col[2], alpha).endVertex();
            b.vertex(m, ix1, cy, iz1).color(col[0], col[1], col[2], alpha).endVertex();
            b.vertex(m, ix0, cy, iz0).color(col[0], col[1], col[2], alpha).endVertex();
        }
    }

    private static void drawDisc(BufferBuilder b, Matrix4f m,
                                 float cx, float cy, float cz, float radius, int segments,
                                 float r, float g, float bl, float centerAlpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = (TAU * i) / segments;
            double a1 = (TAU * (i + 1)) / segments;
            float x0 = cx + radius * (float) Math.cos(a0);
            float z0 = cz + radius * (float) Math.sin(a0);
            float x1 = cx + radius * (float) Math.cos(a1);
            float z1 = cz + radius * (float) Math.sin(a1);
            b.vertex(m, cx, cy, cz).color(r, g, bl, centerAlpha).endVertex();
            b.vertex(m, x0, cy, z0).color(r, g, bl, 0f).endVertex();
            b.vertex(m, x1, cy, z1).color(r, g, bl, 0f).endVertex();
        }
    }

    private static void line(BufferBuilder b, Matrix4f m,
                             float x1, float z1, float x2, float z2, float y,
                             float hw, float[] col, float alpha) {
        lineF(b, m, x1, z1, x2, z2, y, hw, col, alpha, alpha);
    }

    /** 两端 alpha 可分别指定的线段（浮点颜色版，供根须等需要逐段渐隐的场景使用）。 */
    private static void lineF(BufferBuilder b, Matrix4f m,
                              float x1, float z1, float x2, float z2, float y,
                              float hw, float[] col, float a1, float a2) {
        double dx = x2 - x1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) {
            return;
        }
        double nx = -dz / len * hw;
        double nz = dx / len * hw;
        float ax1 = (float) (x1 + nx), az1 = (float) (z1 + nz);
        float ax2 = (float) (x1 - nx), az2 = (float) (z1 - nz);
        float bx1 = (float) (x2 + nx), bz1 = (float) (z2 + nz);
        float bx2 = (float) (x2 - nx), bz2 = (float) (z2 - nz);

        b.vertex(m, ax1, y, az1).color(col[0], col[1], col[2], a1).endVertex();
        b.vertex(m, bx1, y, bz1).color(col[0], col[1], col[2], a2).endVertex();
        b.vertex(m, bx2, y, bz2).color(col[0], col[1], col[2], a2).endVertex();

        b.vertex(m, ax1, y, az1).color(col[0], col[1], col[2], a1).endVertex();
        b.vertex(m, bx2, y, bz2).color(col[0], col[1], col[2], a2).endVertex();
        b.vertex(m, ax2, y, az2).color(col[0], col[1], col[2], a1).endVertex();
    }

    /**
     * 绘制一颗面向相机的柔和圆形光点（径向渐变：中心 alpha、边缘 0）。
     * 用于立誓光核、庇护相关的小圆点场景（非「金叶」意象的元素）。
     */
    private static void emitSoftMote(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz, float size,
                                     float r, float g, float bl, float alpha,
                                     float rightX, float rightY, float rightZ,
                                     float upX, float upY, float upZ) {
        float pex = 0f, pey = 0f, pez = 0f;
        for (int i = 0; i <= MOTE_SEGMENTS; i++) {
            float ang = TAU * i / MOTE_SEGMENTS;
            float ca = (float) Math.cos(ang) * size;
            float sa = (float) Math.sin(ang) * size;
            float ex = cx + rightX * ca + upX * sa;
            float ey = cy + rightY * ca + upY * sa;
            float ez = cz + rightZ * ca + upZ * sa;
            if (i > 0) {
                b.vertex(m, cx, cy, cz).color(r, g, bl, alpha).endVertex();
                b.vertex(m, pex, pey, pez).color(r, g, bl, 0f).endVertex();
                b.vertex(m, ex, ey, ez).color(r, g, bl, 0f).endVertex();
            }
            pex = ex;
            pey = ey;
            pez = ez;
        }
    }

    /**
     * 绘制一片面向相机的柔和「金叶」光点：billboard 平面内用 6 个轮廓点近似出水滴 / 杏仁形
     * （比正圆更贴合「叶片」的联想），支持绕视线方向旋转 {@code rot}，用于模拟叶片飘落时的
     * 自然翻转。中心不透明、边缘渐隐为 0。
     *
     * @param rot 叶片在 billboard 平面内的旋转角（弧度）
     */
    private static void emitLeafMote(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz, float size, float rot,
                                     float r, float g, float bl, float alpha,
                                     float rightX, float rightY, float rightZ,
                                     float upX, float upY, float upZ) {
        float[][] localPts = {
                {0f, size * 1.3f}, {size * 0.55f, size * 0.35f}, {size * 0.4f, -size * 0.7f},
                {0f, -size * 1.1f}, {-size * 0.4f, -size * 0.7f}, {-size * 0.55f, size * 0.35f}
        };
        float cosR = (float) Math.cos(rot), sinR = (float) Math.sin(rot);
        float[] wx = new float[6];
        float[] wy = new float[6];
        float[] wz = new float[6];
        for (int i = 0; i < 6; i++) {
            float lu = localPts[i][0] * cosR - localPts[i][1] * sinR;
            float lv = localPts[i][0] * sinR + localPts[i][1] * cosR;
            wx[i] = cx + rightX * lu + upX * lv;
            wy[i] = cy + rightY * lu + upY * lv;
            wz[i] = cz + rightZ * lu + upZ * lv;
        }
        for (int i = 0; i < 6; i++) {
            int j = (i + 1) % 6;
            b.vertex(m, cx, cy, cz).color(r, g, bl, alpha).endVertex();
            b.vertex(m, wx[i], wy[i], wz[i]).color(r, g, bl, 0f).endVertex();
            b.vertex(m, wx[j], wy[j], wz[j]).color(r, g, bl, 0f).endVertex();
        }
    }

    // ==================== 无分配伪随机（xorshift64） ====================

    private static long seedFor(int entityId, int index) {
        long s = (entityId * 0x9E3779B97F4A7C15L) ^ ((index + 1L) * 0x85EBCA6BL);
        s ^= (s >>> 29);
        return s == 0L ? 0x9E3779B97F4A7C15L : s;
    }

    private static long rngNext(long s) {
        s ^= s << 13;
        s ^= s >>> 7;
        s ^= s << 17;
        return s;
    }

    private static float rngFloat(long s) {
        return ((s >>> 40) & 0xFFFFFFL) / (float) 0x1000000;
    }

    // ==================== 数学 / 颜色辅助 ====================

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }

    /**
     * 「祝福纯度」系数：{@code activeCount} 越多，主题色越从纯金（{@link #HOLY_GOLD}）
     * 偏向纯白（{@link #HOLY_WHITE}），象征祝福叠得越满、能量越纯粹。
     *
     * @param activeCount 同时生效的祝福数量（1~3）
     * @return 0~1 的插值系数，供 {@link #lerpRgb} 使用
     */
    private static float purityFactor(int activeCount) {
        return clamp01((activeCount - 1) * 0.35f);
    }

    private static int lerpRgb(int from, int to, float t) {
        t = clamp01(t);
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int bl = Math.round(fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static float[] unpack(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f
        };
    }
}
