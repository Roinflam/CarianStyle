package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * PORT-STUB(V1)
 *
 * 硬箭「12 格参考距离」指示圈（纯客户端，<b>只有持有者自己看得见</b>）。
 * <p>
 * {@code EnchantmentHardArrow} 的机制是「弓箭伤害 +[等级]×80%，但 12 格内有生物时
 * 自身受到的伤害 +[等级]×80%」。12 格远超肉眼可靠估距的范围，
 * 玩家没法判断「那只僵尸到底进没进圈」。本渲染器就是给这个距离画一把尺子。
 * </p>
 *
 * <h3>它<b>只是</b>一把尺子</h3>
 * <p>
 * <b>本类不做任何实体扫描，也不判断范围内有没有东西。</b>它只画一个固定 12 格的圈，
 * 剩下的交给玩家自己看。这是刻意的设计取舍：
 * </p>
 * <ul>
 *     <li><b>零运行时开销。</b>没有 AABB 查询、没有结果列表分配、没有周期性扫描任务。
 *         唯一的周期性工作是每 {@value #LEVEL_CHECK_INTERVAL_TICKS} tick 读一次主手物品的附魔等级；</li>
 *     <li><b>不会说谎。</b>{@code EnchantmentHardArrow} 走的是
 *         {@code EntityUtil.getNearbyEntities}，其内部是 AABB 盒形还是球形距离尚未确认。
 *         若本类自己扫一遍再染色，一旦形状口径不一致，圈的边缘就会出现
 *         「明明是绿的却已经中招」——那比不提示更糟；</li>
 *     <li><b>不广播弱点。</b>只对 {@code mc.player} 绘制。硬箭的 12 格是持有者自己的风险，
 *         画给别人看等于告诉对手「站进这里能让我多吃 80% 伤害」。</li>
 * </ul>
 *
 * <h3>v3.0：把「看不清」修掉</h3>
 * <p>
 * v2.0 为了不干扰视野把亮度压到 0.2、用了 {@code 0x8FB4C0} 这种低饱和灰青、
 * 环带只有 0.16 格宽。结果是<b>压过头了</b>——在草地、沙地、雪地上几乎看不出来，
 * 一把看不见的尺子等于没有尺子。
 * </p>
 * <p>
 * 这一版换了个思路：<b>不靠提亮，靠形状</b>。
 * </p>
 * <ul>
 *     <li><b>实线改虚线</b>（{@value #DASH_COUNT} 段）。虚线是人眼识别「测量标记」最强的信号——
 *         地图、图纸、瞄准具全都用它。同样的亮度下，断续的线比连续的线<b>更容易被认出是人造标记</b>，
 *         而不是被当成地面纹理的一部分；</li>
 *     <li><b>亮度 0.2 → {@value #ALPHA}，环带 0.16 → {@value #RING_HALF_WIDTH} 格。</b>
 *         虚线本身占的像素比实线少，所以即使单段更亮更粗，整体的视觉重量反而没怎么增加；</li>
 *     <li><b>配色换成明确的青色</b> {@code 0x5FD8E8}。原来那个灰青在灰白色地面上是隐形的；
 *         青色在 Minecraft 的地形色里几乎不存在（草绿、土棕、石灰、沙黄都不含它），
 *         因此不管站在哪种地面上都能分辨；</li>
 *     <li><b>四个方位加长刻度</b>。除了 {@value #TICK_COUNT} 道常规向内刻度，
 *         正前后左右四个方向的刻度加长到 {@value #CARDINAL_TICK_LENGTH} 格，
 *         让玩家一眼看出圈的朝向与自己的关系。</li>
 * </ul>
 * <p>
 * <b>依然没有任何脉动、呼吸或颜色变化。</b>这是常驻元素，动起来就会变成视野污染——
 * 提高可见度的正确做法是改形状和配色，不是让它闪。
 * </p>
 *
 * <h3>v3.1：进出过渡</h3>
 * <p>
 * v3.0 的圈是<b>硬切</b>的：换上弓的那一帧整个圈直接出现，收起时又直接消失。
 * 一个 12 格宽的图形突然出现在脚下，观感上就是「闪了一下」，
 * 而且会让人误以为刚才发生了什么事件。
 * </p>
 * <p>
 * 现在加了 {@value #TRANSITION_TICKS} tick 的展开 / 收起动画，
 * <b>三个量同时被驱动</b>：透明度淡入、半径从 {@value #TRANSITION_START_SCALE}
 * 倍展开到满尺寸、虚线整体转过 {@value #TRANSITION_SPIN_SECTORS} 个扇区归位。
 * </p>
 * <p>
 * 三者缺一不可：只做透明度会读成「一张贴图在变淡」；
 * 加上半径变化才有「展开」的感觉；而那一下轻微的旋转是最点睛的——
 * 它让整个动作像<b>一个仪器在对焦归位</b>，而不是一个技能特效在生效。
 * 这正是本类想给人的印象：它是工具，不是特效。
 * </p>
 * <p>
 * <b>注意这与「无脉动」并不矛盾。</b>过渡只在<b>状态切换的那 0.4 秒</b>发生，
 * 展开完成后就是完全静止的——常驻期间依然一动不动。
 * </p>
 *
 * <h3>为什么不接进既有的光环系统</h3>
 * <p>
 * 项目里已有 {@code AuraDisplayRegistry} + {@code AuraScanner} + {@code AuraGroundRenderer}
 * 这套完整链路，但它的设计前提是「谁身上有这个附魔，谁脚下就画一个圈，<b>所有人都看得见</b>」——
 * 这对魔法之境、圣域这类<b>影响他人</b>的光环是对的，对本需求则正好相反。
 * </p>
 * <p>
 * 要在那套系统里加「只对自己可见」，得同时改条目结构、扫描逻辑与约一千五百行的绘制分发。
 * 而本类完全自包含：<b>只读 {@code mc.player} 的主手物品，零网络包、零服务端配合</b>，
 * 那三个文件一行都不用改。
 * </p>
 *
 * <h3>口径核对结果</h3>
 * <p>已按 {@code EnchantmentHardArrow} 源码逐条核对：</p>
 * <ul>
 *     <li><b>范围 12 格</b>——与 {@link #RANGE} 一致</li>
 *     <li><b>注册名 {@code hard_arrow}</b>——取自 {@code @AutoRegisterEnchantment}</li>
 *     <li><b>只认主手</b>——两个代价入口（{@code onLivingKnockBack} 与
 *         {@code onHurtAsVictimHighest}）都只读 {@code MAIN_HAND}，
 *         注册的 {@code slots} 也只有 {@code MAINHAND}。
 *         故 {@link #currentLevel} 同样只查主手：把弓换到副手代价根本不触发，
 *         此时还画着圈就是在提示一个不存在的风险</li>
 * </ul>
 *
 * @author RoinFlam
 * @version 3.1
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class HardArrowRangeRenderer {
    private static final String HARD_ARROW_ID = "hard_arrow";
    private static final double RANGE = 12.0;
    private static final int LEVEL_CHECK_INTERVAL_TICKS = 5;
    private static final int TRANSITION_TICKS = 8;
    private static final float TRANSITION_START_SCALE = 0.82f;
    private static final float TRANSITION_SPIN_SECTORS = 1.5f;
    private static final float Y_OFFSET = 0.03f;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final int DASH_COUNT = 32;
    private static final float DASH_FILL = 0.62f;
    private static final int DASH_SEGMENTS = 3;
    private static final float RING_HALF_WIDTH = 0.24f;
    private static final int TICK_COUNT = 12;
    private static final float TICK_LENGTH = 0.9f;
    private static final float CARDINAL_TICK_LENGTH = 1.9f;
    private static final float TICK_HALF_WIDTH = 0.1f;
    private static final float ALPHA = 0.42f;
    private static final int COLOR_REFERENCE = 0x5FD8E8;
    private static final float[] C_REFERENCE = VisualColor.constant(COLOR_REFERENCE);
    private HardArrowRangeRenderer() {
    }
}

