package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.network.HemorrhageSyncHandler;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import javax.annotation.Nullable;
import java.util.List;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V2)
 *
 * 出血「飙血」客户端渲染器（纯客户端自绘）。
 * <p>
 * <b>判定采用双重冗余：</b>{@code entity.hasEffect(CarianStylePotion.HEMORRHAGE)}
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
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class HemorrhageBloodRenderer {
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final int DROP_SEGMENTS = 8;
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final int DROP_SEGMENTS_MIN = 4;
    private static final int POOL_SEGMENTS_MIN = 8;
    private static final int FLASH_SEGMENTS_MIN = 8;
    private static final float POOL_OUTER_KEEP_THRESHOLD = 0.55f;
    private static final float MIST_KEEP_THRESHOLD = 0.5f;
    private static final int BLOOD_BRIGHT = 0xE0202F;
    private static final int BLOOD_HOT = 0xFF6070;
    private static final int BLOOD_FLASH = 0xFF8090;
    private static final int BLOOD_MID = 0x8A0F18;
    private static final int BLOOD_DARK = 0x3A0508;
    private static final float[] C_BLOOD_HOT = VisualColor.constant(BLOOD_HOT);
    private static final float[] C_BLOOD_FLASH = VisualColor.constant(BLOOD_FLASH);
    private static final float[] C_BLOOD_MID = VisualColor.constant(BLOOD_MID);
    private static final float[] C_BLOOD_DARK = VisualColor.constant(BLOOD_DARK);
    private static final float[] SCRATCH_COLOR = new float[VisualColor.RGB];
    private static final int POOL_LAYERS = 2;
    private static final int POOL_SEGMENTS = 20;
    private static final float POOL_RADIUS_BASE = 1.05f;
    private static final float POOL_RADIUS_STEP = 0.5f;
    private static final float POOL_BASE_ALPHA = 0.6f;
    private static final int BASE_DROPLETS = 26;
    private static final float DROPLET_RATE = 0.6f;
    private static final float DROPLET_SIZE = 0.18f;
    private static final float LAUNCH_HEIGHT_FACTOR = 0.55f;
    private static final float LAUNCH_SPEED_BASE = 1.05f;
    private static final float LAUNCH_VY = 2.4f;
    private static final float GRAVITY = 2.4f;
    private static final float PULSE_PERIOD = 1.0f;
    private static final float PULSE_WINDOW = 0.4f;
    private static final int PULSE_EXTRA_DROPLETS = 18;
    private static final float PULSE_SIZE_MULT = 2.4f;
    private static final int PULSE_RAY_COUNT = 14;
    private static final int MIST_COUNT = 7;
    private static final float MIST_SIZE_FACTOR = 0.32f;
    private static final float MIST_BASE_ALPHA = 0.4f;
    private static final int DRIP_COUNT = 6;
    private static final float DRIP_RATE = 0.35f;
    private static final float DRIP_HALF_WIDTH = 0.03f;
    private HemorrhageBloodRenderer() {
    }
}

