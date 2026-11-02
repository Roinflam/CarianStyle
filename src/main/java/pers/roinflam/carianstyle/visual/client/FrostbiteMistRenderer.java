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
import pers.roinflam.carianstyle.network.FrostbiteSyncHandler;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import javax.annotation.Nullable;
import java.util.List;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V2)
 *
 * 冻伤「冰霜」客户端渲染器（纯客户端自绘）。
 * <p>
 * <b>判定采用双重冗余：</b>{@code entity.hasEffect(CarianStylePotion.FROSTBITE)}
 * （覆盖玩家自己、以及在观察者开始追踪前就已带冻伤的实体）<b>或</b>
 * {@code ClientSyncEffectManager.shouldRenderEffect(FrostbiteSyncHandler.FROSTBITE_SERIAL, id)}
 * （覆盖战斗中途才被施加冻伤的怪物，见 {@link FrostbiteSyncHandler} 类注释）。
 * </p>
 * <p>
 * <b>三个元素分工：</b>
 * <ol>
 *     <li><b>冰晶星</b>（{@link #drawIceCrystalStar}）——唯一的标志性主视觉：八角星 + 内
 *         六边形，任何角度都能一眼认出「冰」；带周期性脉冲（短暂增亮增大 + 中心闪光）；</li>
 *     <li><b>霜地</b>（{@link #drawFrostedGround}）——单层渐变圆盘 + 从冰晶星八个尖角方向
 *         延伸出去、与星形同步旋转的裂纹；</li>
 *     <li><b>冰雾 + 冷蒸汽</b>（{@link #drawFrostFog} / {@link #drawColdVapor}）——
 *         前者是半径接近实体宽度一半的大号柔光雾块，缓慢漂移、彼此叠加，是「冒寒气」的主要来源；
 *         后者是贴身持续冒出的细蒸汽丝，作为补充细节层。</li>
 * </ol>
 * </p>
 * <p>
 * 渲染管线与 {@code ScarletRotMistRenderer} 同款：{@link RenderLevelStageEvent} 的
 * {@code AFTER_TRANSLUCENT_BLOCKS} 阶段，GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理，
 * 实体列表取自 {@link SharedEntityQuery} 的每帧共享查询，{@code POSITION_COLOR} 纯顶点绘制。
 * </p>
 *
 * <h3>v7（顶点量，近距离视觉零变化）：接入 {@link VisualLod}</h3>
 * <p>
 * 单个冻伤实体每帧的顶点量粗算：
 * </p>
 * <pre>
 * 霜地圆盘（24 段 × 3）                    72
 * 地面裂纹（8 条 × 6）                      48
 * 冰晶星（八角星 48 + 六边形 36 + 闪光 54） 138
 * 冰雾（9 团 × 10 段 × 3）                 270
 * 冷蒸汽（18 丝 × 10 段 × 3）              540
 * ─────────────────────────────────────────
 * 合计                              ~1068 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * 冻伤是本模组<b>最容易群体挂载</b>的效果之一——星律附魔在夜间「造成或受到物理伤害」就叠冻伤、
 * 亚杜拉的月光剑每次攻击对目标周围一圈敌人施加冻伤，一次挥砍就能让十几个实体同时带上。
 * </p>
 * <p>
 * 现按 {@link VisualLod#detail} 缩放：{@link VisualLod#FULL_DETAIL_RANGE} 格内系数为 1.0，
 * <b>与优化前逐像素一致</b>；40 格外单实体降至约 220 顶点。
 * </p>
 * <p>
 * <b>削减策略：</b>
 * </p>
 * <ul>
 *     <li><b>冰晶星完全不削</b>——八角星 + 内六边形共 84 顶点，是「这是冰不是别的」的唯一依据，
 *         而且是<b>顶点性价比最高</b>的元素（84 顶点换来全部辨识度）。只把脉冲峰值那一下的
 *         中心闪光圆盘按细节缩分段（那是锦上添花的柔光，不影响星形轮廓）；</li>
 *     <li><b>冰雾与蒸汽按种子截断尾部</b>——位置由 {@code seedFor(entityId, i)} 决定，
 *         保留元素的漂移轨迹完全不变；</li>
 *     <li><b>蒸汽丝整层可跳过</b>——它本就是冰雾的「补充细节层」（见类内 v5 说明），
 *         远处两者会糊成一片，低于 {@link #VAPOR_KEEP_THRESHOLD} 时只留大雾块
 *         （省 540 顶点，占总量一半）；</li>
 *     <li><b>裂纹按步长抽取</b>——裂纹角度是 {@code rot + i × (TAU / 8)} 均布的，
 *         且刻意与冰晶星的八个尖角对齐，截断会让一半尖角失去延伸出去的裂纹、
 *         破坏「星形向外裂开」的一体感，故按步长抽取保持对称。</li>
 * </ul>
 *
 * <h3>v8（堆分配，视觉逐位一致）：颜色数组零分配化</h3>
 * <p>
 * v7 解决了顶点量，但本渲染器还剩一处纯浪费：旧实现的 {@code unpack(color)}
 * <b>每次调用都 {@code new float[3]}</b>。逐处清点：
 * </p>
 * <pre>
 * 冷蒸汽（18 丝 × 1 次 lerpRgb→unpack）      18
 * 冰雾（9 团 × 1 次 lerpRgb→unpack）           9
 * 地面裂纹（8 条，line() 内部各 unpack 一次）  8
 * 冰晶星（white / blue 各解包一次）             2
 * 霜地圆盘（lerpRgb→unpack）                    1
 * ──────────────────────────────────────────
 * 合计                     ~38 次 new float[3] / 实体 / 帧
 * </pre>
 * <p>
 * 单看不多，但<b>冻伤是全模组最容易「一大片实体同时带上」的效果</b>——
 * 亚杜拉的月光剑一次挥砍就给目标周围一圈敌人各加一层，星律在夜间更是受击就叠。
 * 15 个冻伤实体 × 60fps ≈ <b>每秒 3.4 万次</b>朝生夕死的小数组分配，
 * 而这恰恰发生在群战、客户端最需要帧率的时候。
 * </p>
 * <p>
 * 现改为两条路径（工具见 {@link VisualColor}）：
 * </p>
 * <ol>
 *     <li><b>三个主题色类加载时预解包一次</b>（{@code C_} 前缀常量），此后永久复用；</li>
 *     <li><b>动态插值色写入复用缓冲</b>——{@link #SCRATCH}。</li>
 * </ol>
 * <p>
 * <b>为什么本渲染器只需要一个缓冲：</b>雾团、蒸汽、霜地圆盘的颜色都是
 * 「算出来紧接着就被一次绘制调用消费掉」，任一时刻只有一个动态色存活。
 * 需要<b>两个动态色同时存活</b>的场景（螺旋、刀痕那种两端异色的渐变线段）才必须用双缓冲——
 * 本渲染器的 {@link #lineF} 虽然接受两端 alpha，但<b>两端共用同一个颜色数组</b>，不受影响。
 * </p>
 * <p>
 * <b>顺带清理：</b>原先的 {@code line(..., int col, ...)} 包装方法只有一处调用（地面裂纹），
 * 且传的恒是常量 {@link #ICE_WHITE}，其存在的唯一意义就是内部替调用方 {@code unpack} 一次。
 * 现直接调 {@link #lineF} 并传只读常量，该包装方法予以删除。
 * {@code lerpRgb} 与 {@code unpack} 同样已无调用点，一并删除，
 * 避免后续有人误用而重新引入分配。
 * </p>
 * <p>
 * <b>视觉逐位一致：</b>{@link VisualColor#lerpInto} 保留了旧 {@code lerpRgb} 在 0~255 整数域
 * 插值并 {@link Math#round} 取整的行为，{@link VisualColor#constant} 与旧 {@code unpack}
 * 是同一个 {@code /255f} 公式，因此输出的每个颜色分量与 v7 完全相同——
 * 不是「肉眼看不出」而是「数值相等」。
 * </p>
 *
 * @author RoinFlam
 * @version 8
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class FrostbiteMistRenderer {
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final int MOTE_SEGMENTS = 10;
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final int MOTE_SEGMENTS_MIN = 4;
    private static final int GROUND_SEGMENTS_MIN = 8;
    private static final int FLASH_SEGMENTS_MIN = 6;
    private static final float VAPOR_KEEP_THRESHOLD = 0.5f;
    private static final int ICE_WHITE = 0xEAF6FF;
    private static final int ICE_BLUE = 0x8FD4FF;
    private static final int ICE_DEEP = 0x2E5C82;
    private static final float[] C_ICE_WHITE = VisualColor.constant(ICE_WHITE);
    private static final float[] C_ICE_BLUE = VisualColor.constant(ICE_BLUE);
    private static final float[] SCRATCH = new float[VisualColor.RGB];
    private static final float ICE_STAR_ROT_SPEED = 0.15f;
    private static final float ICE_STAR_RADIUS_FACTOR = 0.6f;
    private static final float ICE_STAR_ALPHA = 0.75f;
    private static final float PULSE_PERIOD = 3.0f;
    private static final float PULSE_ACTIVE_RATIO = 0.25f;
    private static final int FLASH_SEGMENTS = 18;
    private static final int GROUND_SEGMENTS = 24;
    private static final float GROUND_RADIUS_FACTOR = 1.7f;
    private static final float GROUND_ALPHA = 0.42f;
    private static final int CRACK_COUNT = 8;
    private static final float CRACK_HALF_WIDTH = 0.03f;
    private static final float CRACK_ALPHA = 0.55f;
    private static final int VAPOR_COUNT = 18;
    private static final float VAPOR_RISE_SPEED = 0.55f;
    private static final float VAPOR_RADIUS_FACTOR = 0.4f;
    private static final float VAPOR_RISE_DIST_FACTOR = 0.35f;
    private static final float VAPOR_BASE_ALPHA = 0.8f;
    private static final int FOG_COUNT = 9;
    private static final float FOG_SIZE_FACTOR = 0.42f;
    private static final float FOG_HEIGHT_FACTOR = 0.55f;
    private static final float FOG_BASE_ALPHA = 0.4f;
    private static final float FOG_DRIFT_SPEED = 0.3f;
    private FrostbiteMistRenderer() {
    }
}

