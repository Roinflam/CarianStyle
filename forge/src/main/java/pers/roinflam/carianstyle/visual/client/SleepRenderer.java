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
import pers.roinflam.carianstyle.network.SleepSyncHandler;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 睡眠「沉眠」客户端渲染器（纯客户端自绘）。
 * <p>
 * 对应 {@code MobEffectSleep}：目标无法移动 / 跳跃 / 攻击，生物无法将其设为目标，
 * 首次受到生物伤害时「觉醒」并承受 ×2 + 等级×25% 的伤害。由催眠烟雾
 * （{@code EnchantmentHypnoticSmoke}）与托莉娜箭（{@code EnchantmentHypnoticArrow}）施加。
 * </p>
 * <p>
 * <b>判定采用双重冗余：</b>{@code hasEffect(SLEEP)} <b>或</b>
 * {@code ClientSyncEffectManager.shouldRenderEffect(SLEEP_SERIAL, id)}。
 * 对睡眠而言后者才是主力——睡眠几乎总是施加给正在交战的敌人，而这类目标必然已被观察者追踪，
 * 原版此时不再下发效果变化（详见 {@link SleepSyncHandler} 类注释）。
 * </p>
 * <p>
 * <b>受众是旁观者而非被睡者。</b>{@code MobEffectSleep} 会给沉睡实体持续施加原版失明，
 * 被睡的玩家自己其实什么都看不见；这套视觉真正服务的是<b>施法者与旁观者</b>——
 * 他们需要一眼确认「这个目标睡着了、现在打它有觉醒加成」。因此所有元素都设计为
 * <b>从外部远看就能辨识</b>：头顶的螺旋在任何角度都清晰，不依赖贴脸观察细节。
 * </p>
 * <p>
 * <b>三个元素（全场唯一的「慢速」演出，这是刻意的辨识手段）：</b>
 * <ol>
 *     <li><b>头顶催眠螺旋</b>（{@link #drawHypnoticSpiral}）——标志性主视觉。头顶上方水平悬浮的
 *         阿基米德螺线，极缓慢地整体旋转。螺旋是「催眠」这一概念最不可能被误读的符号，
 *         且本模组此前没有任何演出使用过螺线形状，同屏叠加时绝不会与别的效果混淆；</li>
 *     <li><b>托莉娜白花瓣</b>（{@link #drawPetals}）——极缓慢飘落并左右摇曳的乳白花瓣
 *         （billboard 六边形轮廓，非正圆），呼应睡眠女神托莉娜的白花意象；</li>
 *     <li><b>沉眠雾盘</b>（{@link #drawSlumberMist}）——脚下极淡的蓝灰雾盘，以近乎察觉不到的
 *         速度呼吸，为整体压住重量、避免只剩两个飘浮元素显得轻飘。</li>
 * </ol>
 * <b>所有动效速度都刻意压到其它特效的几分之一</b>——冻伤在闪烁、出血在迸溅、癫火在颤动，
 * 而睡眠几乎是静止的。这个「慢」本身就是最强的辨识特征。
 * </p>
 * <p>
 * 渲染管线沿用本模组统一方案：GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理，
 * 实体列表取自 {@link SharedEntityQuery} 的每帧共享查询，{@code POSITION_COLOR} 纯顶点绘制。
 * </p>
 *
 * @author FlameForge
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class SleepRenderer {

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final long START_MILLIS = System.currentTimeMillis();

    // ===== 配色（0xRRGGBB）=====
    /** 花瓣乳白：托莉娜白花的主色 */
    private static final int SLEEP_PETAL = 0xF2EDE0;
    /** 催眠淡蓝灰：螺旋与雾盘的主色 */
    private static final int SLEEP_MIST = 0xBFC8DE;
    /** 沉眠暗蓝灰：雾盘外缘与螺旋末端的暗部 */
    private static final int SLEEP_DEEP = 0x6E7695;

    // ===== 头顶催眠螺旋 =====
    /** 螺旋悬浮高度系数（× 实体高度）：略高于头顶 */
    private static final float SPIRAL_HEIGHT_FACTOR = 1.35f;
    /** 螺旋圈数 */
    private static final float SPIRAL_TURNS = 2.25f;
    /** 螺旋最大半径系数（× 实体宽度） */
    private static final float SPIRAL_RADIUS_FACTOR = 0.85f;
    /** 螺旋细分段数（越大线条越平滑） */
    private static final int SPIRAL_SEGMENTS = 48;
    /** 螺旋整体旋转速度（弧度/秒）——刻意极慢 */
    private static final float SPIRAL_ROT_SPEED = 0.55f;
    /** 螺旋线半宽（格） */
    private static final float SPIRAL_HALF_WIDTH = 0.035f;
    private static final float SPIRAL_BASE_ALPHA = 0.8f;

    // ===== 托莉娜白花瓣 =====
    /** 同时存在的花瓣数量 */
    private static final int PETAL_COUNT = 10;
    /** 单片花瓣从生成到落地的循环速度（每秒推进的归一化进度）——极慢 */
    private static final float PETAL_FALL_SPEED = 0.085f;
    /** 花瓣起始高度系数（× 实体高度） */
    private static final float PETAL_START_HEIGHT_FACTOR = 1.6f;
    /** 花瓣水平分布半径系数（× 实体宽度） */
    private static final float PETAL_SPREAD_FACTOR = 0.8f;
    /** 花瓣基准尺寸（格） */
    private static final float PETAL_SIZE = 0.075f;
    /** 花瓣自旋速度——同样很慢 */
    private static final float PETAL_SPIN_SPEED = 0.7f;
    private static final float PETAL_BASE_ALPHA = 0.85f;

    // ===== 沉眠雾盘 =====
    private static final int MIST_SEGMENTS = 24;
    private static final float MIST_RADIUS_FACTOR = 1.35f;
    private static final float MIST_BASE_ALPHA = 0.28f;
    /** 雾盘呼吸速度——近乎察觉不到 */
    private static final float MIST_BREATH_SPEED = 0.35f;

    private SleepRenderer() {
    }

    /**
     * 世界渲染回调：在半透明方块之后，绘制相机附近所有沉睡生物的睡眠视觉。
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
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

        MobEffect sleep = CarianStylePotion.SLEEP.get();

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
            if (!isAsleep(entity, sleep)) {
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

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;

            drawSlumberMist(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId());
            drawPetals(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(),
                    rightX, rightY, rightZ, upX, upY, upZ);
            drawHypnoticSpiral(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId());
        }
    }

    /**
     * 判断实体是否处于睡眠状态（双重冗余判定）。
     *
     * @param entity 待判定实体
     * @param sleep  睡眠效果对象（可能为 null）
     * @return 沉睡中返回 true
     */
    private static boolean isAsleep(LivingEntity entity, @Nullable MobEffect sleep) {
        if (sleep != null && entity.hasEffect(sleep)) {
            return true;
        }
        return ClientSyncEffectManager.shouldRenderEffect(SleepSyncHandler.SLEEP_SERIAL, entity.getId());
    }

    // ==================== 头顶催眠螺旋（核心标志）====================

    /**
     * 头顶悬浮的水平催眠螺旋：一条阿基米德螺线（{@code r = k·θ}），沿 {@link #SPIRAL_TURNS} 圈
     * 由内向外展开，整体极缓慢旋转；线宽与亮度由内向外递减，末端渐隐。
     * <p>
     * <b>为什么用螺旋：</b>「螺旋 = 催眠」是跨文化都成立的直觉符号，远看一眼就能读出语义；
     * 而且本模组此前的十余种演出里没有任何一个使用螺线形状（法阵是多边形/星形、
     * 光环是同心圆、刀光是弧带），因此同屏叠加时不存在混淆风险。
     * </p>
     * <p>
     * 高度取实体头顶再往上一点，且<b>随呼吸极缓慢上下浮动</b>，避免像贴图一样死板。
     * </p>
     */
    private static void drawHypnoticSpiral(BufferBuilder b, Matrix4f m,
                                           float cx, float cyFoot, float cz,
                                           float width, float height,
                                           float time, int seedId) {
        // 极缓慢的整体旋转 + 各实体错相
        float rot = time * SPIRAL_ROT_SPEED + seedId * 0.7f;
        // 极缓慢的上下浮动
        float bob = Mth.sin(time * 0.5f + seedId * 0.4f) * 0.09f;
        float spiralY = cyFoot + height * SPIRAL_HEIGHT_FACTOR + bob;
        float maxRadius = width * SPIRAL_RADIUS_FACTOR;

        float[] mist = unpack(SLEEP_MIST);
        float[] petal = unpack(SLEEP_PETAL);

        float totalAngle = TAU * SPIRAL_TURNS;
        float prevX = cx;
        float prevZ = cz;

        for (int i = 1; i <= SPIRAL_SEGMENTS; i++) {
            float u = (float) i / SPIRAL_SEGMENTS;   // 0=中心, 1=外端
            float theta = totalAngle * u + rot;
            float r = maxRadius * u;
            float x = cx + r * (float) Math.cos(theta);
            float z = cz + r * (float) Math.sin(theta);

            // 由内向外：线变细、变淡、由乳白转蓝灰
            float hw = SPIRAL_HALF_WIDTH * (1f - u * 0.45f);
            float alpha = SPIRAL_BASE_ALPHA * (1f - u * 0.75f);
            float[] col = mix(petal, mist, u);

            float uPrev = (float) (i - 1) / SPIRAL_SEGMENTS;
            float alphaPrev = SPIRAL_BASE_ALPHA * (1f - uPrev * 0.75f);
            float[] colPrev = mix(petal, mist, uPrev);

            lineGradient(b, m, prevX, prevZ, x, z, spiralY, hw, colPrev, alphaPrev, col, alpha);

            prevX = x;
            prevZ = z;
        }

        // 螺旋中心的一点柔光，作为视觉锚点
        spark(b, m, cx, cz, spiralY, width * 0.12f + 0.05f, petal, 0.65f);
    }

    // ==================== 托莉娜白花瓣 ====================

    /**
     * 极缓慢飘落的乳白花瓣：自实体上方生成，缓缓下沉并左右摇曳，接近地面时淡出。
     * <p>
     * 花瓣用 billboard 六边形轮廓近似（而非正圆），带缓慢自旋，呼应睡眠女神托莉娜的白花意象。
     * 下落速度只有本模组其它上升 / 飘落类元素的几分之一——「慢」是睡眠的核心语言。
     * </p>
     */
    private static void drawPetals(BufferBuilder b, Matrix4f m,
                                   float cx, float cyFoot, float cz,
                                   float width, float height,
                                   float time, int seedId,
                                   float rightX, float rightY, float rightZ,
                                   float upX, float upY, float upZ) {
        float startHeight = height * PETAL_START_HEIGHT_FACTOR;
        float spread = width * PETAL_SPREAD_FACTOR;

        float[] petal = unpack(SLEEP_PETAL);
        float[] mist = unpack(SLEEP_MIST);

        for (int i = 0; i < PETAL_COUNT; i++) {
            long s = seedFor(seedId, i + 200);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = 0.25f + 0.75f * rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.7f + 0.6f * rngFloat(s);
            s = rngNext(s);
            float swayPhase = rngFloat(s) * TAU;
            s = rngNext(s);
            float spinPhase = rngFloat(s) * TAU;

            float t = frac(time * PETAL_FALL_SPEED + phase); // 0=最高处 1=落地

            // 包络：起落两端淡入淡出
            float env;
            if (t < 0.12f) {
                env = t / 0.12f;
            } else if (t > 0.8f) {
                env = 1f - (t - 0.8f) / 0.2f;
            } else {
                env = 1f;
            }
            if (env <= 0f) {
                continue;
            }

            // 缓慢的左右摇曳（落叶感）
            float sway = Mth.sin(time * 0.45f + swayPhase) * 0.22f;
            float curRad = spread * radFactor;
            float px = cx + (float) Math.cos(ang) * curRad + sway;
            float pz = cz + (float) Math.sin(ang) * curRad;
            float py = cyFoot + startHeight * (1f - t) + Y_OFFSET;

            float alpha = PETAL_BASE_ALPHA * env;
            if (alpha <= 0.01f) {
                continue;
            }

            float[] col = mix(petal, mist, t * 0.5f);
            float size = PETAL_SIZE * sizeRand;
            float rot = time * PETAL_SPIN_SPEED + spinPhase;

            emitPetal(b, m, px, py, pz, size, rot, col[0], col[1], col[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ);
        }
    }

    // ==================== 沉眠雾盘 ====================

    /**
     * 脚下的淡蓝灰雾盘：中心稍实、边缘渐隐，以近乎察觉不到的速度呼吸。
     * <p>作用是给整体压住重量——只有螺旋与花瓣两个悬浮元素会显得轻飘，
     * 加一层贴地的雾才有「沉下去了」的分量感。</p>
     */
    private static void drawSlumberMist(BufferBuilder b, Matrix4f m,
                                        float cx, float cy, float cz, float width,
                                        float time, int seedId) {
        float breath = 0.9f + 0.1f * Mth.sin(time * MIST_BREATH_SPEED + seedId * 0.6f);
        float radius = width * MIST_RADIUS_FACTOR * breath;
        int col = lerpRgb(SLEEP_DEEP, SLEEP_MIST, 0.5f + 0.5f * Mth.sin(time * 0.3f + seedId));
        float[] c = unpack(col);
        drawDisc(b, m, cx, cy, cz, radius, MIST_SEGMENTS, c[0], c[1], c[2], MIST_BASE_ALPHA * breath);
    }

    // ==================== 几何基元 ====================

    /**
     * 绘制一片面向相机的花瓣：billboard 平面内用 6 个轮廓点近似出圆润的椭圆花瓣形，
     * 支持绕视线方向旋转。中心不透明、边缘渐隐为 0。
     *
     * @param size 花瓣半尺寸
     * @param rot  在 billboard 平面内的旋转角（弧度）
     */
    private static void emitPetal(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz, float size, float rot,
                                  float r, float g, float bl, float alpha,
                                  float rightX, float rightY, float rightZ,
                                  float upX, float upY, float upZ) {
        // 圆润的椭圆轮廓（横向略宽），比正圆更像花瓣、比尖菱形更柔和
        float[][] local = {
                {0f, size * 1.05f}, {size * 0.85f, size * 0.5f}, {size * 0.85f, -size * 0.5f},
                {0f, -size * 1.05f}, {-size * 0.85f, -size * 0.5f}, {-size * 0.85f, size * 0.5f}
        };
        float cosR = (float) Math.cos(rot), sinR = (float) Math.sin(rot);
        float[] wx = new float[6];
        float[] wy = new float[6];
        float[] wz = new float[6];
        for (int i = 0; i < 6; i++) {
            float lu = local[i][0] * cosR - local[i][1] * sinR;
            float lv = local[i][0] * sinR + local[i][1] * cosR;
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

    /**
     * 带宽度的水平线段，<b>两端颜色与 alpha 均可分别指定</b>（螺旋需要沿弧长做色彩梯度）。
     *
     * @param hw 线半宽（格）
     */
    private static void lineGradient(BufferBuilder b, Matrix4f m,
                                     float x1, float z1, float x2, float z2, float y,
                                     float hw, float[] col1, float a1, float[] col2, float a2) {
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

        b.vertex(m, ax1, y, az1).color(col1[0], col1[1], col1[2], a1).endVertex();
        b.vertex(m, bx1, y, bz1).color(col2[0], col2[1], col2[2], a2).endVertex();
        b.vertex(m, bx2, y, bz2).color(col2[0], col2[1], col2[2], a2).endVertex();

        b.vertex(m, ax1, y, az1).color(col1[0], col1[1], col1[2], a1).endVertex();
        b.vertex(m, bx2, y, bz2).color(col2[0], col2[1], col2[2], a2).endVertex();
        b.vertex(m, ax2, y, az2).color(col1[0], col1[1], col1[2], a1).endVertex();
    }

    /**
     * 小菱形光点（柔光），中心最亮、四角渐隐。水平面。
     */
    private static void spark(BufferBuilder b, Matrix4f m, float px, float pz, float y,
                              float size, float[] col, float alpha) {
        float r = col[0], g = col[1], bl = col[2];
        float[][] pts = {{px, pz - size}, {px + size, pz}, {px, pz + size}, {px - size, pz}};
        for (int i = 0; i < 4; i++) {
            float[] a = pts[i];
            float[] c = pts[(i + 1) % 4];
            b.vertex(m, px, y, pz).color(r, g, bl, alpha).endVertex();
            b.vertex(m, a[0], y, a[1]).color(r, g, bl, 0f).endVertex();
            b.vertex(m, c[0], y, c[1]).color(r, g, bl, 0f).endVertex();
        }
    }

    /**
     * 水平径向渐变圆盘（中心 alpha、边缘 0）。
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
