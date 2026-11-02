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
import java.util.List;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V3)
 *
 * 狄蒂卡之祸「提线人偶」客户端渲染器（纯客户端自绘，<b>零网络包</b>）。
 * <p>
 * 对应 {@code EnchantmentDaedicarWoe}：受到的伤害 ×3，受伤无敌时间 -50%。
 * 这是全模组最重的诅咒——伤害三倍还砍掉一半无敌帧，
 * 意味着连续挨打时几乎没有喘息，实战中往往是「一套连招直接送走」。
 * </p>
 *
 * <h3>为什么是人偶</h3>
 * <p>
 * 狄蒂卡在原作里就是<b>被封在人偶里的活祭品</b>，这个诅咒的护符外形正是一具小人偶。
 * 而本模组现有的十余种演出里<b>没有任何一个用过人形轮廓</b>——
 * 法阵是多边形 / 星形、光环是同心圆、丝线归灾祸、尖刺归噩兆、
 * 螺旋归睡眠、月轮归暗月、根须归黄金树。
 * 一具吊在头顶、缺胳膊少腿、随风摇晃的破人偶，在任何角度、任何距离下都不可能被认成别的东西。
 * </p>
 * <p>
 * <b>抽搐是必须的那一笔。</b>只是摇晃的话读作「挂了个饰品」；
 * 每隔几秒猛地抽一下，才读得出「这东西是活的，而且它在替你受罪」——
 * 正好对应「受伤无敌时间 -50%」那半条机制：你被打的时候恢复不过来。
 * </p>
 * <p>
 * <b>配色</b>用惨白骨色 + 暗紫提线，与灾祸的暗血红完全岔开——
 * 两个诅咒同时挂在身上时（这在整合包里并非不可能）必须一眼分得清。
 * </p>
 *
 * <h3>为什么不需要任何网络包</h3>
 * <p>
 * 生效条件只有一个：<b>身上装着带狄蒂卡之祸的护甲</b>。附魔存在于 {@code ItemStack} 的
 * NBT 里、随装备槽位正常同步给全部观察者，客户端直接读得到。
 * 与 {@link CalamityRenderer} 判定灾祸、{@link DarkMoonRenderer} 判定暗月是同一手法。
 * </p>
 * <p>
 * 因此本渲染器<b>不占用任何效果序列号、不新增任何包、服务端零开销</b>，
 * 也不需要改动 {@code EnchantmentDaedicarWoe} 一行代码。
 * </p>
 *
 * <h3>装备槽口径</h3>
 * <p>
 * {@link #hasWoe} 目前只扫描<b>4 件护甲槽</b>——与 {@link CalamityRenderer} 同一判断依据。
 * 若该附魔实际不是护甲附魔，把 {@code getArmorSlots()} 换成遍历
 * {@code EquipmentSlots.SCAN} 即可，其余代码一行不用动；
 * 判定错了的后果是「装上了却不显示」，不会崩溃、也不影响任何机制。
 * </p>
 *
 * <h3>人偶画在 billboard 平面内</h3>
 * <p>
 * 人形轮廓必须<b>始终正对观察者</b>才读得出是人形——侧着看会退化成几根竖线。
 * 故整具人偶活在由相机右向量 {@code right} 与上向量 {@code up} 张成的平面里，
 * 用平面二维坐标 {@code (u, v)} 描述各个关节，再由
 * {@code P = center + right·u + up·v} 映射到世界。
 * 这样描述比逐点算三维方便得多，也不易出错。
 * </p>
 * <p>
 * <b>摇晃与抽搐都作用在这个平面内</b>：绕吊点做二维旋转即可，
 * 见 {@link #rotU} / {@link #rotV}。
 * </p>
 *
 * <h3>顶点量与 LOD</h3>
 * <pre>
 * 人偶骨架（躯干 + 双臂 + 双腿 + 2 道缝合线 = 7 条线 × 6）   84
 * 头部（12 段圆盘 × 3）                                       36
 * 吊线 + 双股牵连线（3 条 × 6）                               18
 * 脚下惨白圈（20 段 × 3）                                     60
 * ───────────────────────────────────────────────────────
 * 合计                                        ~198 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * <b>这是全模组最轻的世界演出</b>，比噩兆的 270 还低。
 * 因此接入 {@link VisualLod} 的<b>首要目的是
 * {@link VisualLod#countInstance()}</b>——拥挤度是全局共享的，
 * 只要还有渲染器不登记，{@code crowdFactor} 就会被系统性高估，
 * 已接入的重量级渲染器（黄金树祝福 2000、重力力场圈 2112）就削减不足。
 * 它自己能省的那点顶点反而是次要的。
 * </p>
 * <p>
 * <b>人偶骨架完全不削。</b>七条线加一个头共 120 顶点，却承担全部辨识度；
 * 少画任何一条（比如砍掉一条腿）都会让「人形」这个读法崩掉。
 * 只有头部圆盘的分段数与脚下圈的分段数参与缩放。
 * </p>
 * <p>
 * 三个配色全是编译期常量、演出中只有 alpha 与姿态在变、色相从不插值，
 * 故全部预解包为 {@code C_} 常量，颜色相关堆分配恒为 0，无需复用缓冲。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class DaedicarWoeRenderer {
    private static final String WOE_ID = "daedicar_woe";
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final int WOE_PALE = 0xE8E0CE;
    private static final int WOE_THREAD = 0x8E74B4;
    private static final int WOE_DEEP = 0x2E2242;
    private static final float[] C_PALE = VisualColor.constant(WOE_PALE);
    private static final float[] C_THREAD = VisualColor.constant(WOE_THREAD);
    private static final float[] C_DEEP = VisualColor.constant(WOE_DEEP);
    private static final float DOLL_HEIGHT_FACTOR = 1.5f;
    private static final float DOLL_SIZE_FACTOR = 0.52f;
    private static final float DOLL_LIMB_HALF = 0.022f;
    private static final int DOLL_HEAD_SEGMENTS = 12;
    private static final int DOLL_HEAD_SEGMENTS_MIN = 6;
    private static final float DOLL_ALPHA = 0.88f;
    private static final float SWING_SPEED = 0.9f;
    private static final float SWING_AMOUNT = 0.17f;
    private static final float TWITCH_PERIOD = 3.1f;
    private static final float TWITCH_WINDOW = 0.09f;
    private static final float TWITCH_SHAKE_SPEED = 38f;
    private static final float TWITCH_AMOUNT = 0.22f;
    private static final float HANG_LINE_LENGTH = 1.1f;
    private static final float THREAD_HALF = 0.012f;
    private static final float THREAD_KEEP_THRESHOLD = 0.45f;
    private static final int RING_SEGMENTS = 20;
    private static final int RING_SEGMENTS_MIN = 8;
    private static final float RING_RADIUS_FACTOR = 1.0f;
    private static final float RING_HALF_WIDTH = 0.035f;
    private static final float RING_ALPHA = 0.38f;
    private static final float RING_BREATH_SPEED = 0.7f;
    private DaedicarWoeRenderer() {
    }
}

