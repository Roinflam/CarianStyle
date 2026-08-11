package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.network.IncisionSyncHandler;
import pers.roinflam.carianstyle.potion.MobEffectIncision;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 切腹「狂化」客户端渲染器（纯客户端自绘）。
 * <p>
 * 对应 {@link MobEffectIncision}：消耗 50% 最大生命进入 10 秒状态，期间每秒流失 12.5% 最大生命、
 * 移速 +120%（持续衰减至 +30%）、伤害 +40%、攻速 +80%、护甲 -75%、恢复 +60%，造成伤害回复 25%，
 * 击杀延长持续时间。机制上是「自残换爆发」的血战状态。
 * </p>
 * <p>
 * <b>判定采用双重冗余：</b>{@code entity.hasEffect(CarianStylePotion.INCISION.get())}
 * <b>或</b> {@code ClientSyncEffectManager.shouldRenderEffect(IncisionSyncHandler.INCISION_SERIAL, id)}。
 * 与出血 / 冻伤不同的是，切腹是<b>自身增益</b>——玩家自己进入切腹时原版同步完整可靠，
 * 单靠前者即可正确显示；后者只负责补上「观察其它实体的切腹状态」这一档
 * （详见 {@link IncisionSyncHandler} 类注释）。
 * </p>
 * <p>
 * <b>与出血渲染器的视觉区分（关键设计约束）：</b>{@code HemorrhageBloodRenderer} 已经占用了
 * 「血泊 + 垂落血线 + 抛物线血滴 + 迸溅射线 + 血雾」这一整套语言。两者可能同时挂在同一实体上，
 * 若视觉雷同则完全无法分辨。故本渲染器刻意采用<b>反向</b>的语言：
 * <ul>
 *     <li><b>方向相反</b>——出血是向下的（血泊沉积、血线垂落、血滴坠地）；切腹是向上的
 *         （血气上涌、碎片升腾、疾走外散）；</li>
 *     <li><b>色调更暗</b>——出血用鲜红 {@code 0xE0202F}；切腹用氧化暗红 {@link #INCISION_BLOOD}
 *         与近黑红 {@link #INCISION_DARK}，只有刀痕本身是高亮的；</li>
 *     <li><b>语义相反</b>——出血是被动失血的负面态；切腹是主动狂化的增益态。</li>
 * </ul>
 * </p>
 * <p>
 * <b>四个元素：</b>
 * <ol>
 *     <li><b>腹部横向刀痕</b>（{@link #drawGash}）——唯一的标志性主视觉，也是唯一的高亮元素；</li>
 *     <li><b>上升血刃碎片</b>（{@link #drawRisingShards}）——自刀痕处向上飘散的<b>细长菱形</b>碎片
 *         （刻意不用圆形柔光点，与出血的血滴区分）；</li>
 *     <li><b>疾走涟漪</b>（{@link #drawSprintRipples}）——脚下向外扩散的扁平环。
 *         <b>这是唯一与机制直接联动的元素</b>：强度读取 {@code MOVEMENT_SPEED} 上切腹修正器的实时数值
 *         （见 {@link #speedIntensity}），因此会跟着 +120%→+30% 的衰减一起变弱；</li>
 *     <li><b>躯干血气笼罩</b>（{@link #drawBodyHaze}）——贴身的大号暗红雾团，补足体积感。</li>
 * </ol>
 * </p>
 *
 * <h3>v2（顶点量，近距离视觉零变化）：接入 {@link VisualLod}</h3>
 * <p>
 * 单个切腹实体每帧的顶点量粗算：
 * </p>
 * <pre>
 * 疾走涟漪（最多 3 环 × 28 段 × 6）      504
 * 躯干血气（8 雾团 × 10 段 × 3）         240
 * 上升血刃（最多 20 片 × 4 三角）        240
 * 腹部刀痕（10 段 × 6）                   60
 * ─────────────────────────────────────────
 * 合计                              ~1044 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * 切腹是玩家主动开启的爆发状态，团战里往往<b>多人同时开</b>，且与出血、黄金树祝福大量共存。
 * 现按 {@link VisualLod#detail} 缩放元素数量与分段数：
 * {@link VisualLod#FULL_DETAIL_RANGE} 格内系数为 1.0，<b>与优化前逐像素一致</b>；
 * 40 格外单实体降至约 200 顶点。
 * </p>
 * <p>
 * <b>削减策略：</b>
 * </p>
 * <ul>
 *     <li><b>刀痕不削数量、只削分段</b>——它是切腹唯一的辨识符号，且仅 60 顶点，
 *         哪怕最低细节也必须完整可见，只把沿长度的细分从 10 段降到 4 段（轮廓略方，远处不可见）；</li>
 *     <li><b>血刃碎片与雾团按种子截断尾部</b>——位置由 {@code seedFor(entityId, i)} 决定，
 *         保留元素的轨迹完全不变，靠近时是「逐渐多出几片碎片」而非重新洗牌；</li>
 *     <li><b>涟漪减圈数</b>——涟漪的相位是 {@code i / count} 均布的，减少 count 会改变相位间隔，
 *         但涟漪本身就是「一圈接一圈往外推」的循环动画、没有固定方位，
 *         减圈只表现为「波与波之间隔得更开」，观感自然；</li>
 *     <li><b>血气雾团整层可跳过</b>——它是纯体积氛围层，远处完全糊成一片，
 *         低于 {@link #HAZE_KEEP_THRESHOLD} 时整层不画（省 240 顶点，占总量近四分之一）。</li>
 * </ul>
 *
 * @author FlameForge
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class IncisionRenderer {

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    /** 离地高度偏移，避免地面图形与地形 z-fighting */
    private static final float Y_OFFSET = 0.02f;
    /**
     * 渲染器起始墙钟毫秒（类加载时固定）。动画时间必须用「当前毫秒 - 此起始值」的差值再转 float，
     * 直接用 currentTimeMillis()/1000f 数值过大会导致 float 精度不足、动画卡死。
     */
    private static final long START_MILLIS = System.currentTimeMillis();

    // ===== v2 LOD 下限与保留阈值 =====
    /** 刀痕沿长度的最少细分段数：4 段轮廓略方，但两端收尖的形态仍然成立 */
    private static final int GASH_SEGMENTS_MIN = 4;
    /** 涟漪环的最少分段数 */
    private static final int RIPPLE_SEGMENTS_MIN = 8;
    /** 雾团柔光块的最少分段数 */
    private static final int HAZE_SEGMENTS_MIN = 4;
    /** 躯干血气层的保留阈值：纯体积氛围层，远处完全糊成一片 */
    private static final float HAZE_KEEP_THRESHOLD = 0.5f;

    // ===== 配色（0xRRGGBB）=====
    /** 刀痕核心：亮血红，全渲染器唯一的高亮色，确保标志物在任何背景下都能读出 */
    private static final int INCISION_GASH = 0xFF4A55;
    /** 主血色：氧化暗红（刻意比出血的 0xE0202F 暗，避免撞色） */
    private static final int INCISION_BLOOD = 0xA01824;
    /** 近黑红：雾团外缘、地面涟漪的深色端 */
    private static final int INCISION_DARK = 0x2A0408;

    // ===== 腹部横向刀痕（核心标志）=====
    /** 刀痕高度系数（× 实体高度）。取腹部而非胸口，贴合「切腹」意象 */
    private static final float GASH_HEIGHT_FACTOR = 0.45f;
    /** 刀痕半长系数（× 实体宽度） */
    private static final float GASH_HALF_LENGTH_FACTOR = 0.85f;
    /** 刀痕半厚系数（× 实体高度） */
    private static final float GASH_HALF_THICK_FACTOR = 0.055f;
    /** 刀痕沿长度方向的细分段数（越大轮廓越平滑） */
    private static final int GASH_SEGMENTS = 10;
    /** 刀痕心跳脉动速度 */
    private static final float GASH_PULSE_SPEED = 4.5f;
    private static final float GASH_BASE_ALPHA = 0.9f;

    // ===== 上升血刃碎片 =====
    /** 同时存在的碎片数量 */
    private static final int SHARD_COUNT = 20;
    /** 单片碎片从生成到消散的循环速度（每秒推进的归一化进度） */
    private static final float SHARD_RISE_SPEED = 0.5f;
    /** 碎片上升高度系数（× 实体高度），升到头顶以上 */
    private static final float SHARD_RISE_HEIGHT_FACTOR = 1.15f;
    /** 碎片水平外散半径系数（× 实体宽度） */
    private static final float SHARD_SPREAD_FACTOR = 0.75f;
    /** 碎片基准尺寸（格） */
    private static final float SHARD_SIZE = 0.10f;
    /** 碎片自旋速度 */
    private static final float SHARD_SPIN_SPEED = 3.2f;
    private static final float SHARD_BASE_ALPHA = 0.85f;

    // ===== 疾走涟漪（与移速修正器联动）=====
    /** 同时存在的涟漪环数 */
    private static final int RIPPLE_COUNT = 3;
    /** 涟漪扩散循环速度 */
    private static final float RIPPLE_RATE = 0.9f;
    /** 涟漪最大半径系数（× 实体宽度） */
    private static final float RIPPLE_MAX_RADIUS_FACTOR = 2.3f;
    private static final int RIPPLE_SEGMENTS = 28;
    private static final float RIPPLE_HALF_WIDTH = 0.055f;
    private static final float RIPPLE_BASE_ALPHA = 0.55f;
    /**
     * 读不到移速修正器时的回退强度。
     * <p>取中值，保证即便属性未同步（理论上不应发生）视觉也不会整个消失。</p>
     */
    private static final float SPEED_INTENSITY_FALLBACK = 0.5f;

    // ===== 躯干血气笼罩 =====
    /** 雾团数量 */
    private static final int HAZE_COUNT = 8;
    /** 雾团半径系数（× 实体宽度） */
    private static final float HAZE_SIZE_FACTOR = 0.38f;
    /** 雾团覆盖的躯干高度系数（× 实体高度） */
    private static final float HAZE_HEIGHT_FACTOR = 0.7f;
    private static final float HAZE_BASE_ALPHA = 0.34f;
    /** 雾团缓慢漂移速度 */
    private static final float HAZE_DRIFT_SPEED = 0.45f;
    /** 雾团 billboard 圆的分段数 */
    private static final int HAZE_SEGMENTS = 10;

    private IncisionRenderer() {
    }

    /**
     * 世界渲染回调：在半透明方块之后，绘制相机附近所有处于切腹状态生物的狂化视觉。
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

        MobEffect incision = CarianStylePotion.INCISION.get();

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
            // 共享列表已保证 isAlive，此处只做效果判定
            if (!hasIncision(entity, incision)) {
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

            // ⭐ v2：本实体的细节系数（距离 × 同屏拥挤度）。12 格内恒为 1.0，视觉与优化前一致
            float detail = VisualLod.detail(distSqr);
            // 登记实例，供下一帧估算拥挤度（不影响本帧绘制）
            VisualLod.countInstance();

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;

            // 爆发强度：由移速修正器实时读出（1.0=刚触发，0.0=已衰减到底）
            float burst = speedIntensity(entity);

            drawSprintRipples(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time,
                    entity.getId(), burst, detail);
            // 血气雾团是纯体积氛围层，远处完全糊成一片，低细节时整层跳过（省约 240 顶点）
            if (VisualLod.keepLayer(detail, HAZE_KEEP_THRESHOLD)) {
                drawBodyHaze(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(),
                        rightX, rightY, rightZ, upX, upY, upZ, detail);
            }
            drawRisingShards(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(),
                    burst, rightX, rightY, rightZ, upX, upY, upZ, detail);
            drawGash(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(),
                    rightX, rightY, rightZ, upX, upY, upZ, detail);
        }
    }

    /**
     * 判断实体是否处于切腹状态（双重冗余判定）。
     * <p>玩家自己进入切腹时 {@code hasEffect} 即可命中；同步集合只负责补上观察其它实体的场景。</p>
     *
     * @param entity   待判定实体
     * @param incision 切腹效果对象（可能为 null）
     * @return 处于切腹状态返回 true
     */
    private static boolean hasIncision(LivingEntity entity, @Nullable MobEffect incision) {
        if (incision != null && entity.hasEffect(incision)) {
            return true;
        }
        return ClientSyncEffectManager.shouldRenderEffect(
                IncisionSyncHandler.INCISION_SERIAL, entity.getId());
    }

    /**
     * 读取切腹的「爆发强度」：直接取 {@code MOVEMENT_SPEED} 属性上切腹修正器的实时数值。
     * <p>
     * {@link MobEffectIncision} 在施加时把移速修正器设为 +1.2（+120%），随后每 tick 递减
     * 直到下限 +0.3（+30%）。属性修正器会随实体同步到客户端，因此客户端可以直接读出当前值，
     * 换算成 0~1 的强度：刚触发为 1.0，衰减到底为 0.0。
     * </p>
     *
     * @param entity 目标实体
     * @return 爆发强度（0~1）
     */
    private static float speedIntensity(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return SPEED_INTENSITY_FALLBACK;
        }
        AttributeModifier modifier = attribute.getModifier(MobEffectIncision.ID);
        if (modifier == null) {
            return SPEED_INTENSITY_FALLBACK;
        }
        // 1.2（刚触发）→ 0.3（衰减到底）映射为 1.0 → 0.0
        double amount = modifier.getAmount();
        return clamp01((float) ((amount - 0.3) / 0.9));
    }

    // ==================== 腹部横向刀痕（核心标志）====================

    /**
     * 绘制腹部横向发光刀痕：面向相机的水平长条，厚度沿长度按正弦包络收束（两端收尖、中段最厚），
     * 中心用高亮的 {@link #INCISION_GASH}、向两端过渡到暗红并淡出为 0。整体随心跳脉动明灭。
     * <p>这是本渲染器唯一的高亮元素，也是「切腹」意象的核心符号。</p>
     * <p>
     * <b>v2：只削分段、不削存在。</b>刀痕仅 60 顶点却承担全部辨识度，因此无论细节多低都完整绘制，
     * 只把沿长度的细分从 {@link #GASH_SEGMENTS} 降到下限 {@link #GASH_SEGMENTS_MIN}
     * （轮廓略方，但「两端收尖的横痕」这一形态完全成立）。
     * </p>
     */
    private static void drawGash(BufferBuilder b, Matrix4f m,
                                 float cx, float cyFoot, float cz, float width, float height,
                                 float time, int seedId,
                                 float rightX, float rightY, float rightZ,
                                 float upX, float upY, float upZ, float detail) {
        float gashY = cyFoot + height * GASH_HEIGHT_FACTOR;
        // 心跳脉动：短促强跳 + 常驻底亮，模拟血脉搏动
        float beat = Mth.sin(time * GASH_PULSE_SPEED + seedId * 0.7f);
        float pulse = 0.72f + 0.28f * beat * beat * beat * beat; // 四次方 → 尖峰窄、底部平
        float halfLen = width * GASH_HALF_LENGTH_FACTOR * (1f + 0.05f * pulse);
        float halfThick = height * GASH_HALF_THICK_FACTOR * (0.85f + 0.3f * pulse);
        float alpha = GASH_BASE_ALPHA * pulse;

        int segments = VisualLod.scaleSegments(GASH_SEGMENTS, GASH_SEGMENTS_MIN, detail);

        float[] hot = unpack(INCISION_GASH);
        float[] blood = unpack(INCISION_BLOOD);

        float prevOffset = -halfLen;
        float prevThick = 0f;
        for (int i = 0; i <= segments; i++) {
            float u = (float) i / segments;               // 0~1 沿长度
            float offset = -halfLen + u * 2f * halfLen;   // 长度方向偏移
            float thick = halfThick * (float) Math.sin(Math.PI * u); // 两端收尖

            if (i > 0) {
                // 颜色：中段热、两端暗；alpha：中段实、两端 0
                float uPrev = (float) (i - 1) / segments;
                float[] cPrev = gashColor(uPrev, hot, blood);
                float[] cCur = gashColor(u, hot, blood);
                float aPrev = alpha * gashAlpha(uPrev);
                float aCur = alpha * gashAlpha(u);

                emitBillboardQuad(b, m, cx, gashY, cz,
                        prevOffset, prevThick, offset, thick,
                        cPrev, aPrev, cCur, aCur,
                        rightX, rightY, rightZ, upX, upY, upZ);
            }
            prevOffset = offset;
            prevThick = thick;
        }
    }

    /**
     * 刀痕颜色包络：中段取高亮 {@code hot}，向两端过渡到暗红 {@code blood}。
     *
     * @param u     沿长度的归一化位置（0~1）
     * @param hot   中段高亮色
     * @param blood 两端暗色
     * @return 该位置的颜色
     */
    private static float[] gashColor(float u, float[] hot, float[] blood) {
        // 距离中心的归一化距离（0=中心，1=端点）
        float d = Math.abs(u - 0.5f) * 2f;
        return mix(hot, blood, d * d);
    }

    /**
     * 刀痕不透明度包络：中段最实、两端渐隐为 0。
     *
     * @param u 沿长度的归一化位置（0~1）
     * @return 该位置的 alpha 系数（0~1）
     */
    private static float gashAlpha(float u) {
        return (float) Math.sin(Math.PI * u);
    }

    /**
     * 在 billboard 平面内绘制一段「带状四边形」：沿相机右向量偏移 {@code offset}、
     * 沿相机上向量正负各扩 {@code thick}，两端可分别指定颜色与 alpha。
     * <p>用于把刀痕拼成一条厚度渐变的发光横条。</p>
     */
    private static void emitBillboardQuad(BufferBuilder b, Matrix4f m,
                                          float cx, float cy, float cz,
                                          float off0, float thick0, float off1, float thick1,
                                          float[] col0, float a0, float[] col1, float a1,
                                          float rightX, float rightY, float rightZ,
                                          float upX, float upY, float upZ) {
        float x0t = cx + rightX * off0 + upX * thick0;
        float y0t = cy + rightY * off0 + upY * thick0;
        float z0t = cz + rightZ * off0 + upZ * thick0;
        float x0b = cx + rightX * off0 - upX * thick0;
        float y0b = cy + rightY * off0 - upY * thick0;
        float z0b = cz + rightZ * off0 - upZ * thick0;

        float x1t = cx + rightX * off1 + upX * thick1;
        float y1t = cy + rightY * off1 + upY * thick1;
        float z1t = cz + rightZ * off1 + upZ * thick1;
        float x1b = cx + rightX * off1 - upX * thick1;
        float y1b = cy + rightY * off1 - upY * thick1;
        float z1b = cz + rightZ * off1 - upZ * thick1;

        b.vertex(m, x0t, y0t, z0t).color(col0[0], col0[1], col0[2], a0).endVertex();
        b.vertex(m, x0b, y0b, z0b).color(col0[0], col0[1], col0[2], a0).endVertex();
        b.vertex(m, x1b, y1b, z1b).color(col1[0], col1[1], col1[2], a1).endVertex();

        b.vertex(m, x0t, y0t, z0t).color(col0[0], col0[1], col0[2], a0).endVertex();
        b.vertex(m, x1b, y1b, z1b).color(col1[0], col1[1], col1[2], a1).endVertex();
        b.vertex(m, x1t, y1t, z1t).color(col1[0], col1[1], col1[2], a1).endVertex();
    }

    // ==================== 上升血刃碎片 ====================

    /**
     * 自刀痕处向上飘散的细长菱形血刃碎片：带自旋、随高度外散、上升过程中由亮转暗并淡出。
     * <p>刻意使用<b>尖锐的细长菱形</b>而非圆形柔光点——出血渲染器的血滴已经占用了圆形语言，
     * 用菱形碎片才能在两者同时挂载时区分开，也更贴合「血刃」而非「血滴」的语义。</p>
     * <p>数量随 {@code burst}（爆发强度）增减：刚触发时最密，衰减到底时约剩一半。</p>
     * <p>
     * <b>v2：数量再乘细节系数。</b>碎片位置由 {@code seedFor(entityId, i + 100)} 决定，
     * 截断尾部时保留元素的种子与轨迹完全不变，靠近时是「逐渐多出几片碎片」而非重新洗牌。
     * </p>
     */
    private static void drawRisingShards(BufferBuilder b, Matrix4f m,
                                         float cx, float cyFoot, float cz, float width, float height,
                                         float time, int seedId, float burst,
                                         float rightX, float rightY, float rightZ,
                                         float upX, float upY, float upZ, float detail) {
        int burstCount = Math.max(1, Math.round(SHARD_COUNT * (0.5f + 0.5f * burst)));
        int count = VisualLod.scale(burstCount, detail);
        float startY = cyFoot + height * GASH_HEIGHT_FACTOR;
        float riseHeight = height * SHARD_RISE_HEIGHT_FACTOR;
        float spread = width * SHARD_SPREAD_FACTOR;

        float[] hot = unpack(INCISION_GASH);
        float[] blood = unpack(INCISION_BLOOD);
        float[] dark = unpack(INCISION_DARK);

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i + 100);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = 0.25f + 0.75f * rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.65f + 0.7f * rngFloat(s);
            s = rngNext(s);
            float spinPhase = rngFloat(s) * TAU;
            s = rngNext(s);
            float spinDir = (rngFloat(s) < 0.5f) ? -1f : 1f;

            float t = frac(time * SHARD_RISE_SPEED + phase);

            // 包络：快速淡入、后段淡出
            float env;
            if (t < 0.1f) {
                env = t / 0.1f;
            } else if (t > 0.5f) {
                env = 1f - (t - 0.5f) / 0.5f;
            } else {
                env = 1f;
            }
            if (env <= 0f) {
                continue;
            }

            float alpha = SHARD_BASE_ALPHA * env * (0.55f + 0.45f * burst);
            if (alpha <= 0.01f) {
                continue;
            }

            // 上升带加速感（t 的平方使后段拉开），水平随高度外散
            float rise = t * t * 0.4f + t * 0.6f;
            float curRad = spread * radFactor * (0.35f + 0.65f * t);
            float px = cx + (float) Math.cos(ang) * curRad;
            float pz = cz + (float) Math.sin(ang) * curRad;
            float py = startY + rise * riseHeight;

            // 颜色：初生偏亮（贴近刀痕），上升过程转暗红再转近黑红
            float[] col = (t < 0.4f) ? mix(hot, blood, t / 0.4f) : mix(blood, dark, (t - 0.4f) / 0.6f);
            float size = SHARD_SIZE * sizeRand * (1f - 0.35f * t);
            float rot = time * SHARD_SPIN_SPEED * spinDir + spinPhase;

            emitShard(b, m, px, py, pz, size, rot, col[0], col[1], col[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ);
        }
    }

    /**
     * 绘制一片面向相机的细长菱形「血刃碎片」：长轴为短轴的约 3 倍，可绕视线方向旋转。
     * 中心不透明、四个顶点渐隐为 0。
     * <p><b>轮廓固定 4 点，不参与 LOD 缩放</b>——菱形已是最简形状，
     * 碎片的削减完全通过「减少片数」实现。</p>
     *
     * @param size 短轴半长（长轴为其 3 倍）
     * @param rot  在 billboard 平面内的旋转角（弧度）
     */
    private static void emitShard(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz, float size, float rot,
                                  float r, float g, float bl, float alpha,
                                  float rightX, float rightY, float rightZ,
                                  float upX, float upY, float upZ) {
        float longAxis = size * 3f;
        // 局部坐标下的四个顶点：上下为长轴尖端，左右为短轴
        float[][] local = {
                {0f, longAxis}, {size, 0f}, {0f, -longAxis}, {-size, 0f}
        };
        float cosR = (float) Math.cos(rot), sinR = (float) Math.sin(rot);
        float[] wx = new float[4];
        float[] wy = new float[4];
        float[] wz = new float[4];
        for (int i = 0; i < 4; i++) {
            float lu = local[i][0] * cosR - local[i][1] * sinR;
            float lv = local[i][0] * sinR + local[i][1] * cosR;
            wx[i] = cx + rightX * lu + upX * lv;
            wy[i] = cy + rightY * lu + upY * lv;
            wz[i] = cz + rightZ * lu + upZ * lv;
        }
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            b.vertex(m, cx, cy, cz).color(r, g, bl, alpha).endVertex();
            b.vertex(m, wx[i], wy[i], wz[i]).color(r, g, bl, 0f).endVertex();
            b.vertex(m, wx[j], wy[j], wz[j]).color(r, g, bl, 0f).endVertex();
        }
    }

    // ==================== 疾走涟漪（与移速修正器联动）====================

    /**
     * 脚下向外扩散的扁平涟漪环。
     * <p><b>本渲染器唯一与机制直接联动的元素</b>：环数、亮度、扩散速度全部由 {@code burst}
     * （{@link #speedIntensity} 读出的移速修正器实时值）驱动。切腹刚触发时移速 +120%，
     * 涟漪密集明亮、扩散快；随着修正器衰减到 +30%，涟漪逐渐稀疏黯淡，玩家能直接从脚下看出
     * 「爆发期快过去了」，而不必去盯 buff 栏的读秒。</p>
     * <p>
     * <b>v2：环数与分段数再乘细节系数。</b>涟漪的相位是 {@code i / count} 均布的，
     * 减少 count 会改变相位间隔——但涟漪本身是「一圈接一圈往外推」的循环动画、没有固定方位，
     * 减圈只表现为「波与波之间隔得更开」，观感自然，无需按步长抽取。
     * </p>
     */
    private static void drawSprintRipples(BufferBuilder b, Matrix4f m,
                                          float cx, float cy, float cz, float width,
                                          float time, int seedId, float burst, float detail) {
        if (burst <= 0.02f) {
            return;
        }
        int burstCount = Math.max(1, Math.round(RIPPLE_COUNT * (0.4f + 0.6f * burst)));
        int count = VisualLod.scale(burstCount, detail);
        int segments = VisualLod.scaleSegments(RIPPLE_SEGMENTS, RIPPLE_SEGMENTS_MIN, detail);
        float maxRadius = width * RIPPLE_MAX_RADIUS_FACTOR * (0.6f + 0.4f * burst);
        // 扩散速度随爆发强度提升，强化「疾走」的观感
        float rate = RIPPLE_RATE * (0.7f + 0.6f * burst);

        float[] blood = unpack(INCISION_BLOOD);
        float[] dark = unpack(INCISION_DARK);

        for (int i = 0; i < count; i++) {
            float phase = (float) i / count;
            float t = frac(time * rate + phase + seedId * 0.11f);
            float radius = maxRadius * easeOutCubic(t);
            if (radius <= 0.08f) {
                continue;
            }
            // 起步快速淡入、随扩散淡出
            float alpha = RIPPLE_BASE_ALPHA * (1f - t) * smoothstep(0f, 0.12f, t) * burst;
            if (alpha <= 0.01f) {
                continue;
            }
            float[] col = mix(blood, dark, t);
            ring(b, m, cx, cy, cz, radius, segments, RIPPLE_HALF_WIDTH, col, alpha);
        }
    }

    // ==================== 躯干血气笼罩 ====================

    /**
     * 贴身的大号暗红雾团：缓慢漂移、彼此叠加，覆盖躯干中上段。
     * <p>与出血血雾的区别在于色调更暗更闷（{@link #INCISION_DARK} 参与混合）、位置更贴身，
     * 读作「被自己蒸腾的血气包裹」而不是「伤口在往外喷」。作用是给整体补足体积感。</p>
     * <p>
     * <b>v2：数量与分段数再乘细节系数；整层由调用方按 {@link #HAZE_KEEP_THRESHOLD} 决定是否绘制。</b>
     * 雾团位置由 {@code seedFor(entityId, i + 700)} 决定，截断尾部安全。
     * </p>
     */
    private static void drawBodyHaze(BufferBuilder b, Matrix4f m,
                                     float cx, float cyFoot, float cz, float width, float height,
                                     float time, int seedId,
                                     float rightX, float rightY, float rightZ,
                                     float upX, float upY, float upZ, float detail) {
        float[] blood = unpack(INCISION_BLOOD);
        float[] dark = unpack(INCISION_DARK);

        int count = VisualLod.scale(HAZE_COUNT, detail);
        int segments = VisualLod.scaleSegments(HAZE_SEGMENTS, HAZE_SEGMENTS_MIN, detail);

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i + 700);
            float baseAngle = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = rngFloat(s);
            s = rngNext(s);
            float heightFrac = rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.75f + 0.5f * rngFloat(s);
            s = rngNext(s);
            float driftPhase = rngFloat(s) * TAU;
            s = rngNext(s);
            float pulsePhase = rngFloat(s) * TAU;

            float driftAngle = baseAngle + Mth.sin(time * HAZE_DRIFT_SPEED + driftPhase) * 0.55f;
            float radius = width * 0.3f * radFactor;
            float px = cx + (float) Math.cos(driftAngle) * radius;
            float pz = cz + (float) Math.sin(driftAngle) * radius;
            float py = cyFoot + height * (0.25f + heightFrac * HAZE_HEIGHT_FACTOR);

            float pulse = 0.72f + 0.28f * Mth.sin(time * 0.85f + pulsePhase);
            float alpha = HAZE_BASE_ALPHA * pulse;
            float size = width * HAZE_SIZE_FACTOR * sizeRand;

            float[] col = mix(dark, blood, 0.35f + 0.4f * pulse);

            emitSoftMote(b, m, px, py, pz, size, col[0], col[1], col[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ, segments);
        }
    }

    // ==================== 通用几何基元 ====================

    /** 画一圈水平圆环（内外两侧为同一 alpha 的窄带）。 */
    private static void ring(BufferBuilder b, Matrix4f m,
                             float cx, float cy, float cz, float radius, int segments,
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

    /**
     * 绘制一颗面向相机的柔和圆形光点（径向渐变：中心 alpha、边缘 0）。
     *
     * @param segments 分段数。v2 起由调用方按细节系数传入，下限 {@link #HAZE_SEGMENTS_MIN}；
     *                 全细节时即 {@link #HAZE_SEGMENTS}。
     */
    private static void emitSoftMote(BufferBuilder b, Matrix4f m,
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

    // ==================== 数学 / 颜色辅助 ====================

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

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }

    /** 两个 [r,g,b] 颜色按 t 线性插值（t 自动夹取到 0~1）。 */
    private static float[] mix(float[] a, float[] b, float t) {
        float u = clamp01(t);
        return new float[]{
                a[0] + (b[0] - a[0]) * u,
                a[1] + (b[1] - a[1]) * u,
                a[2] + (b[2] - a[2]) * u
        };
    }

    /** 0xRRGGBB 拆为 [r,g,b]（0~1）。 */
    private static float[] unpack(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f
        };
    }
}
