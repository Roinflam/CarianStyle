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
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.network.HemorrhageSyncHandler;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 出血「飙血」客户端渲染器（纯客户端自绘）。
 * <p>
 * <b>判定采用双重冗余：</b>{@code entity.hasEffect(CarianStylePotion.HEMORRHAGE.get())}
 * （覆盖玩家自己、以及在观察者开始追踪前就已带出血的实体）<b>或</b>
 * {@code ClientSyncEffectManager.shouldRenderEffect(HemorrhageSyncHandler.HEMORRHAGE_SERIAL, id)}
 * （覆盖战斗中途才被施加出血的怪物——原版不会把这次新增效果同步给已经在追踪该实体的观察者，
 * 见 {@link HemorrhageSyncHandler} 类注释。<b>这是此前「完全没有特效」的根因</b>：正在交战的目标
 * 几乎总是已被观察者追踪，导致 {@code hasEffect} 恒为 false，特效从未触发过，而不是强度不够）。
 * </p>
 * 视觉分两层：
 * <ol>
 *     <li><b>常驻氛围</b>——脚下血泊（两层渐变圆盘）、从躯干循环垂落的粗血线、
 *         多颗沿抛物线飞出的柔光血滴；</li>
 *     <li><b>心跳式迸溅爆发</b>——{@link #drawBurstRays}，按 {@link #PULSE_PERIOD} 周期性触发，
 *         在胸口炸开一圈放射状血线（内亮外暗，模拟血液甩出后迅速氧化变暗）+ 脚下同步的冲击
 *         闪光，让心跳喷发的瞬间有明确的「打击感」，而不只是持续飘落的血滴；</li>
 *     <li><b>血雾</b>（{@link #drawBloodMist}）——伤口附近喷出的红色雾团（原理与冻伤的冰雾
 *         相同，见 {@code FrostbiteMistRenderer#drawFrostFog}），配合血滴 / 射线一起构成
 *         「真正在喷血」的体积感，而不是零散血点各自往外飞。</li>
 * </ol>
 * </p>
 * <p>
 * <b>v3（修复同步后再加码）：</b>补上 {@link HemorrhageSyncHandler} 之后特效终于能正常触发，
 * 但反馈仍然「没有飙血的感觉」——血滴数量/体积/飞溅距离全面上调（基础血滴 16→26、尺寸
 * 0.11→0.18、初速度提升约 50%），心跳爆发窗口从周期的 24% 延长到 40%、间隔从 1.3 秒缩短到
 * 1 秒（喷得更频繁），迸溅射线更粗更长，再加上全新的血雾层，多管齐下确保观感是「正在喷血」
 * 而不是「偶尔滴几滴」。
 * </p>
 * <p>
 * <b>v4（性能，视觉零变化）：</b>接入 {@link VisualBatch} 与 {@link SharedEntityQuery}——
 * <ul>
 *     <li>不再自行设置 / 恢复 GL 状态、不再自行 {@code begin/end} 顶点缓冲，改为向
 *         {@link VisualBatch} 提供的共享缓冲写顶点，由其在本帧末统一提交（七个渲染器合并为一次
 *         GL 状态切换与一次 draw call）；</li>
 *     <li>不再自行做范围实体查询，改为遍历 {@link SharedEntityQuery} 的每帧共享列表，
 *         把原先的查询判定条件下沉为循环内的 {@code continue}（见 {@link #hasHemorrhage}）。</li>
 * </ul>
 * 判定条件、精确平方距离裁剪、绘制顺序与全部几何参数均未改动。
 * </p>
 * <p>
 * <b>v5（顶点量，近距离视觉零变化）：</b>接入 {@link VisualLod} 按距离与同屏拥挤度削减元素数量。
 * 本渲染器是全模组顶点开销最大的一个——单个患者每帧 <b>948</b> 个顶点
 * （血泊 120 + 血滴 624 + 垂落血线 36 + 血雾 168），心跳爆发窗口内达 <b>1512</b>。
 * 其中血滴与血雾共 33 个柔光块，每块 {@link #DROP_SEGMENTS} 段 × 3 顶点 = 24，
 * 单这一项就吃掉 792 个顶点。
 * </p>
 * <p>
 * 现在这些数量按 {@link VisualLod#detail} 缩放：{@link VisualLod#FULL_DETAIL_RANGE} 格内
 * 系数为 1.0，<b>与优化前逐像素一致</b>；远处与团战时逐步削减，40 格外单患者降至约 126 顶点
 * （降幅 87%）。削减只会「少画尾部几个」，保留元素的随机种子不变、位置不变，
 * 因此靠近时是逐渐多出几颗血滴，不会整片重新洗牌。
 * </p>
 *
 * <h3>v6（堆分配，视觉逐位一致）：颜色数组零分配化</h3>
 * <p>
 * v5 解决了顶点量，但留下了另一个热路径浪费：旧的 {@code unpack(int)} 把 {@code 0xRRGGBB}
 * 拆成 {@code float[3]} 时<b>每次都 {@code new float[3]}</b>。本渲染器的调用密度是全模组最高的：
 * </p>
 * <pre>
 * 喷溅血滴（26 基础 + 18 脉冲）每颗 lerpRgb→unpack   44
 * 血雾（7 团）每团 lerpRgb→unpack                     7
 * 垂落血线（6 条）verticalLine 内部 unpack            6
 * 迸溅射线 hot / dark / flash                          3
 * 血泊（2 层）                                          2
 * ────────────────────────────────────────────────────
 * 合计                              ~62 次 new float[3] / 实体 / 帧
 * </pre>
 * <p>
 * 十个患者 × 60fps ≈ <b>每秒 3.7 万次小数组分配</b>，且每个数组的存活期只有紧随其后的几行。
 * Eden 区回收单次很便宜，但这个量级会实打实推高客户端 GC 频率。
 * </p>
 * <p>
 * 现改为两条路径（工具方法见 {@link VisualColor}）：
 * </p>
 * <ol>
 *     <li><b>固定配色类加载时预解包一次</b>——{@link #C_BLOOD_BRIGHT} 等 {@code C_} 前缀常量，
 *         此后永久复用。本渲染器的血泊、垂落血线、迸溅射线、冲击闪光全部只用固定色，
 *         这一项就消掉了 11 次/帧；</li>
 *     <li><b>动态插值色写入复用缓冲</b>——血滴与血雾的颜色随飞行进度变化，
 *         改用 {@link VisualColor#lerpInto} 直接写进 {@link #SCRATCH_COLOR}，
 *         省掉中间的 int 与新数组，消掉剩下的 51 次/帧。</li>
 * </ol>
 * <p>
 * 至此本渲染器每帧颜色相关的堆分配为 <b>0</b>。
 * </p>
 * <p>
 * <b>为什么本渲染器只需要一个复用缓冲：</b>血滴与血雾的颜色都是「算出来紧接着就
 * {@code emitSoftDrop} 消费掉」，任一时刻只有一个动态色存活。
 * 需要<b>两个动态色同时存活</b>的场景（例如渐变线段要同时持有起点色与终点色）必须用两个缓冲，
 * 否则后写的会覆盖先写的、整条线退化成纯色——本渲染器的 {@link #rayLine} 两端用的是
 * {@link #C_BLOOD_HOT} / {@link #C_BLOOD_DARK} 两个<b>常量</b>，不受影响。
 * </p>
 * <p>
 * <b>视觉逐位一致：</b>{@link VisualColor#lerpInto} 刻意保留了旧 {@code lerpRgb} 在
 * 0~255 整数域插值并 {@link Math#round} 的行为（详见其类注释），
 * 因此输出与 v5 的每个颜色分量完全相同，不是「肉眼看不出」而是「数值相等」。
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
public final class HemorrhageBloodRenderer {

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final int DROP_SEGMENTS = 8;
    private static final long START_MILLIS = System.currentTimeMillis();

    // ===== v5 LOD 下限（低于这些值圆形会看出棱角 / 层次会塌掉）=====
    /** 柔光血滴的最少分段数：4 段仍是个饱满的菱形柔光块，再低就露馅 */
    private static final int DROP_SEGMENTS_MIN = 4;
    /** 血泊圆盘的最少分段数 */
    private static final int POOL_SEGMENTS_MIN = 8;
    /** 脚下冲击闪光的最少分段数 */
    private static final int FLASH_SEGMENTS_MIN = 8;
    /** 血泊第二层（外圈淡红）的保留阈值：低于此值只画内层 */
    private static final float POOL_OUTER_KEEP_THRESHOLD = 0.55f;
    /** 血雾层的保留阈值 */
    private static final float MIST_KEEP_THRESHOLD = 0.5f;

    // ===== 配色（0xRRGGBB）=====
    private static final int BLOOD_BRIGHT = 0xE0202F;
    private static final int BLOOD_HOT = 0xFF6070;
    private static final int BLOOD_FLASH = 0xFF8090;
    private static final int BLOOD_MID = 0x8A0F18;
    private static final int BLOOD_DARK = 0x3A0508;

    // ===== v6：预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定，表示「类加载时解包一次、此后永久复用的常量颜色数组」。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，
    // 会永久污染该配色且之后每帧都是错的——改动这几行时务必留意。
    // 注：BLOOD_BRIGHT 没有对应的 C_ 常量——它只作为 lerpInto 的插值端点（以 int 传入），
    // 从不单独作为一个成品颜色使用，预解包反而会变成未被引用的字段。
    /** 迸溅射线内端的高亮血色 */
    private static final float[] C_BLOOD_HOT = VisualColor.constant(BLOOD_HOT);
    /** 脚下冲击闪光色 */
    private static final float[] C_BLOOD_FLASH = VisualColor.constant(BLOOD_FLASH);
    /** 中段血色（血泊外层、垂落血线） */
    private static final float[] C_BLOOD_MID = VisualColor.constant(BLOOD_MID);
    /** 氧化暗血色（血泊内层、射线外端、血滴末期） */
    private static final float[] C_BLOOD_DARK = VisualColor.constant(BLOOD_DARK);

    /**
     * v6：动态插值色的复用缓冲（⚠ 写入后必须立即消费，不可跨调用留存）。
     * <p>
     * 仅用于血滴（{@link #drawDroplets}）与血雾（{@link #drawBloodMist}）——
     * 这两处的颜色随飞行进度 / 脉冲相位实时变化，无法预解包。两者都是
     * 「写入 → 紧接着 {@link #emitSoftDrop} 消费」，任一时刻只有一个动态色存活，
     * 故<b>一个缓冲就够</b>。
     * </p>
     * <p>
     * 若将来新增「两个动态色需要同时存活」的元素（典型如两端异色的渐变线段），
     * <b>必须再加一个独立缓冲</b>，不能复用本字段——否则后写的会覆盖先写的。
     * </p>
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final float[] SCRATCH_COLOR = new float[VisualColor.RGB];

    // ===== 地面血泊 =====
    private static final int POOL_LAYERS = 2;
    private static final int POOL_SEGMENTS = 20;
    private static final float POOL_RADIUS_BASE = 1.05f;
    private static final float POOL_RADIUS_STEP = 0.5f;
    private static final float POOL_BASE_ALPHA = 0.6f;

    // ===== 喷溅血滴（抛物线飞溅，v3：全面加大）=====
    private static final int BASE_DROPLETS = 26;
    /** 每颗血滴的飞行循环速度（每秒推进的归一化进度） */
    private static final float DROPLET_RATE = 0.6f;
    private static final float DROPLET_SIZE = 0.18f;
    /** 喷溅起点高度系数（× 实体高度） */
    private static final float LAUNCH_HEIGHT_FACTOR = 0.55f;
    private static final float LAUNCH_SPEED_BASE = 1.05f;
    private static final float LAUNCH_VY = 2.4f;
    private static final float GRAVITY = 2.4f;

    // ===== 心跳式迸溅爆发（v3：更猛更频繁）=====
    private static final float PULSE_PERIOD = 1.0f;
    private static final float PULSE_WINDOW = 0.4f;
    private static final int PULSE_EXTRA_DROPLETS = 18;
    private static final float PULSE_SIZE_MULT = 2.4f;
    private static final int PULSE_RAY_COUNT = 14;

    // ===== 血雾（v3 新增：伤口处的喷射雾团，跟冻伤的冰雾同一原理，但更集中更红）=====
    private static final int MIST_COUNT = 7;
    private static final float MIST_SIZE_FACTOR = 0.32f;
    private static final float MIST_BASE_ALPHA = 0.4f;

    // ===== 滴落血线（从躯干垂落）=====
    private static final int DRIP_COUNT = 6;
    private static final float DRIP_RATE = 0.35f;
    private static final float DRIP_HALF_WIDTH = 0.03f;

    private HemorrhageBloodRenderer() {
    }

    /**
     * 世界渲染回调：在半透明方块之后，绘制相机附近所有出血生物的飙血视觉。
     * <p>
     * v4：GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理，实体列表取自
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

        MobEffect hemorrhage = CarianStylePotion.HEMORRHAGE.get();

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
            // v4：原先作为查询谓词的判定，现下沉为循环内筛选（共享列表已保证 isAlive）
            if (!hasHemorrhage(entity, hemorrhage)) {
                continue;
            }

            double ex = Mth.lerp((double) partial, entity.xo, entity.getX());
            double ey = Mth.lerp((double) partial, entity.yo, entity.getY());
            double ez = Mth.lerp((double) partial, entity.zo, entity.getZ());

            double dx = ex - cam.x;
            double dy = ey - cam.y;
            double dz = ez - cam.z;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > CULL_SQR) {
                continue;
            }

            // v5：本实体的细节系数（距离 × 同屏拥挤度）。12 格内恒为 1.0，视觉与优化前一致
            float detail = VisualLod.detail(distSqr);
            // 登记实例，供下一帧估算拥挤度（不影响本帧绘制）
            VisualLod.countInstance();

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;

            drawBloodPool(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(), detail);
            drawDroplets(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(),
                    rightX, rightY, rightZ, upX, upY, upZ, detail);
            drawDrips(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(), detail);
            drawBurstRays(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(), detail);
            // 血雾是纯氛围层，远处完全看不出，低细节时整层跳过（省 168 顶点）
            if (VisualLod.keepLayer(detail, MIST_KEEP_THRESHOLD)) {
                drawBloodMist(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(),
                        rightX, rightY, rightZ, upX, upY, upZ, detail);
            }
        }
    }

    /**
     * 判断实体是否应显示出血视觉（双重冗余判定，与优化前的查询谓词逐条一致）。
     *
     * @param entity     待判定实体
     * @param hemorrhage 出血效果对象（可能为 null）
     * @return 应显示返回 true
     */
    private static boolean hasHemorrhage(LivingEntity entity, @Nullable MobEffect hemorrhage) {
        if (hemorrhage != null && entity.hasEffect(hemorrhage)) {
            return true;
        }
        return ClientSyncEffectManager.shouldRenderEffect(
                HemorrhageSyncHandler.HEMORRHAGE_SERIAL, entity.getId());
    }

    /**
     * 脚下血泊：两层渐变圆盘，随时间轻微呼吸。
     * <p>v5：分段数按细节系数缩放（下限 {@link #POOL_SEGMENTS_MIN}），
     * 低细节时外层整层跳过——外层本就是淡淡的一圈，远处完全看不出。</p>
     * <p>v6：两层用的都是固定色，直接取预解包常量，不再每层 {@code unpack} 一次。</p>
     */
    private static void drawBloodPool(BufferBuilder b, Matrix4f m,
                                      float cx, float cy, float cz, float width,
                                      float time, int seedId, float detail) {
        float breath = 0.9f + 0.1f * Mth.sin(time * 1.1f + seedId * 0.4f);
        int segments = VisualLod.scaleSegments(POOL_SEGMENTS, POOL_SEGMENTS_MIN, detail);
        int layers = VisualLod.keepLayer(detail, POOL_OUTER_KEEP_THRESHOLD) ? POOL_LAYERS : 1;
        for (int layer = 0; layer < layers; layer++) {
            float radius = width * (POOL_RADIUS_BASE + layer * POOL_RADIUS_STEP) * breath;
            float centerAlpha = POOL_BASE_ALPHA - layer * 0.14f;
            if (centerAlpha <= 0f || radius <= 0.05f) {
                continue;
            }
            // v6：常量颜色直接引用预解包数组（只读）
            float[] c = layer == 0 ? C_BLOOD_DARK : C_BLOOD_MID;
            drawDisc(b, m, cx, cy, cz, radius, segments, c[0], c[1], c[2], centerAlpha);
        }
    }

    /**
     * 喷溅血滴：多颗沿抛物线飞出的柔光圆点，颜色由鲜红转暗，落地/飞散后淡出；
     * 另按 {@link #PULSE_PERIOD} 周期性加量，模拟心跳式的大喷发。
     * <p>
     * v6：每颗血滴的颜色随飞行进度 t 变化，是本渲染器分配最密集的一处（最多 44 次/帧）。
     * 改用 {@link VisualColor#lerpInto} 直接写入 {@link #SCRATCH_COLOR}，
     * 省掉中间 int 与新数组；写入后紧接着被 {@link #emitSoftDrop} 消费，符合复用缓冲的使用约束。
     * </p>
     */
    private static void drawDroplets(BufferBuilder b, Matrix4f m,
                                     float cx, float cyFoot, float cz, float width, float height,
                                     float time, int seedId,
                                     float rightX, float rightY, float rightZ,
                                     float upX, float upY, float upZ, float detail) {
        float launchY = cyFoot + height * LAUNCH_HEIGHT_FACTOR;

        // 心跳脉冲窗口：命中窗口内额外多喷一批较大的血滴
        float pulsePhase = frac(time / PULSE_PERIOD + seedId * 0.13f);
        boolean pulseActive = pulsePhase < PULSE_WINDOW;

        // v5：基础血滴与脉冲加量分别缩放。
        // 注意必须分开缩放而不是缩放总数——脉冲血滴用的是另一套种子（i + 500）、
        // 尺寸也另有倍率，混在一起按总数截断会把整批脉冲血滴切没，喷发感就废了。
        int baseCount = VisualLod.scale(BASE_DROPLETS, detail);
        int pulseCount = pulseActive ? VisualLod.scale(PULSE_EXTRA_DROPLETS, detail) : 0;
        // 每颗柔光块的分段数同样缩放——这是本渲染器最大的顶点杠杆
        int dropSegments = VisualLod.scaleSegments(DROP_SEGMENTS, DROP_SEGMENTS_MIN, detail);

        int count = baseCount + pulseCount;
        for (int i = 0; i < count; i++) {
            boolean isPulseDroplet = i >= baseCount;
            // 种子索引沿用「全细节下的原始下标」，保证缩放前后同一颗血滴的轨迹完全一致
            int seedIndex = isPulseDroplet ? (i - baseCount + BASE_DROPLETS) : i;
            long s = seedFor(seedId, isPulseDroplet ? (seedIndex + 500) : seedIndex);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float speedRand = 0.6f + 0.8f * rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.7f + 0.6f * rngFloat(s);

            float rate = isPulseDroplet ? DROPLET_RATE * 1.4f : DROPLET_RATE;
            float t = frac(time * rate + phase);

            float speed = LAUNCH_SPEED_BASE * speedRand;
            float horizontal = speed * t;
            float vertical = LAUNCH_VY * t - GRAVITY * t * t;

            float px = cx + (float) Math.cos(ang) * horizontal;
            float pz = cz + (float) Math.sin(ang) * horizontal;
            float py = launchY + vertical;
            if (py < cyFoot) {
                py = cyFoot;
            }

            float alpha = (1f - t) * (isPulseDroplet ? 1.0f : 0.85f);
            if (t < 0.08f) {
                alpha *= t / 0.08f;
            }
            if (alpha <= 0.01f) {
                continue;
            }

            // v6：无分配插值，写入复用缓冲后立即消费
            VisualColor.lerpInto(SCRATCH_COLOR, BLOOD_BRIGHT, BLOOD_DARK, t);
            float size = DROPLET_SIZE * sizeRand * (isPulseDroplet ? PULSE_SIZE_MULT : 1f);

            emitSoftDrop(b, m, px, py + Y_OFFSET, pz, size,
                    SCRATCH_COLOR[0], SCRATCH_COLOR[1], SCRATCH_COLOR[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ, dropSegments);
        }
    }

    /**
     * 心跳脉冲窗口内的额外「迸溅射线」：胸口处向外爆开的放射状血线（内端 {@link #BLOOD_HOT} 高亮、
     * 外端 {@link #BLOOD_DARK} 迅速转暗，模拟血液甩出后氧化变暗），配合脚下同步的冲击闪光，
     * 让心跳喷发的瞬间比单纯的抛物线血滴更有打击感。与 {@link #drawDroplets} 复用同一个脉冲相位，
     * 故两者视觉上是同一次喷发的不同表现层，不会各自为政。
     * <p>
     * v6：射线两端与冲击闪光用的都是固定色，直接取预解包常量。
     * <b>注意这里两个颜色是同时存活的</b>（{@link #rayLine} 需要内端色与外端色一起用），
     * 但因为两者都是常量数组、互不干扰，无需复用缓冲——若将来改成动态插值色，
     * 就必须用两个独立缓冲（详见 {@link #SCRATCH_COLOR} 注释）。
     * </p>
     */
    private static void drawBurstRays(BufferBuilder b, Matrix4f m,
                                      float cx, float cyFoot, float cz, float width, float height,
                                      float time, int seedId, float detail) {
        float pulsePhase = frac(time / PULSE_PERIOD + seedId * 0.13f);
        if (pulsePhase >= PULSE_WINDOW) {
            return;
        }
        float p = pulsePhase / PULSE_WINDOW;
        float chestY = cyFoot + height * LAUNCH_HEIGHT_FACTOR;
        float len = width * 2.6f * easeOutCubic(Math.min(1f, p * 2.2f));
        float alpha = (1f - p) * 0.85f;
        float hw = Math.max(0.05f, width * 0.035f);

        // v5：射线数量缩放。
        // 注意这里不能简单地「只画前 N 条」——射线角度是 i * (TAU / PULSE_RAY_COUNT) 顺序排布的，
        // 截断前 N 条会让迸溅只覆盖一段圆弧、变成朝一个方向喷。
        // 改为按步长在整圈上均匀抽取，既保持原有角度、又始终是完整一圈。
        int rayCount = VisualLod.scale(PULSE_RAY_COUNT, detail);
        int rayStep = Math.max(1, PULSE_RAY_COUNT / rayCount);
        for (int i = 0; i < PULSE_RAY_COUNT; i += rayStep) {
            long s = seedFor(seedId, i + 900);
            float ang = i * (TAU / PULSE_RAY_COUNT) + rngFloat(s) * 0.35f;
            float ox = cx + (float) Math.cos(ang) * len;
            float oz = cz + (float) Math.sin(ang) * len;
            // 射线略带下坠：外端略低于起点，制造甩溅弧线的错觉
            // v6：内外两端均为预解包常量（只读），无分配
            rayLine(b, m, cx, cz, chestY, ox, oz, chestY - len * 0.2f, hw,
                    C_BLOOD_HOT, C_BLOOD_DARK, alpha);
        }

        // 脚下冲击闪光：与射线同步爆发，短促高亮后迅速回落
        if (p < 0.3f) {
            float flash = (0.3f - p) / 0.3f;
            drawDisc(b, m, cx, cyFoot + Y_OFFSET, cz, width * 1.6f * (0.4f + flash),
                    VisualLod.scaleSegments(16, FLASH_SEGMENTS_MIN, detail),
                    C_BLOOD_FLASH[0], C_BLOOD_FLASH[1], C_BLOOD_FLASH[2], 0.45f * flash);
        }
    }

    /**
     * 一条起点 (x1,y0,z1) 到终点 (x2,y1,z2) 的三维血线，内端亮、外端暗且更透明。
     * 与 {@link #verticalLine} 不同之处在于两端可分别指定颜色，用于表现「甩出后迅速氧化变暗」。
     */
    private static void rayLine(BufferBuilder b, Matrix4f m,
                                float x1, float z1, float y0, float x2, float z2, float y1,
                                float hw, float[] colInner, float[] colOuter, float alpha) {
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
        float outerAlpha = alpha * 0.4f;

        b.vertex(m, ax1, y0, az1).color(colInner[0], colInner[1], colInner[2], alpha).endVertex();
        b.vertex(m, bx1, y1, bz1).color(colOuter[0], colOuter[1], colOuter[2], outerAlpha).endVertex();
        b.vertex(m, bx2, y1, bz2).color(colOuter[0], colOuter[1], colOuter[2], outerAlpha).endVertex();

        b.vertex(m, ax1, y0, az1).color(colInner[0], colInner[1], colInner[2], alpha).endVertex();
        b.vertex(m, bx2, y1, bz2).color(colOuter[0], colOuter[1], colOuter[2], outerAlpha).endVertex();
        b.vertex(m, ax2, y0, az2).color(colInner[0], colInner[1], colInner[2], alpha).endVertex();
    }

    /**
     * 血雾：伤口（胸口）附近喷出的红色雾团，原理与冻伤 {@code FrostbiteMistRenderer#drawFrostFog}
     * 相同（大号柔光块叠加漂移），但更集中在伤口位置、颜色更红更暗，配合血滴 / 迸溅射线
     * 共同构成「真正在喷血」的观感，而不是零散的血点各自往外飞。
     * <p>v6：雾团颜色随脉冲相位实时变化，同样改用 {@link VisualColor#lerpInto} 写入复用缓冲。</p>
     */
    private static void drawBloodMist(BufferBuilder b, Matrix4f m,
                                      float cx, float cyFoot, float cz, float width, float height,
                                      float time, int seedId,
                                      float rightX, float rightY, float rightZ,
                                      float upX, float upY, float upZ, float detail) {
        float chestY = cyFoot + height * LAUNCH_HEIGHT_FACTOR;
        // v5：雾团数量与每团分段数同时缩放（雾团角度来自随机种子，取前 N 个即可，分布仍是散的）
        int mistCount = VisualLod.scale(MIST_COUNT, detail);
        int mistSegments = VisualLod.scaleSegments(DROP_SEGMENTS, DROP_SEGMENTS_MIN, detail);
        for (int i = 0; i < mistCount; i++) {
            long s = seedFor(seedId, i + 1300);
            float baseAngle = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = rngFloat(s);
            s = rngNext(s);
            float heightRand = rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.75f + 0.5f * rngFloat(s);
            s = rngNext(s);
            float driftPhase = rngFloat(s) * TAU;
            s = rngNext(s);
            float pulsePhase = rngFloat(s) * TAU;

            float driftAngle = baseAngle + Mth.sin(time * 0.9f + driftPhase) * 0.7f;
            float radius = width * 0.35f * radFactor;
            float px = cx + (float) Math.cos(driftAngle) * radius;
            float pz = cz + (float) Math.sin(driftAngle) * radius;
            float py = chestY + (heightRand - 0.5f) * height * 0.4f;

            float pulse = 0.7f + 0.3f * Mth.sin(time * 1.8f + pulsePhase);
            float alpha = MIST_BASE_ALPHA * pulse;
            float size = width * MIST_SIZE_FACTOR * sizeRand;

            // v6：无分配插值，写入复用缓冲后立即消费
            VisualColor.lerpInto(SCRATCH_COLOR, BLOOD_MID, BLOOD_BRIGHT, 0.4f + 0.4f * pulse);

            emitSoftDrop(b, m, px, py, pz, size,
                    SCRATCH_COLOR[0], SCRATCH_COLOR[1], SCRATCH_COLOR[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ, mistSegments);
        }
    }

    /**
     * 从躯干垂落的粗血线，循环下滑并在近地面淡出。
     * <p>v5：条数按细节系数缩放（角度来自随机种子，取前 N 条分布仍是散的）。</p>
     * <p>v6：血线用的是固定色，改为传入预解包常量，{@link #verticalLine} 不再内部 {@code unpack}。</p>
     */
    private static void drawDrips(BufferBuilder b, Matrix4f m,
                                  float cx, float cyFoot, float cz, float width, float height,
                                  float time, int seedId, float detail) {
        int dripCount = VisualLod.scale(DRIP_COUNT, detail);
        for (int i = 0; i < dripCount; i++) {
            long s = seedFor(seedId, i + 800);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float startHeightFrac = 0.4f + 0.4f * rngFloat(s);

            float t = frac(time * DRIP_RATE + phase);
            float startY = cyFoot + height * startHeightFrac;
            float len = height * 0.2f;
            float headY = startY - t * (startY - cyFoot);
            float tailY = Math.max(cyFoot, headY - len);

            float alpha = 0.7f * (1f - smoothstep(0.75f, 1f, t));
            if (alpha <= 0.01f) {
                continue;
            }

            float px = cx + (float) Math.cos(ang) * width * 0.32f;
            float pz = cz + (float) Math.sin(ang) * width * 0.32f;

            verticalLine(b, m, px, pz, headY, tailY, DRIP_HALF_WIDTH, C_BLOOD_MID, alpha);
        }
    }

    /**
     * 竖直血线（面向世界 X 轴的四边形），顶端实、底端渐隐为 0。
     * <p>
     * v6：颜色参数由 {@code int} 改为已解包的 {@code float[]}——原实现每条线都要
     * {@code unpack} 一次（6 次/帧），而调用方传的始终是同一个固定色，
     * 改由调用方传预解包常量后这里零分配。
     * </p>
     *
     * @param col 已解包的颜色 {@code [r, g, b]}（本渲染器传 {@link #C_BLOOD_MID}）
     */
    private static void verticalLine(BufferBuilder b, Matrix4f m,
                                     float x, float z, float yTop, float yBottom,
                                     float hw, float[] col, float alpha) {
        if (yTop <= yBottom) {
            return;
        }
        b.vertex(m, x - hw, yTop, z).color(col[0], col[1], col[2], alpha).endVertex();
        b.vertex(m, x + hw, yTop, z).color(col[0], col[1], col[2], alpha).endVertex();
        b.vertex(m, x + hw, yBottom, z).color(col[0], col[1], col[2], 0f).endVertex();

        b.vertex(m, x - hw, yTop, z).color(col[0], col[1], col[2], alpha).endVertex();
        b.vertex(m, x + hw, yBottom, z).color(col[0], col[1], col[2], 0f).endVertex();
        b.vertex(m, x - hw, yBottom, z).color(col[0], col[1], col[2], 0f).endVertex();
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

    /**
     * 面向相机的柔光圆点（中心不透明、边缘全透明的三角扇）。
     *
     * @param segments 分段数。v5 起由调用方按细节系数传入，
     *                 下限 {@link #DROP_SEGMENTS_MIN}；全细节时即 {@link #DROP_SEGMENTS}。
     *                 <b>这是本渲染器最大的顶点杠杆</b>——血滴与雾团合计 33 个柔光块，
     *                 每块每段 3 个顶点，8 段降到 4 段即省下近 400 个顶点。
     */
    private static void emitSoftDrop(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz, float size,
                                     float r, float g, float bl, float alpha,
                                     float rightX, float rightY, float rightZ,
                                     float upX, float upY, float upZ, int segments) {
        float pex = 0f, pey = 0f, pez = 0f;
        for (int i = 0; i <= segments; i++) {
            float ang = TAU * i / segments;
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
        float t = (x - e0) / (e1 - e0);
        if (t < 0f) {
            t = 0f;
        } else if (t > 1f) {
            t = 1f;
        }
        return t * t * (3f - 2f * t);
    }
}
