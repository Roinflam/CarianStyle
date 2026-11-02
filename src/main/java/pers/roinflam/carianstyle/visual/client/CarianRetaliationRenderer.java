package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V1)
 *
 * 卡利亚式奉还「待命辉剑阵」客户端渲染器（纯客户端自绘，<b>零网络包</b>）。
 * <p>
 * 对应 {@code EnchantmentCarianRetaliation}：举盾受到魔法或远程伤害时，
 * 生成 3 道魔法辉剑自动追踪射向攻击者，每道造成 [等级]×20% 魔法伤害。
 * </p>
 * <p>
 * <b>本渲染器画的是「反击尚未触发」的待命状态</b>——玩家举起盾的那一刻，
 * 盾前浮现一面卡利亚辉石法阵，三道辉剑悬在阵上缓缓公转、剑尖朝外，
 * 等着有人把魔法或箭射过来。真正打出去之后，那三道剑由
 * {@link GlintbladesEffectRenderer} 接管（它渲染的正是本附魔生成的
 * {@code EntityGlintblades} 实体），二者<b>共用同一套辉石蓝配色</b>，
 * 视觉上是连续的：待命 → 射出，同一批剑。
 * </p>
 *
 * <h3>为什么不需要任何网络包</h3>
 * <p>
 * 生效条件只有两个，而<b>客户端两样都能自己读到</b>：
 * </p>
 * <ol>
 *     <li><b>正在举盾</b> —— {@code entity.isBlocking()}。原版的「正在使用物品」标志
 *         （{@code SharedFlags} 的 USING_ITEM 位）与 {@code useItem} 本身都随实体正常同步，
 *         因此观察者能准确知道别人有没有举盾；</li>
 *     <li><b>举着的那面盾带 carian_retaliation</b> —— 直接读
 *         {@code entity.getUseItem()} 的附魔 NBT。<b>用「正在使用的物品」而不是遍历双手，
 *         比检查主手 / 副手更准确</b>：举盾时 {@code getUseItem()} 返回的就是那面盾，
 *         不会把另一只手里恰好也带同名附魔的物品误判进来。</li>
 * </ol>
 * <p>
 * 这与 {@code DarkMoonRenderer} 判定暗月、{@code ScarletRotMistRenderer} 判定腐败女神
 * 是同一手法。相比之下，猩红腐败 / 冻伤 / 出血那些必须走 {@code ClientSyncEffectManager}
 * 的原因是它们基于 {@code MobEffect}——原版只在观察者<b>开始追踪</b>某实体的那一刻
 * 同步一次效果列表，战斗中途新加的效果收不到。附魔与举盾状态都没有这个问题。
 * </p>
 * <p>
 * 因此本渲染器<b>不占用任何效果序列号、不新增任何包、服务端零开销</b>，
 * 也不需要改动 {@code EnchantmentCarianRetaliation} 一行代码。
 * </p>
 *
 * <h3>形状语言：垂直于朝向的盾面法阵</h3>
 * <p>
 * 本模组现有的法阵<b>全部铺在地上</b>（因果律、冻结地震、癫火、光环、祈祷一击……），
 * 唯一的例外是 {@link GlintbladesEffectRenderer} 那个跟着辉剑走的小符文阵。
 * 本渲染器把法阵<b>立起来、垂直于持有者的朝向</b>——也就是「贴在盾面前方」，
 * 这个姿态在全模组独一份，远看就能读出「他举着盾、盾前有东西」。
 * </p>
 * <p>
 * <b>三个元素：</b>
 * </p>
 * <ol>
 *     <li><b>盾前法阵</b>（{@link #drawWardCircle}）——双环 + 六芒星 + 外缘刻度，
 *         缓慢自转。六芒星是卡利亚辉石魔法在本模组的既定符号
 *         （{@code AuraGroundRenderer.motifCarian} 与 {@code GlintbladesEffectRenderer}
 *         都用它），沿用可以让玩家一眼归类到「这是卡利亚的东西」；</li>
 *     <li><b>三道待命辉剑</b>（{@link #drawStandbyBlades}）——沿阵圆周 120° 均布，
 *         <b>剑尖朝外</b>（径向），缓慢公转并各自轻微起伏。数量刻意固定为 3，
 *         与附魔实际生成的辉剑数一致——玩家看到几把，反击时就飞出去几把；</li>
 *     <li><b>阵心辉光</b>（{@link #drawCore}）——一点小菱形柔光，作为视觉锚点。</li>
 * </ol>
 *
 * <h3>举盾 / 放盾的出入动画</h3>
 * <p>
 * <b>举起</b>用 {@code entity.getTicksUsingItem()} 驱动——这是原版自带的
 * 「已使用物品多少 tick」计数器，随实体同步，天然就是我们要的展开进度，
 * 不需要自己维护任何状态。前 {@link #APPEAR_TICKS} tick 内法阵由小到大展开、
 * 亮度淡入。
 * </p>
 * <p>
 * <b>放下</b>则必须自己记一笔：盾一放下 {@code isBlocking()} 立刻为 false，
 * 若不做处理法阵会「啪」地消失，很生硬。故用 {@link #FADE_STATE} 记录
 * 「最后一次仍在举盾的时刻与当时的姿态」，放下后原地收缩淡出
 * {@link #FADE_SECONDS} 秒。这套状态机与 {@code AuraGroundRenderer} 的
 * 光环淡出同源，但更轻——只在<b>本帧不再举盾、而上一帧还在举</b>的实体上创建条目，
 * 淡出结束即移除，稳态下 Map 恒为空。
 * </p>
 *
 * <h3>性能</h3>
 * <p>
 * 单个实体每帧顶点量：
 * </p>
 * <pre>
 * 外环（24 段 × 6）                    144
 * 内环（24 段 × 6）                    144
 * 六芒星（6 条线 × 6）                  36
 * 外缘刻度（12 条 × 6）                 72
 * 三道辉剑（3 把 × 2 面 × 6 × 2 层）    72
 * 剑尖光点（3 × 12）                    36
 * 阵心辉光（12）                        12
 * ─────────────────────────────────────
 * 合计                            ~516 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * 举盾是<b>持续状态</b>，团战中盾战玩家可能长时间挂着，故完整接入
 * {@link VisualLod}（含 {@link VisualLod#countInstance()}——少登记一个渲染器
 * 就会让全局 {@code crowdFactor} 被系统性高估，已接入的重量级渲染器就削减不足）。
 * </p>
 * <p>
 * <b>三个主题色全是编译期常量、演出中只有 alpha 与尺寸在变、色相从不插值</b>，
 * 故全部预解包为 {@code C_} 常量，颜色相关堆分配恒为 0，无需 {@code SCRATCH} 缓冲；
 * 几何也全部用标量内联，无任何临时数组。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class CarianRetaliationRenderer {
    private static final String RETALIATION_ID = "carian_retaliation";
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final float APPEAR_TICKS = 6.0f;
    private static final float FADE_SECONDS = 0.28f;
    private static final int RING_SEGMENTS_MIN = 14;
    private static final float TICK_KEEP_THRESHOLD = 0.5f;
    private static final float INNER_RING_KEEP_THRESHOLD = 0.45f;
    private static final float BLADE_SPARK_KEEP_THRESHOLD = 0.4f;
    private static final int GLINT_CORE = 0xEAF4FF;
    private static final int GLINT_BLUE = 0x8FD2FF;
    private static final int GLINT_DEEP = 0x3A6FC0;
    private static final float[] C_GLINT_CORE = VisualColor.constant(GLINT_CORE);
    private static final float[] C_GLINT_BLUE = VisualColor.constant(GLINT_BLUE);
    private static final float[] C_GLINT_DEEP = VisualColor.constant(GLINT_DEEP);
    private static final double WARD_FORWARD = 0.72;
    private static final double WARD_HEIGHT = 1.15;
    private static final double WARD_RADIUS = 0.75;
    private static final double WARD_INNER_RATIO = 0.58;
    private static final double WARD_STAR_RATIO = 0.82;
    private static final int WARD_SEGMENTS = 24;
    private static final double WARD_RING_HALF = 0.022;
    private static final float WARD_ROT_SPEED = 0.75f;
    private static final float WARD_BREATH_SPEED = 1.6f;
    private static final float WARD_RING_ALPHA = 0.8f;
    private static final float WARD_INNER_ALPHA = 0.55f;
    private static final float WARD_STAR_ALPHA = 0.7f;
    private static final int WARD_TICK_COUNT = 12;
    private static final double WARD_TICK_LENGTH = 0.12;
    private static final float WARD_TICK_ALPHA = 0.45f;
    private static final int BLADE_COUNT = 3;
    private static final double BLADE_ORBIT_RATIO = 0.62;
    private static final double BLADE_LENGTH = 0.42;
    private static final double BLADE_HALF_WIDTH = 0.035;
    private static final float BLADE_ORBIT_SPEED = 1.15f;
    private static final float BLADE_BOB_SPEED = 2.3f;
    private static final double BLADE_BOB_AMOUNT = 0.05;
    private static final float BLADE_ALPHA = 0.9f;
    private static final float CORE_SIZE = 0.075f;
    private static final float CORE_ALPHA = 0.75f;
    private static final Map<Integer, FadeState> FADE_STATE = new HashMap<>();

    private static final class FadeState {
        float startTime;
        double x;
        double y;
        double z;
        float yawRad;
        float appear;
    }
    private CarianRetaliationRenderer() {
    }
}

