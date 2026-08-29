package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 普拉顿桑克斯的回溯「时之凝滞」客户端渲染器（纯客户端自绘）。
 * <p>
 * 对应 {@code EnchantmentTimeReversal}：受到致命伤害时阻止死亡、进入 5 秒完全无敌，
 * 期间受到的全部伤害被储存并反弹给攻击者，结束后回复 25% 储存值。
 * </p>
 *
 * <h3>这套视觉要回答三个问题</h3>
 * <p>
 * 逆转之前是全模组反馈最差的机制：玩家残血站着挨打却不掉血，
 * 屏幕上什么都不发生。视觉必须让人一眼读出：
 * </p>
 * <ol>
 *     <li><b>「我现在无敌」</b>——脚下钟面 + 身周凝固碎片，一起表达「时间在这里停住了」；</li>
 *     <li><b>「快结束了」</b>——收束阶段碎片猛然回吸 + 爆发环，
 *         是明确的「无敌要没了，准备跑」信号；</li>
 *     <li><b>「攒了多少」</b>——这一条<b>不在本渲染器</b>，由 HUD 的储存伤害条负责
 *         （{@code CarianStyleTimeReversalDisplay}）。世界视觉表达状态，数值交给 HUD，
 *         这是本模组一贯的分工。</li>
 * </ol>
 *
 * <h3>形状语言：全模组唯一「反着走」的演出</h3>
 * <p>
 * 本模组其余全部环形演出都是<b>向外扩散</b>（因果律、冻结地震、排斥、龙雷、癫火、
 * 立体花的爆发环无一例外），唯一的反例是满月月华的回春环（向内收拢，表达「汇聚」）。
 * </p>
 * <p>
 * 逆转在此基础上更进一步——<b>环向内收缩、指针逆时针走、碎片完全静止</b>。
 * 尤其是「静止」这一条：全模组所有粒子都在动（血滴在飞、孢子在升、月尘在飘），
 * 唯独这些碎片钉在半空一动不动，这个反差本身就是最强的「时间停止」信号，
 * 比任何色彩或图案都直接。
 * </p>
 * <p>
 * <b>配色刻意压暗</b>：旧金 {@link #TIME_GOLD} 比黄金树的 {@code 0xFFC23A} 暗且偏灰，
 * 配合褐色暗部 {@link #TIME_DEEP}，读起来是「古旧钟表」而不是「神圣光辉」。
 * 加上钟面 + 逆行指针的形状，与黄金树祝福 / 祈祷一击的暖金不会混淆；
 * 何况逆转冷却 6000 tick，同屏与它们并列比较的机会本就极少。
 * </p>
 *
 * <h3>为什么走「持续状态同步」而不是定点特效包</h3>
 * <p>
 * 逆转是一段 100 tick 的<b>状态</b>：期间玩家会被击退、会自己跑位。
 * 若用 {@code AoeEffectPacket} 发一个定点演出，人会跑出自己的光圈；
 * 而用状态同步（{@link EnchantmentTimeReversal#TIME_REVERSAL_SERIAL}）
 * 视觉天然跟随实体，且结束时移除即可，客户端不需要猜时长。
 * 这与重力力场圈（{@code EnchantmentGravitas.GRAVITY_FIELD_SERIAL}）是同一套做法。
 * </p>
 *
 * <h3>出入动画：客户端自己维护，服务端不用管</h3>
 * <p>
 * 同步只告诉客户端「现在有 / 没有」，没有进度信息。因此出现时的展开与
 * 结束时的收束由本渲染器自己用 {@link #STATE} 记录时间戳来驱动，
 * 与 {@code AuraGroundRenderer} 的光环出现 / 淡出、
 * {@code CarianRetaliationRenderer} 的举盾 / 放盾是同一套状态机。
 * </p>
 * <p>
 * <b>收束动画的时机因此天然正确</b>：服务端在治疗结算的同一帧移除序列号，
 * 客户端下一帧发现「不再同步」就开始收束——玩家看到碎片回吸的瞬间，
 * 正是他真的不再无敌的瞬间，不会有误导窗口。
 * </p>
 * <p>
 * <b>实体卸载时的兜底：</b>{@link FadeState} 记录了最后已知坐标，
 * 实体解析不到时在原地把收束播完，不会「啪」地消失。
 * </p>
 *
 * <h3>顶点量与 LOD</h3>
 * <pre>
 * 钟面双环（2 × 32 段 × 6）                384
 * 12 刻度 + 逆行指针（13 × 6）              78
 * 逆行收缩环（3 × 32 段 × 6）              576
 * 凝固碎片（14 片 × 12）                   168
 * 收束爆发环（仅收束阶段，32 段 × 6）      192
 * ─────────────────────────────────────────
 * 稳态合计                          ~1200 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * 逆转冷却 6000 tick（5 分钟），同屏几乎不可能超过一两个，
 * 但仍完整接入 {@link VisualLod}——{@link VisualLod#countInstance()} 是必须的，
 * 少登记一个渲染器就会让全局 {@code crowdFactor} 被系统性高估、
 * 已接入的重量级渲染器削减不足。
 * </p>
 * <p>
 * 三个配色全是编译期常量、演出中只有 alpha 与尺寸在变、色相从不插值，
 * 故全部预解包为 {@code C_} 常量，颜色相关堆分配恒为 0，无需 {@code SCRATCH} 缓冲。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class TimeReversalRenderer {

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    /** 离地高度偏移，避免地面图形与地形 z-fighting */
    private static final float Y_OFFSET = 0.02f;

    /**
     * 渲染器起始墙钟毫秒（类加载时固定）。
     * <p>动画时间必须用差值再转 float：直接 {@code currentTimeMillis()/1000f} 数值约 1.7e9，
     * 超出 float 有效精度，逐帧算出的时间会完全相同、动画彻底静止。</p>
     */
    private static final long START_MILLIS = System.currentTimeMillis();

    /** 展开动画时长（秒） */
    private static final float APPEAR_SECONDS = 0.45f;
    /** 收束动画时长（秒） */
    private static final float FADE_SECONDS = 0.6f;

    // ===== 配色（0xRRGGBB）=====
    /** 旧金：钟面主色。刻意比黄金树的 0xFFC23A 暗且偏灰，读作「古旧钟表」而非「神圣」 */
    private static final int TIME_GOLD = 0xD8B45A;
    /** 苍白：指针、碎片高光、收束爆闪 */
    private static final int TIME_PALE = 0xF2EEE0;
    /** 深褐：外环暗部、地面铺底 */
    private static final int TIME_DEEP = 0x5A4420;

    // ===== 预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定，表示「类加载时解包一次、此后永久复用的常量颜色数组」。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，
    // 会永久污染该配色且之后每帧都是错的——改动这几行时务必留意。
    private static final float[] C_GOLD = VisualColor.constant(TIME_GOLD);
    private static final float[] C_PALE = VisualColor.constant(TIME_PALE);
    private static final float[] C_DEEP = VisualColor.constant(TIME_DEEP);

    // ===== 脚下钟面 =====
    /** 钟面半径系数（× 实体宽度） */
    private static final float DIAL_RADIUS_FACTOR = 1.5f;
    /** 钟面环分段数 */
    private static final int DIAL_SEGMENTS = 32;
    /** 钟面环的最少分段数 */
    private static final int DIAL_SEGMENTS_MIN = 12;
    /** 钟面环线半宽（格） */
    private static final float DIAL_RING_HALF = 0.05f;
    /** 钟面刻度数（12 时刻，钟表的固有数字，不参与削减） */
    private static final int DIAL_TICK_COUNT = 12;
    /** 刻度长度相对半径的比例 */
    private static final float DIAL_TICK_RATIO = 0.16f;
    /** 指针长度相对半径的比例 */
    private static final float HAND_LENGTH_RATIO = 0.82f;
    /**
     * 指针角速度（弧度/秒）。
     * <p><b>负值 = 逆时针</b>，这正是「回溯」的字面表达。
     * 取 -1.26 使指针在 5 秒的逆转窗口内正好逆走一整圈，
     * 玩家扫一眼指针位置就能估出还剩多久。</p>
     */
    private static final float HAND_SPEED = -TAU / 5f;
    private static final float DIAL_ALPHA = 0.72f;

    // ===== 逆行收缩环 =====
    /** 同时存在的收缩环数 */
    private static final int RING_COUNT = 3;
    /** 收缩循环频率（圈/秒） */
    private static final float RING_PER_SECOND = 0.55f;
    /** 收缩环起始半径系数（× 实体宽度） */
    private static final float RING_RADIUS_FACTOR = 2.0f;
    private static final float RING_HALF = 0.045f;
    private static final float RING_ALPHA = 0.6f;

    // ===== 凝固碎片（核心标志：完全静止）=====
    /** 碎片数量 */
    private static final int SHARD_COUNT = 14;
    /** 碎片环绕半径系数（× 实体宽度） */
    private static final float SHARD_RADIUS_FACTOR = 1.15f;
    /** 碎片分布高度系数（× 实体高度） */
    private static final float SHARD_HEIGHT_FACTOR = 1.5f;
    /** 碎片基准尺寸（格） */
    private static final float SHARD_SIZE = 0.075f;
    private static final float SHARD_ALPHA = 0.85f;
    /** 碎片层的保留阈值 */
    private static final float SHARD_KEEP_THRESHOLD = 0.35f;

    /**
     * 逆转状态的出现 / 收束动画状态（按实体网络 id 索引）。
     * <p>
     * 只在实体处于逆转状态、或正在播收束动画时存在条目；收束结束即移除。
     * 逆转冷却 5 分钟，因此这个 Map 稳态下几乎恒为空。
     * </p>
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final Map<Integer, FadeState> STATE = new HashMap<>();

    /**
     * 一个正在播放的逆转视觉：记录出入时间戳与最后已知坐标。
     * <p>
     * <b>为什么要记坐标：</b>实体可能在收束期间被卸载（切区块 / 跨维度），
     * 此时按 id 解析不到实体。记下最后一帧的位置就能在原地把收束播完，
     * 而不是「啪」地消失——后者会让玩家以为出了 bug。
     * </p>
     */
    private static final class FadeState {
        /** 出现时刻（秒，墙钟） */
        float appearTime;
        /** 开始收束的时刻（秒）；&lt;0 表示仍激活 */
        float fadeStart = -1f;
        /** 上次「本帧仍在同步列表中」的帧号，用于零分配地判断「本帧没出现」 */
        int lastSeenFrame = -1;
        /** 最后已知世界坐标（实体卸载后用于原地播完收束） */
        double lastX;
        double lastY;
        double lastZ;
        /** 实体宽度 / 高度快照（同上，卸载后仍需正确尺寸） */
        float width = 0.6f;
        float height = 1.8f;
    }

    private TimeReversalRenderer() {
    }

    /**
     * 世界渲染回调：绘制相机附近所有处于逆转状态（或正在收束）实体的时之凝滞视觉。
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // ⚠ 离开世界的清理必须在取共享缓冲之前：那一帧批次本就不会开启，
        // 若排在判空之后，收束状态会残留到下次进入世界，与新分配的实体 id 撞号后闪出错误画面
        if (mc.level == null || mc.player == null) {
            STATE.clear();
            return;
        }
        BufferBuilder builder = VisualBatch.builder();
        if (builder == null) {
            return;
        }
        Vec3 cam = VisualBatch.cameraPosition();
        if (cam == null) {
            return;
        }

        Matrix4f matrix = VisualBatch.matrix();
        float partial = VisualBatch.partialTick();
        float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;
        int frameId = VisualBatch.frameId();

        // ===== 1) 刷新：本帧仍在同步列表中的实体，写入当前帧号与姿态快照 =====
        List<LivingEntity> candidates = SharedEntityQuery.livingEntitiesNearCamera(mc, cam);
        for (LivingEntity entity : candidates) {
            if (!ClientSyncEffectManager.shouldRenderEffect(
                    EnchantmentTimeReversal.TIME_REVERSAL_SERIAL, entity.getId())) {
                continue;
            }
            int id = entity.getId();
            FadeState st = STATE.get(id);
            if (st == null) {
                st = new FadeState();
                st.appearTime = time;
                STATE.put(id, st);
            }
            st.fadeStart = -1f;
            st.lastSeenFrame = frameId;
            st.lastX = Mth.lerp((double) partial, entity.xo, entity.getX());
            st.lastY = Mth.lerp((double) partial, entity.yo, entity.getY());
            st.lastZ = Mth.lerp((double) partial, entity.zo, entity.getZ());
            st.width = entity.getBbWidth();
            st.height = entity.getBbHeight();
        }

        // ===== 2) 标记本帧未出现的条目开始收束 =====
        for (Map.Entry<Integer, FadeState> e : STATE.entrySet()) {
            FadeState st = e.getValue();
            if (st.fadeStart < 0f && st.lastSeenFrame != frameId) {
                st.fadeStart = time;
            }
        }

        // ===== 3) 绘制（含正在收束的） =====
        Iterator<Map.Entry<Integer, FadeState>> it = STATE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, FadeState> entry = it.next();
            FadeState st = entry.getValue();

            float appear;
            float collapse;
            float alpha;
            if (st.fadeStart < 0f) {
                // 出现 / 稳定
                appear = easeOutCubic(clamp01((time - st.appearTime) / APPEAR_SECONDS));
                collapse = 0f;
                alpha = appear;
            } else {
                // 收束：碎片回吸 + 整体淡出
                float p = clamp01((time - st.fadeStart) / FADE_SECONDS);
                if (p >= 1f) {
                    it.remove();
                    continue;
                }
                appear = 1f;
                collapse = easeOutCubic(p);
                alpha = 1f - p;
            }
            if (alpha <= 0.01f) {
                continue;
            }

            // 位置：实体还在就用实时坐标，否则用最后已知坐标原地播完
            double wx = st.lastX;
            double wy = st.lastY;
            double wz = st.lastZ;
            Entity bound = mc.level.getEntity(entry.getKey());
            if (bound != null && bound.isAlive()) {
                wx = Mth.lerp((double) partial, bound.xo, bound.getX());
                wy = Mth.lerp((double) partial, bound.yo, bound.getY());
                wz = Mth.lerp((double) partial, bound.zo, bound.getZ());
                st.lastX = wx;
                st.lastY = wy;
                st.lastZ = wz;
            }

            double dx = wx - cam.x;
            double dy = wy - cam.y;
            double dz = wz - cam.z;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > CULL_SQR) {
                continue; // 太远：本帧不画，但收束计时继续
            }

            float detail = VisualLod.detail(distSqr);
            VisualLod.countInstance();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;
            int seedId = entry.getKey();

            drawDial(builder, matrix, rx, ryFoot + Y_OFFSET, rz, st.width, time, appear, alpha, detail);
            drawContractingRings(builder, matrix, rx, ryFoot + Y_OFFSET, rz, st.width,
                    time, seedId, appear, alpha, detail);
            if (VisualLod.keepLayer(detail, SHARD_KEEP_THRESHOLD)) {
                drawFrozenShards(builder, matrix, rx, ryFoot, rz, st.width, st.height,
                        seedId, appear, collapse, alpha, detail);
            }
            if (collapse > 0f) {
                drawCollapseBurst(builder, matrix, rx, ryFoot + Y_OFFSET, rz, st.width, collapse, detail);
            }
        }
    }

    // ==================== 脚下钟面 ====================

    /**
     * 脚下的旧金钟面：双环 + 12 刻度 + <b>逆时针</b>走的指针。
     * <p>
     * 指针角速度 {@link #HAND_SPEED} 取负值且大小恰为「5 秒一整圈」——
     * 逆转窗口正好 100 tick，因此<b>指针从起点逆走回起点的那一刻就是无敌结束的那一刻</b>，
     * 玩家扫一眼指针位置就能估出还剩多久，比盯 HUD 秒数更符合战斗中的注意力分配。
     * </p>
     * <p>
     * <b>削减：</b>环分段数缩放。<b>12 个刻度与指针不参与削减</b>——
     * 12 是钟表的固有数字，抽掉几个就不是钟面了；而且它们合计才 78 个顶点。
     * </p>
     */
    private static void drawDial(BufferBuilder b, Matrix4f m,
                                 float cx, float cy, float cz, float width,
                                 float time, float appear, float alpha, float detail) {
        float radius = width * DIAL_RADIUS_FACTOR * appear;
        if (radius <= 0.05f) {
            return;
        }
        int segments = VisualLod.scaleSegments(DIAL_SEGMENTS, DIAL_SEGMENTS_MIN, detail);
        float a = DIAL_ALPHA * alpha;

        // 地面铺底：让整片区域读作「这里的时间不一样」
        disc(b, m, cx, cy, cz, radius, segments, C_DEEP, 0.1f * alpha);
        // 外环 + 内环
        ring(b, m, cx, cy, cz, radius, segments, DIAL_RING_HALF, C_GOLD, a);
        ring(b, m, cx, cy, cz, radius * 0.78f, segments, DIAL_RING_HALF * 0.7f, C_DEEP, a * 0.8f);

        // 12 刻度：整点更长更亮（3/6/9/12），与真实钟面一致
        float tickOuter = radius * 0.97f;
        for (int i = 0; i < DIAL_TICK_COUNT; i++) {
            float ang = TAU * i / DIAL_TICK_COUNT;
            boolean quarter = (i % 3 == 0);
            float len = radius * DIAL_TICK_RATIO * (quarter ? 1.6f : 1f);
            float ca = Mth.cos(ang);
            float sa = Mth.sin(ang);
            line(b, m,
                    cx + ca * (tickOuter - len), cz + sa * (tickOuter - len),
                    cx + ca * tickOuter, cz + sa * tickOuter,
                    cy, DIAL_RING_HALF * (quarter ? 1.1f : 0.7f),
                    C_GOLD, a * (quarter ? 1f : 0.65f), a * (quarter ? 1f : 0.65f));
        }

        // ⭐ 逆时针指针：本模组唯一反向走的旋转元素
        float handAng = time * HAND_SPEED;
        float hx = cx + Mth.cos(handAng) * radius * HAND_LENGTH_RATIO;
        float hz = cz + Mth.sin(handAng) * radius * HAND_LENGTH_RATIO;
        line(b, m, cx, cz, hx, hz, cy, DIAL_RING_HALF * 1.3f, C_PALE, a, a * 0.3f);
        // 指针轴心
        spark(b, m, cx, cz, cy, radius * 0.07f + 0.04f, C_PALE, a);
    }

    // ==================== 逆行收缩环 ====================

    /**
     * 自外向内收缩的环：与本模组其余全部「向外扩散」的环反向。
     * <p>
     * 方向本身就是信息——玩家不需要认出这是钟表，只要看到「东西在往里收」，
     * 就能读出与爆发类演出相反的语义。
     * </p>
     * <p>
     * <b>削减：</b>环数与分段数按细节缩放。环的相位是 {@code i / count} 均布的，
     * 减环数会改变相位间隔，但它是循环推进的波、没有固定方位，
     * 只表现为「波与波之间隔得更开」，观感自然，无需按步长抽取。
     * </p>
     */
    private static void drawContractingRings(BufferBuilder b, Matrix4f m,
                                             float cx, float cy, float cz, float width,
                                             float time, int seedId,
                                             float appear, float alpha, float detail) {
        int count = VisualLod.scale(RING_COUNT, detail);
        int segments = VisualLod.scaleSegments(DIAL_SEGMENTS, DIAL_SEGMENTS_MIN, detail);
        float maxRadius = width * RING_RADIUS_FACTOR * appear;

        for (int i = 0; i < count; i++) {
            float phase = (float) i / count;
            float t = frac(time * RING_PER_SECOND + phase + seedId * 0.07f);
            // ⭐ 向内收缩：半径随进度递减
            float radius = maxRadius * (1f - easeOutCubic(t));
            if (radius <= 0.15f) {
                continue;
            }
            float a = RING_ALPHA * alpha * smoothstep(0f, 0.12f, t) * (1f - smoothstep(0.85f, 1f, t));
            if (a <= 0.01f) {
                continue;
            }
            ring(b, m, cx, cy, cz, radius, segments, RING_HALF, C_GOLD, a);
        }
    }

    // ==================== 凝固碎片（核心标志）====================

    /**
     * 身周悬停的碎片：<b>完全静止</b>，位置只由种子决定、不随时间变化。
     * <p>
     * <b>这是整套视觉最重要的一笔。</b>本模组所有粒子类元素都在动——
     * 血滴在飞、孢子在升、月尘在飘、金叶在落。
     * 唯独这些碎片钉在半空一动不动，这个反差本身就是最直接的「时间停止」信号，
     * 比任何色彩或图案都不需要解释。
     * </p>
     * <p>
     * <b>唯一的例外是收束阶段</b>：{@code collapse} 从 0 走到 1 时，
     * 碎片沿径向猛然回吸到中心并放大——「时间重新开始流动、被冻住的东西一起砸回来」，
     * 恰好对应机制上「储存的伤害在此刻结算成治疗」。
     * </p>
     * <p>
     * <b>削减：</b>数量按细节缩放；整层由调用方按 {@link #SHARD_KEEP_THRESHOLD} 决定是否绘制。
     * 碎片角度是纯随机的（与下标无关），截断尾部时保留碎片的位置完全不变。
     * </p>
     */
    private static void drawFrozenShards(BufferBuilder b, Matrix4f m,
                                         float cx, float cyFoot, float cz,
                                         float width, float height, int seedId,
                                         float appear, float collapse, float alpha, float detail) {
        int count = VisualLod.scale(SHARD_COUNT, detail);
        float radius = width * SHARD_RADIUS_FACTOR;

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = 0.45f + 0.55f * rngFloat(s);
            s = rngNext(s);
            float heightFrac = 0.12f + 0.85f * rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.65f + 0.7f * rngFloat(s);

            // 出现时自外向内落位；收束时反向猛吸到中心
            float radialScale = appear * (1f - collapse * 0.92f);
            float curRad = radius * radFactor * radialScale;
            float px = cx + Mth.cos(ang) * curRad;
            float pz = cz + Mth.sin(ang) * curRad;
            float py = cyFoot + height * heightFrac * (1f - collapse * 0.35f);

            // 回吸时碎片放大，制造「砸回来」的分量感
            float size = SHARD_SIZE * sizeRand * (1f + collapse * 1.4f);
            float a = SHARD_ALPHA * alpha;
            float[] col = (i % 3 == 0) ? C_PALE : C_GOLD;

            billboardDiamond(b, m, px, py, pz, size, col, a);
        }
    }

    // ==================== 收束爆发 ====================

    /**
     * 收束阶段的爆发环 + 中心闪光：与碎片回吸同步，标记「无敌结束、治疗结算」。
     * <p>
     * 这是整套演出唯一<b>向外</b>扩散的元素——刻意留到最后一刻才用，
     * 让「时间重新开始流动」这件事有一个明确的释放点。
     * </p>
     */
    private static void drawCollapseBurst(BufferBuilder b, Matrix4f m,
                                          float cx, float cy, float cz, float width,
                                          float collapse, float detail) {
        int segments = VisualLod.scaleSegments(DIAL_SEGMENTS, DIAL_SEGMENTS_MIN, detail);
        float fade = 1f - collapse;
        float radius = width * 2.6f * easeOutCubic(collapse);
        if (radius > 0.1f) {
            ring(b, m, cx, cy, cz, radius, segments, DIAL_RING_HALF * 1.4f, C_PALE, 0.75f * fade);
            ring(b, m, cx, cy, cz, radius * 0.72f, segments, DIAL_RING_HALF, C_GOLD, 0.45f * fade);
        }
        // 中心强闪：只在收束最开始的一小段
        if (collapse < 0.35f) {
            float flash = 1f - collapse / 0.35f;
            disc(b, m, cx, cy, cz, width * 1.1f, segments, C_PALE, 0.55f * flash);
        }
    }

    // ==================== 水平几何基元 ====================

    /**
     * 水平径向渐变圆盘（中心 alpha、边缘 0）。
     */
    private static void disc(BufferBuilder b, Matrix4f m,
                             float cx, float cy, float cz, float radius, int segments,
                             float[] col, float centerAlpha) {
        if (centerAlpha <= 0.004f || radius <= 1.0e-4f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
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

    /**
     * 水平圆环（内外两侧为同一 alpha 的窄带）。
     *
     * @param halfWidth 环带半宽（格）
     */
    private static void ring(BufferBuilder b, Matrix4f m,
                             float cx, float cy, float cz, float radius, int segments,
                             float halfWidth, float[] col, float alpha) {
        if (alpha <= 0.004f || radius <= 1.0e-4f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
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

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alpha).endVertex();
            b.vertex(m, ox1, cy, oz1).color(r, g, bl, alpha).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alpha).endVertex();

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alpha).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alpha).endVertex();
            b.vertex(m, ix0, cy, iz0).color(r, g, bl, alpha).endVertex();
        }
    }

    /**
     * 带宽度的水平线段，两端 alpha 可分别指定。
     *
     * @param hw 线半宽（格）
     */
    private static void line(BufferBuilder b, Matrix4f m,
                             float x1, float z1, float x2, float z2, float y,
                             float hw, float[] col, float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        double dx = x2 - x1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) {
            return;
        }
        double nx = -dz / len * hw;
        double nz = dx / len * hw;
        float r = col[0], g = col[1], bl = col[2];
        float ax1 = (float) (x1 + nx), az1 = (float) (z1 + nz);
        float ax2 = (float) (x1 - nx), az2 = (float) (z1 - nz);
        float bx1 = (float) (x2 + nx), bz1 = (float) (z2 + nz);
        float bx2 = (float) (x2 - nx), bz2 = (float) (z2 - nz);

        b.vertex(m, ax1, y, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx1, y, bz1).color(r, g, bl, a2).endVertex();
        b.vertex(m, bx2, y, bz2).color(r, g, bl, a2).endVertex();

        b.vertex(m, ax1, y, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx2, y, bz2).color(r, g, bl, a2).endVertex();
        b.vertex(m, ax2, y, az2).color(r, g, bl, a1).endVertex();
    }

    /**
     * 小菱形光点（水平面），中心最亮、四角渐隐。
     * <p>角点内联为标量，零分配（做法与 {@code AoeEffectRenderer} 的同名方法同源）。</p>
     */
    private static void spark(BufferBuilder b, Matrix4f m, float px, float pz, float y,
                              float size, float[] col, float alpha) {
        if (alpha <= 0.004f || size <= 1.0e-4f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float p0x = px, p0z = pz - size;
        float p1x = px + size, p1z = pz;
        float p2x = px, p2z = pz + size;
        float p3x = px - size, p3z = pz;

        sparkTri(b, m, px, y, pz, p0x, p0z, p1x, p1z, r, g, bl, alpha);
        sparkTri(b, m, px, y, pz, p1x, p1z, p2x, p2z, r, g, bl, alpha);
        sparkTri(b, m, px, y, pz, p2x, p2z, p3x, p3z, r, g, bl, alpha);
        sparkTri(b, m, px, y, pz, p3x, p3z, p0x, p0z, r, g, bl, alpha);
    }

    /**
     * 水平光点的一瓣三角形：中心不透明，两个外角渐隐为 0。
     */
    private static void sparkTri(BufferBuilder b, Matrix4f m,
                                 float cx, float y, float cz,
                                 float ax, float az, float bx, float bz,
                                 float r, float g, float bl, float alpha) {
        b.vertex(m, cx, y, cz).color(r, g, bl, alpha).endVertex();
        b.vertex(m, ax, y, az).color(r, g, bl, 0f).endVertex();
        b.vertex(m, bx, y, bz).color(r, g, bl, 0f).endVertex();
    }

    /**
     * 面向相机的小菱形光点：中心最亮、四角渐隐。
     * <p>仅 12 顶点；碎片用它而非水平菱形，因为碎片悬在半空、需要从任意角度都看得见。</p>
     */
    private static void billboardDiamond(BufferBuilder b, Matrix4f m,
                                         float cx, float cy, float cz, float size,
                                         float[] col, float alpha) {
        if (alpha <= 0.004f || size <= 1.0e-4f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float rx = VisualBatch.rightX() * size;
        float ry = VisualBatch.rightY() * size;
        float rz = VisualBatch.rightZ() * size;
        float upx = VisualBatch.upX() * size;
        float upy = VisualBatch.upY() * size;
        float upz = VisualBatch.upZ() * size;

        float p0x = cx + upx, p0y = cy + upy, p0z = cz + upz;
        float p1x = cx + rx, p1y = cy + ry, p1z = cz + rz;
        float p2x = cx - upx, p2y = cy - upy, p2z = cz - upz;
        float p3x = cx - rx, p3y = cy - ry, p3z = cz - rz;

        diamondTri(b, m, cx, cy, cz, p0x, p0y, p0z, p1x, p1y, p1z, r, g, bl, alpha);
        diamondTri(b, m, cx, cy, cz, p1x, p1y, p1z, p2x, p2y, p2z, r, g, bl, alpha);
        diamondTri(b, m, cx, cy, cz, p2x, p2y, p2z, p3x, p3y, p3z, r, g, bl, alpha);
        diamondTri(b, m, cx, cy, cz, p3x, p3y, p3z, p0x, p0y, p0z, r, g, bl, alpha);
    }

    /**
     * billboard 菱形的一瓣三角形：中心不透明，两个外角渐隐为 0。
     */
    private static void diamondTri(BufferBuilder b, Matrix4f m,
                                   float cx, float cy, float cz,
                                   float ax, float ay, float az,
                                   float bx, float by, float bz,
                                   float r, float g, float bl, float alpha) {
        b.vertex(m, cx, cy, cz).color(r, g, bl, alpha).endVertex();
        b.vertex(m, ax, ay, az).color(r, g, bl, 0f).endVertex();
        b.vertex(m, bx, by, bz).color(r, g, bl, 0f).endVertex();
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

    // ==================== 数学辅助 ====================

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static float smoothstep(float e0, float e1, float x) {
        if (e1 <= e0) {
            return x < e0 ? 0f : 1f;
        }
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }
}
