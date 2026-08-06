package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.network.ScarletRotSyncHandler;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 猩红腐败「红雾」客户端渲染器（纯客户端自绘，还原艾尔登法环猩红沼泽弥漫的红色孢子雾）。
 * <p>
 * <b>判定采用三重冗余（关键）：</b>原版 {@link MobEffect} 只对「玩家自己」完整同步到客户端；对
 * <b>其他实体</b>（尤其是怪物），原版仅在玩家<b>开始追踪该实体的那一刻</b>同步一次当时的效果列表，此后追踪期间
 * 该实体的效果变化（新加 / 移除 / 过期）<b>不会</b>再同步给观察者。因此战斗中给怪物施加猩红腐败后，
 * 观察者客户端的 {@code entity.hasEffect(SCARLET_ROT)} 仍为 {@code false}，导致
 * 本渲染器无法据此渲染红雾——这正是「打怪上腐败却看不到红雾」的根因。为兼顾「可靠」与
 * 「覆盖战斗中的怪」，本渲染器对每个实体用三个条件取并集判定其是否带腐败（见 {@link #shouldRenderRot}）：
 * <ol>
 *     <li>{@code entity.hasEffect(SCARLET_ROT)}——覆盖玩家自己、以及在玩家开始追踪前就已带腐败的实体；</li>
 *     <li>{@link ClientSyncEffectManager#shouldRenderEffect}（序列号 {@link ScarletRotSyncHandler#SCARLET_ROT_SERIAL}）
 *         ——覆盖战斗中被施加腐败的怪（依赖 {@link ScarletRotSyncHandler} 监听 MobEffectEvent 同步生效）；</li>
 *     <li>{@link #hasAeonia}（主手带腐败女神附魔）——腐败女神持有者必带永久腐败，附魔随物品 NBT 可靠同步。</li>
 * </ol>
 * 即便同步链路(2)完全失效，(1)(3)也能退回到「玩家自己 + 腐败女神」的可见水平，不会全黑。
 * <p>
 * <b>腐败女神光环独立判定：</b>感染源光环只看「主手是否带 aeonia」(条件 3)，<b>不嵌套在腐败判定之内</b>，
 * 故无论腐败同步链路如何，腐败女神的大光环必定显示。
 * <p>
 * <b>孢子形状：</b>每颗孢子用「面向相机的圆形径向渐变」绘制（{@link #emitSoftSpore}，中心不透明、边缘
 * 渐隐为 0，由 {@link #SPORE_SEGMENTS} 段三角扇拼成柔和圆光斑），放大后呈化开的雾团而非尖锐红点。
 * <p>
 * <b>两类视觉：</b>(1) 普通中毒红雾——脚下水平红雾盘 + 环绕上升的猩红孢子点云；(2) 腐败女神 aeonia
 * 感染源光环——大范围地面腐败浸染盘（视觉半径 {@link #AEONIA_FIELD_RADIUS}，远小于附魔实际 32 格效果
 * 半径，纯为观感）+ 感染半径内地面各处升腾的孢子柱 +（可选）脚下缓慢旋转的核心孢子环。整体偏深红更浓。
 * <p>
 * <b>浓度与淡出：</b>客户端无法可靠取得其他实体效果的等级与剩余时间（不随效果同步），故普通红雾统一
 * 基础浓度（{@code amplifier=0}）、不做到期淡出（{@code fade=1}）；出现 / 消失由判定条件控制。
 * <p>
 * <b>v2（性能，视觉零变化）：</b>接入 {@link VisualBatch} 与 {@link SharedEntityQuery}——
 * <ul>
 *     <li>不再自行设置 / 恢复 GL 状态、不再自行 {@code begin/end} 顶点缓冲，改为向
 *         {@link VisualBatch} 提供的共享缓冲写顶点，由其在本帧末统一提交；</li>
 *     <li>不再自行做范围实体查询，改为遍历 {@link SharedEntityQuery} 的每帧共享列表，
 *         把原先的三重冗余查询谓词下沉为循环内的 {@code continue}（见 {@link #shouldRenderRot}）。
 *         相比原先每帧新建一个 ArrayList，现在整帧共用一个列表。</li>
 * </ul>
 * 判定条件、精确平方距离裁剪、绘制顺序与全部几何参数均未改动。
 * <p>
 * <b>其余性能要点：</b>孢子用无分配 xorshift 即时计算；逐实体精确距离裁剪；
 * {@code aeonia} 附魔懒解析缓存。所有外观参数集中在顶部常量，便于调整。
 *
 * @author FlameForge
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ScarletRotMistRenderer {

    /** 腐败女神附魔注册 id（与 {@code AuraDisplayRegistry} 一致，按 {@code carianstyle:<id>} 解析） */
    private static final String AEONIA_ENCHANT_ID = "aeonia";

    /** 距离裁剪（格）：相机太远的患者本帧不绘制。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}。 */
    private static final double CULL = 48.0;
    /** 距离裁剪的平方（避免开方） */
    private static final double CULL_SQR = CULL * CULL;
    /** 2π */
    private static final float TAU = (float) (Math.PI * 2.0);
    /** 离地高度偏移，避免地面雾盘与地面 z-fighting */
    private static final float Y_OFFSET = 0.02f;
    /** 单颗孢子圆的三角扇段数（越大越圆越柔；用于把孢子画成柔和圆光斑而非尖锐菱形） */
    private static final int SPORE_SEGMENTS = 10;
    /**
     * 渲染器起始墙钟毫秒（类加载时固定）。
     * <p>动画时间必须用「当前毫秒 - 此起始值」的<b>差值</b>再转 float，而非直接 {@code currentTimeMillis()/1000f}：
     * 后者数值约 1.7e12，超出 float 有效精度（float 仅约 7 位有效数字，该量级下最小分辨间隔达十几万毫秒），
     * 会导致每帧算出的时间完全相同、动画彻底静止。用运行时长差值（数值小）转 float 才能保证逐帧平滑推进。</p>
     */
    private static final long START_MILLIS = System.currentTimeMillis();

    // ===== 配色（0xRRGGBB，取自艾尔登法环猩红主题，与 AoeEffectRenderer 一致）=====
    /** 猩红（孢子上升后期的亮红） */
    private static final int SCARLET = 0xE0244A;
    /** 深猩红（孢子初生 / 地面雾的深色） */
    private static final int SCARLET_DEEP = 0x800018;
    /** 暗血色（地面雾最底层的更暗铺底） */
    private static final int SCARLET_HAZE = 0x5A0012;

    // ===== 普通中毒：上升孢子参数（贴脸单挑档）=====
    /** 基础孢子数（统一浓度，客户端不区分等级） */
    private static final int BASE_SPORES = 16;
    /** 每级附加孢子数（保留参数；当前渲染统一用 amplifier=0，不生效） */
    private static final int SPORES_PER_LEVEL = 4;
    /** 孢子数上限（防止顶点爆量） */
    private static final int MAX_SPORES = 32;
    /** 孢子上升循环速度（每秒推进的归一化进度，越大升得越快） */
    private static final float RISE_SPEED = 0.22f;
    /** 孢子柔光圆半径基准（格，贴脸看故加大） */
    private static final float SPORE_SIZE = 0.13f;
    /** 孢子上升最大高度系数（× 实体高度，>1 使孢子升到头顶以上） */
    private static final float RISE_HEIGHT_FACTOR = 1.5f;
    /** 孢子水平分布半径系数（× 实体宽度） */
    private static final float SPREAD_FACTOR = 1.0f;
    /** 孢子整体不透明度基准（圆形渐变已较柔，可略降以更雾化） */
    private static final float SPORE_BASE_ALPHA = 0.6f;

    // ===== 普通中毒：地面红雾盘参数 =====
    /** 地面雾盘层数（不同半径叠出弥漫层次） */
    private static final int GROUND_LAYERS = 2;
    /** 地面雾盘分段数（圆的细分，越大越圆、顶点越多） */
    private static final int GROUND_SEGMENTS = 24;
    /** 地面雾盘最内层基准半径系数（× 实体宽度） */
    private static final float GROUND_RADIUS_BASE = 1.6f;
    /** 地面雾盘每层半径递增系数（× 实体宽度） */
    private static final float GROUND_RADIUS_STEP = 0.7f;
    /** 地面雾盘中心不透明度基准（逐层递减） */
    private static final float GROUND_BASE_ALPHA = 0.26f;

    // ===== 腐败女神 aeonia：感染源光环参数 =====
    /** 感染源光环视觉半径（格）。<b>远小于附魔实际 32 格效果半径</b>，纯为观感，避免糊满屏幕 */
    private static final float AEONIA_FIELD_RADIUS = 7.0f;
    /** 感染源地面浸染盘层数 */
    private static final int AEONIA_GROUND_LAYERS = 3;
    /** 感染源地面浸染盘分段数 */
    private static final int AEONIA_GROUND_SEGMENTS = 32;
    /** 感染源地面浸染盘中心不透明度基准（大盘须更淡避免糊屏，逐层递减） */
    private static final float AEONIA_GROUND_ALPHA = 0.15f;
    /** 感染源环境升腾孢子数（在感染半径内地面各处升起） */
    private static final int AEONIA_FIELD_SPORES = 32;
    /** 感染源环境孢子上升循环速度 */
    private static final float AEONIA_RISE_SPEED = 0.16f;
    /** 感染源环境孢子上升高度（格，地面升起的孢子柱高度） */
    private static final float AEONIA_SPORE_HEIGHT = 2.6f;
    /** 感染源环境孢子柔光圆半径基准（格） */
    private static final float AEONIA_SPORE_SIZE = 0.13f;
    /** 感染源环境孢子不透明度基准 */
    private static final float AEONIA_SPORE_ALPHA = 0.58f;
    /** 感染源环境孢子随高度的螺旋外散强度 */
    private static final float AEONIA_SWIRL = 0.6f;
    /**
     * 感染源核心孢子环：光点数。
     * <p><b>默认 0（关闭）</b>——脚下这圈规律旋转的光点最容易被看成「一圈红点」，故默认不画；
     * 若想要脚下核心标记，设为如 12 即可开启。</p>
     */
    private static final int AEONIA_CORE_RING_COUNT = 0;
    /** 感染源核心孢子环：半径（格） */
    private static final float AEONIA_CORE_RING_RADIUS = 1.2f;
    /** 感染源核心孢子环：旋转速度 */
    private static final float AEONIA_CORE_RING_SPEED = 0.3f;
    /** 感染源核心孢子环：离地高度（格） */
    private static final float AEONIA_CORE_RING_HEIGHT = 0.15f;
    /** 感染源核心孢子环：柔光圆半径（格） */
    private static final float AEONIA_CORE_RING_SIZE = 0.14f;

    /** 腐败女神附魔懒解析缓存（注册表在 mod 加载后才可用，首次解析成功后固定） */
    private static Enchantment aeoniaCache;
    /** 腐败女神附魔是否已成功解析 */
    private static boolean aeoniaResolved;

    private ScarletRotMistRenderer() {
    }

    /**
     * 世界渲染回调：在半透明方块之后，绘制相机附近所有带猩红腐败生物的红雾，
     * 并为其中的腐败女神持有者叠加感染源光环。
     * <p>
     * v2：GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理，实体列表取自
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

        // 猩红腐败效果对象（用于 hasEffect 兜底判定）；腐败女神附魔（懒解析，可能为 null）
        final MobEffect rot = CarianStylePotion.SCARLET_ROT.get();
        final Enchantment aeonia = resolveAeonia();

        // 相机朝向向量，用于让孢子柔光圆始终正面朝向相机（billboard）
        Matrix4f matrix = VisualBatch.matrix();
        float rightX = VisualBatch.rightX();
        float rightY = VisualBatch.rightY();
        float rightZ = VisualBatch.rightZ();
        float upX = VisualBatch.upX();
        float upY = VisualBatch.upY();
        float upZ = VisualBatch.upZ();

        float partial = VisualBatch.partialTick();
        // 墙钟驱动孢子动画，与世界 tick 解耦，飘升更平滑、不受 TPS 卡顿影响。
        // 必须用「运行时长差值」转 float：直接用 currentTimeMillis()/1000f 数值过大、float 精度不足，
        // 会导致每帧时间相同、动画静止（见 START_MILLIS 注释）。
        float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;

        for (LivingEntity entity : candidates) {
            // v2：原先作为查询谓词的三重冗余判定，现下沉为循环内筛选（共享列表已保证 isAlive）
            if (!shouldRenderRot(entity, rot, aeonia)) {
                continue;
            }

            // 实体实时插值位置（脚底中心）
            double ex = Mth.lerp((double) partial, entity.xo, entity.getX());
            double ey = Mth.lerp((double) partial, entity.yo, entity.getY());
            double ez = Mth.lerp((double) partial, entity.zo, entity.getZ());

            // 逐实体精确距离裁剪（AABB 为立方，对角线更远，这里按平方距离再剔除一次）
            double dx = ex - cam.x;
            double dy = ey - cam.y;
            double dz = ez - cam.z;
            if (dx * dx + dy * dy + dz * dz > CULL_SQR) {
                continue;
            }

            // 是否腐败女神持有者（独立判定，决定是否叠加感染源光环）
            boolean isAeonia = hasAeonia(entity, aeonia);

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            // 相对相机坐标
            float rx = (float) dx;
            float ryGround = (float) dy + Y_OFFSET;
            float rz = (float) dz;

            // 1) 普通红雾（进入判定者皆视为带腐败）：地面雾盘（背景层）+ 上升孢子（前景层）。
            // amplifier 固定 0、fade 固定 1——客户端无法可靠取得其他实体效果的等级与剩余时间。
            drawGroundHaze(builder, matrix, rx, ryGround, rz, width, time, 0, 1f, entity.getId());
            drawRisingSpores(builder, matrix, rx, (float) dy, rz, width, height, time, 0, 1f,
                    entity.getId(), rightX, rightY, rightZ, upX, upY, upZ);

            // 2) 腐败女神感染源光环（独立判定，必定随持有者显示）
            if (isAeonia) {
                drawAeoniaInfectionField(builder, matrix, rx, (float) dy, rz, time, 1f, entity.getId(),
                        rightX, rightY, rightZ, upX, upY, upZ);
            }
        }
    }

    /**
     * 判断实体是否应显示猩红腐败红雾（三重冗余判定，与优化前的查询谓词逐条一致）。
     *
     * @param entity 待判定实体
     * @param rot    猩红腐败效果对象（可能为 null）
     * @param aeonia 腐败女神附魔（可能为 null）
     * @return 应显示返回 true
     */
    private static boolean shouldRenderRot(LivingEntity entity,
                                           @Nullable MobEffect rot,
                                           @Nullable Enchantment aeonia) {
        if (rot != null && entity.hasEffect(rot)) {
            return true;
        }
        if (ClientSyncEffectManager.shouldRenderEffect(
                ScarletRotSyncHandler.SCARLET_ROT_SERIAL, entity.getId())) {
            return true;
        }
        return hasAeonia(entity, aeonia);
    }

    /**
     * 判断实体主手是否带腐败女神附魔（即是否为感染源持有者）。
     * <p>附魔随物品 NBT 正常同步，对所有实体（含怪物）都可靠。</p>
     *
     * @param entity 实体
     * @param aeonia 腐败女神附魔（可能为 null）
     * @return 主手带腐败女神返回 true
     */
    private static boolean hasAeonia(LivingEntity entity, @Nullable Enchantment aeonia) {
        if (aeonia == null) {
            return false;
        }
        ItemStack main = entity.getMainHandItem();
        return !main.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(aeonia, main) > 0;
    }

    /**
     * 懒解析腐败女神附魔对象（注册表在 mod 加载后才可用，故首次调用时解析并缓存）。
     *
     * @return 腐败女神附魔；未注册（如被禁用）时返回 null
     */
    @Nullable
    private static Enchantment resolveAeonia() {
        if (!aeoniaResolved) {
            aeoniaCache = ForgeRegistries.ENCHANTMENTS.getValue(
                    new ResourceLocation(Reference.MOD_ID, AEONIA_ENCHANT_ID));
            // 仅在成功解析后才标记完成，否则下次重试
            aeoniaResolved = (aeoniaCache != null);
        }
        return aeoniaCache;
    }

    // ==================== 普通中毒视觉 ====================

    /**
     * 绘制脚下地面红雾盘：{@link #GROUND_LAYERS} 层不同半径的水平渐变圆盘（中心浓、边缘淡出），
     * 随时间缓慢呼吸，营造弥漫地表的红雾。
     *
     * @param b         缓冲
     * @param m         变换矩阵
     * @param cx        中心相对相机 X
     * @param cy        雾盘高度相对相机 Y（实体脚底 + 偏移）
     * @param cz        中心相对相机 Z
     * @param width     实体宽度（决定雾盘半径）
     * @param time      墙钟时间（秒）
     * @param amplifier 效果等级（当前统一传 0）
     * @param fade      整体淡出系数（当前统一传 1）
     * @param seedId    实体 id（错开各实体的呼吸相位）
     */
    private static void drawGroundHaze(BufferBuilder b, Matrix4f m,
                                       float cx, float cy, float cz, float width,
                                       float time, int amplifier, float fade, int seedId) {
        // 等级带来的额外浓度（每级 +0.02，封顶约 +0.1；当前 amplifier=0 即无加成）
        float ampBoost = Math.min(0.10f, amplifier * 0.02f);
        // 各实体独立的呼吸相位
        float breath = 0.9f + 0.1f * Mth.sin(time * 1.5f + seedId * 0.7f);
        // 颜色在暗血色与深猩红之间缓慢脉动
        int col = lerpRgb(SCARLET_HAZE, SCARLET_DEEP, 0.5f + 0.5f * Mth.sin(time * 0.8f + seedId));
        float cr = ((col >> 16) & 0xFF) / 255f;
        float cg = ((col >> 8) & 0xFF) / 255f;
        float cb = (col & 0xFF) / 255f;

        for (int layer = 0; layer < GROUND_LAYERS; layer++) {
            float radius = width * (GROUND_RADIUS_BASE + layer * GROUND_RADIUS_STEP) * breath;
            float centerAlpha = (GROUND_BASE_ALPHA + ampBoost - layer * 0.06f) * fade;
            if (centerAlpha <= 0f || radius <= 0.05f) {
                continue;
            }
            drawDisc(b, m, cx, cy, cz, radius, GROUND_SEGMENTS, cr, cg, cb, centerAlpha);
        }
    }

    /**
     * 绘制环绕实体上升的猩红孢子点云。
     * <p>每个孢子按 {@code 实体id + 序号} 取稳定种子，决定其相位 / 角度 / 半径 / 大小 / 螺旋方向，
     * 故分布稳定且各孢子错相。归一化进度 t 由墙钟循环推进：高度随 t 上升、底部淡入顶部淡出、
     * 颜色由深红转亮红、伴随细微明灭、随高度螺旋外散。每颗孢子为柔和圆光斑（见 {@link #emitSoftSpore}）。</p>
     *
     * @param b         缓冲
     * @param m         变换矩阵
     * @param cx        实体脚底相对相机 X
     * @param cyFoot    实体脚底相对相机 Y
     * @param cz        实体脚底相对相机 Z
     * @param width     实体宽度（决定水平分布半径）
     * @param height    实体高度（决定上升高度）
     * @param time      墙钟时间（秒）
     * @param amplifier 效果等级（当前统一传 0）
     * @param fade      整体淡出系数（当前统一传 1）
     * @param seedId    实体 id（孢子分布种子基底）
     * @param rightX    相机右向量 X（billboard）
     * @param rightY    相机右向量 Y
     * @param rightZ    相机右向量 Z
     * @param upX       相机上向量 X（billboard）
     * @param upY       相机上向量 Y
     * @param upZ       相机上向量 Z
     */
    private static void drawRisingSpores(BufferBuilder b, Matrix4f m,
                                         float cx, float cyFoot, float cz, float width, float height,
                                         float time, int amplifier, float fade, int seedId,
                                         float rightX, float rightY, float rightZ,
                                         float upX, float upY, float upZ) {
        int count = Math.min(MAX_SPORES, BASE_SPORES + amplifier * SPORES_PER_LEVEL);
        float riseHeight = height * RISE_HEIGHT_FACTOR;
        float spread = width * SPREAD_FACTOR;

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i);
            // 逐项取稳定随机参数
            float phase = rngFloat(s);          // 上升相位偏移
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;       // 初始水平角度
            s = rngNext(s);
            float radFactor = 0.3f + 0.7f * rngFloat(s); // 水平半径系数 0.3~1.0
            s = rngNext(s);
            float sizeRand = 0.7f + 0.6f * rngFloat(s);  // 大小随机 0.7~1.3
            s = rngNext(s);
            float twPhase = rngFloat(s) * TAU;   // 明灭相位
            s = rngNext(s);
            float swirl = (rngFloat(s) - 0.5f) * 2.0f;   // 螺旋方向/强度 -1~1

            // 归一化上升进度 t∈[0,1)
            float t = frac(time * RISE_SPEED + phase);

            // 透明度包络：底部淡入(<0.15)、顶部淡出(>0.55)
            float env;
            if (t < 0.15f) {
                env = t / 0.15f;
            } else if (t > 0.55f) {
                env = 1f - (t - 0.55f) / 0.45f;
            } else {
                env = 1f;
            }
            if (env <= 0f) {
                continue;
            }
            // 细微明灭
            float twinkle = 0.7f + 0.3f * Mth.sin(time * 3f + twPhase);
            float alpha = SPORE_BASE_ALPHA * env * twinkle * fade;
            if (alpha <= 0.01f) {
                continue;
            }

            // 位置：随高度螺旋外散
            float curRad = spread * radFactor * (0.6f + 0.4f * t);
            float a = ang + t * swirl;
            float px = cx + (float) Math.cos(a) * curRad;
            float pz = cz + (float) Math.sin(a) * curRad;
            float py = cyFoot + t * riseHeight + Y_OFFSET;

            // 颜色：底深顶亮
            int col = lerpRgb(SCARLET_DEEP, SCARLET, t);
            float cr = ((col >> 16) & 0xFF) / 255f;
            float cg = ((col >> 8) & 0xFF) / 255f;
            float cb = (col & 0xFF) / 255f;

            // 大小：上升过程略微缩小（消散感）× 随机
            float size = SPORE_SIZE * sizeRand * (1.1f - 0.4f * t);

            emitSoftSpore(b, m, px, py, pz, size, cr, cg, cb, alpha,
                    rightX, rightY, rightZ, upX, upY, upZ);
        }
    }

    // ==================== 腐败女神 aeonia 感染源光环 ====================

    /**
     * 绘制腐败女神感染源光环（叠加于持有者普通红雾之上）。
     * <p>三部分共同表达「腐败从核心向外浸染大地」：
     * <ol>
     *     <li><b>大范围地面腐败浸染盘</b>（{@link #AEONIA_GROUND_LAYERS} 层、半径 {@link #AEONIA_FIELD_RADIUS}，
     *         低 alpha、错相呼吸、颜色脉动）；</li>
     *     <li><b>环境升腾孢子</b>（在感染半径内地面各处的稳定位置——黄金角 + 面积均匀分布——持续错相
     *         升起的孢子柱，随高度螺旋外散、淡入淡出）；</li>
     *     <li><b>脚下核心孢子环</b>（缓慢旋转的一圈猩红柔光圆，标记腐败核心；{@link #AEONIA_CORE_RING_COUNT}
     *         默认 0 关闭，设 >0 开启）。</li>
     * </ol>
     * 全部复用普通红雾的 {@link #drawDisc} / {@link #emitSoftSpore} 与配色，整体偏深红、比普通中毒更浓。</p>
     *
     * @param b      缓冲
     * @param m      变换矩阵
     * @param cx     实体脚底相对相机 X
     * @param cyFoot 实体脚底相对相机 Y
     * @param cz     实体脚底相对相机 Z
     * @param time   墙钟时间（秒）
     * @param fade   整体淡出系数（当前统一传 1）
     * @param seedId 实体 id（分布与相位种子基底）
     * @param rightX 相机右向量 X（billboard）
     * @param rightY 相机右向量 Y
     * @param rightZ 相机右向量 Z
     * @param upX    相机上向量 X（billboard）
     * @param upY    相机上向量 Y
     * @param upZ    相机上向量 Z
     */
    private static void drawAeoniaInfectionField(BufferBuilder b, Matrix4f m,
                                                 float cx, float cyFoot, float cz,
                                                 float time, float fade, int seedId,
                                                 float rightX, float rightY, float rightZ,
                                                 float upX, float upY, float upZ) {
        float groundY = cyFoot + Y_OFFSET;

        // —— 1) 大范围地面腐败浸染盘 ——
        float breath = 0.92f + 0.08f * Mth.sin(time * 1.2f + seedId * 0.5f);
        for (int layer = 0; layer < AEONIA_GROUND_LAYERS; layer++) {
            float radius = AEONIA_FIELD_RADIUS * (1f - layer * 0.28f) * breath;
            if (radius <= 0.05f) {
                continue;
            }
            float centerAlpha = (AEONIA_GROUND_ALPHA - layer * 0.03f) * fade;
            if (centerAlpha <= 0f) {
                continue;
            }
            int col = lerpRgb(SCARLET_HAZE, SCARLET_DEEP, 0.4f + 0.4f * Mth.sin(time * 0.6f + layer * 1.3f + seedId));
            float cr = ((col >> 16) & 0xFF) / 255f;
            float cg = ((col >> 8) & 0xFF) / 255f;
            float cb = (col & 0xFF) / 255f;
            drawDisc(b, m, cx, groundY, cz, radius, AEONIA_GROUND_SEGMENTS, cr, cg, cb, centerAlpha);
        }

        // —— 2) 环境升腾孢子（地面各处稳定位置错相升起）——
        for (int i = 0; i < AEONIA_FIELD_SPORES; i++) {
            // 黄金角螺旋 + sqrt 半径 = 均匀面积分布（稳定不闪跳）
            float ringFrac = (i + 0.5f) / AEONIA_FIELD_SPORES;
            float baseRad = AEONIA_FIELD_RADIUS * (float) Math.sqrt(ringFrac);
            float baseAng = i * 2.399963f; // 黄金角（弧度）

            // 独立随机参数（种子基底与身上孢子区分，避免撞）
            long s = seedFor(seedId * 31 + 7, i);
            float phase = rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.7f + 0.6f * rngFloat(s);
            s = rngNext(s);
            float swirlDir = (rngFloat(s) - 0.5f) * 2f;
            s = rngNext(s);
            float twPhase = rngFloat(s) * TAU;

            float t = frac(time * AEONIA_RISE_SPEED + phase);
            // 包络：底部淡入(<0.15)、顶部淡出(>0.6)
            float env;
            if (t < 0.15f) {
                env = t / 0.15f;
            } else if (t > 0.6f) {
                env = 1f - (t - 0.6f) / 0.4f;
            } else {
                env = 1f;
            }
            if (env <= 0f) {
                continue;
            }
            float twinkle = 0.7f + 0.3f * Mth.sin(time * 2.5f + twPhase);
            float alpha = AEONIA_SPORE_ALPHA * env * twinkle * fade;
            if (alpha <= 0.01f) {
                continue;
            }

            // 水平位置（随高度轻微螺旋外散）
            float a = baseAng + t * AEONIA_SWIRL * swirlDir;
            float rr = baseRad + t * 0.3f;
            float gx = cx + (float) Math.cos(a) * rr;
            float gz = cz + (float) Math.sin(a) * rr;
            float gy = cyFoot + t * AEONIA_SPORE_HEIGHT + Y_OFFSET;

            int col = lerpRgb(SCARLET_DEEP, SCARLET, t);
            float cr = ((col >> 16) & 0xFF) / 255f;
            float cg = ((col >> 8) & 0xFF) / 255f;
            float cb = (col & 0xFF) / 255f;
            float size = AEONIA_SPORE_SIZE * sizeRand * (1.1f - 0.4f * t);

            emitSoftSpore(b, m, gx, gy, gz, size, cr, cg, cb, alpha,
                    rightX, rightY, rightZ, upX, upY, upZ);
        }

        // —— 3) 脚下核心孢子环（缓慢旋转，标记腐败核心；默认关闭）——
        if (AEONIA_CORE_RING_COUNT > 0) {
            float rotAng = time * AEONIA_CORE_RING_SPEED;
            float ringY = cyFoot + AEONIA_CORE_RING_HEIGHT + Y_OFFSET;
            for (int i = 0; i < AEONIA_CORE_RING_COUNT; i++) {
                float a = rotAng + TAU * i / AEONIA_CORE_RING_COUNT;
                float px = cx + (float) Math.cos(a) * AEONIA_CORE_RING_RADIUS;
                float pz = cz + (float) Math.sin(a) * AEONIA_CORE_RING_RADIUS;
                float twinkle = 0.6f + 0.4f * Mth.sin(time * 4f + i * 0.8f);
                int col = lerpRgb(SCARLET_DEEP, SCARLET, 0.5f + 0.5f * Mth.sin(a * 2f + time));
                float cr = ((col >> 16) & 0xFF) / 255f;
                float cg = ((col >> 8) & 0xFF) / 255f;
                float cb = (col & 0xFF) / 255f;
                float alpha = 0.6f * twinkle * fade;
                emitSoftSpore(b, m, px, ringY, pz, AEONIA_CORE_RING_SIZE, cr, cg, cb, alpha,
                        rightX, rightY, rightZ, upX, upY, upZ);
            }
        }
    }

    // ==================== 通用绘制基元 ====================

    /**
     * 绘制一个水平径向渐变圆盘（中心 {@code centerAlpha}、边缘 0 的三角扇）。
     *
     * @param b           缓冲
     * @param m           变换矩阵
     * @param cx          中心相对相机 X
     * @param cy          高度相对相机 Y（圆盘水平，y 固定）
     * @param cz          中心相对相机 Z
     * @param radius      半径
     * @param segments    分段数
     * @param r           颜色 R（0~1）
     * @param g           颜色 G（0~1）
     * @param bl          颜色 B（0~1）
     * @param centerAlpha 中心不透明度（边缘恒为 0）
     */
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
     * 绘制一颗面向相机的柔和圆形孢子（径向渐变：中心 {@code alpha}、边缘 0）。
     * <p>由 {@link #SPORE_SEGMENTS} 段三角扇拼成一个正对相机的圆——相比中心 + 四角的菱形，
     * 圆形中心不再尖锐、边缘平滑渐隐，放大后像化开的雾团而非尖锐红点。圆平面由相机右 / 上向量
     * 张成，故始终正对相机。</p>
     *
     * @param b      缓冲
     * @param m      变换矩阵
     * @param cx     中心相对相机 X
     * @param cy     中心相对相机 Y
     * @param cz     中心相对相机 Z
     * @param size   圆半径（格）
     * @param r      颜色 R（0~1）
     * @param g      颜色 G（0~1）
     * @param bl     颜色 B（0~1）
     * @param alpha  中心不透明度（边缘恒为 0）
     * @param rightX 相机右向量 X
     * @param rightY 相机右向量 Y
     * @param rightZ 相机右向量 Z
     * @param upX    相机上向量 X
     * @param upY    相机上向量 Y
     * @param upZ    相机上向量 Z
     */
    private static void emitSoftSpore(BufferBuilder b, Matrix4f m,
                                      float cx, float cy, float cz, float size,
                                      float r, float g, float bl, float alpha,
                                      float rightX, float rightY, float rightZ,
                                      float upX, float upY, float upZ) {
        float pex = 0f, pey = 0f, pez = 0f;
        for (int i = 0; i <= SPORE_SEGMENTS; i++) {
            float ang = TAU * i / SPORE_SEGMENTS;
            float ca = (float) Math.cos(ang) * size;
            float sa = (float) Math.sin(ang) * size;
            // 边缘点 = 中心 + 右向量*ca + 上向量*sa（正对相机的圆周）
            float ex = cx + rightX * ca + upX * sa;
            float ey = cy + rightY * ca + upY * sa;
            float ez = cz + rightZ * ca + upZ * sa;
            if (i > 0) {
                // 三角扇：中心(alpha) + 上一圆周点(0) + 当前圆周点(0)
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

    /**
     * 由 实体 id 与孢子序号生成一个非 0 的稳定种子。
     *
     * @param entityId   实体网络 id（或派生基底）
     * @param sporeIndex 孢子序号
     * @return 非 0 种子
     */
    private static long seedFor(int entityId, int sporeIndex) {
        long s = (entityId * 0x9E3779B97F4A7C15L) ^ ((sporeIndex + 1L) * 0x85EBCA6BL);
        s ^= (s >>> 29);
        return s == 0L ? 0x9E3779B97F4A7C15L : s;
    }

    /**
     * xorshift64 推进一步。
     *
     * @param s 当前状态（非 0）
     * @return 下一状态
     */
    private static long rngNext(long s) {
        s ^= s << 13;
        s ^= s >>> 7;
        s ^= s << 17;
        return s;
    }

    /**
     * 由状态取 [0,1) 浮点。
     *
     * @param s 状态
     * @return [0,1)
     */
    private static float rngFloat(long s) {
        return ((s >>> 40) & 0xFFFFFFL) / (float) 0x1000000;
    }

    // ==================== 数学 / 颜色辅助 ====================

    /**
     * 取小数部分（结果恒在 [0,1)）。
     *
     * @param x 输入
     * @return 小数部分
     */
    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    /**
     * 在两个 0xRRGGBB 之间线性插值（t 自动夹取到 [0,1]），返回 0xRRGGBB。
     *
     * @param from 起色
     * @param to   终色
     * @param t    插值系数
     * @return 插值后的 0xRRGGBB
     */
    private static int lerpRgb(int from, int to, float t) {
        if (t < 0f) {
            t = 0f;
        } else if (t > 1f) {
            t = 1f;
        }
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int bl = Math.round(fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
