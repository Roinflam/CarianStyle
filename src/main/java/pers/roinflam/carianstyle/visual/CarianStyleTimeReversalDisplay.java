package pers.roinflam.carianstyle.visual;

import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;
import pers.roinflam.carianstyle.utils.Reference;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V2)
 *
 * 时间逆转「储存伤害」的 HUD 叠层注册（双端通用）。
 *
 * <h3>它显示什么，为什么值得单独占一行</h3>
 * <p>
 * {@code CarianStyleStackDisplays} 里已有一项时间逆转的<b>冷却倒计时</b>（serialId 18），
 * 但那是「下次什么时候能再用」；本项显示的是逆转<b>正在进行时</b>已经攒了多少伤害。
 * </p>
 * <p>
 * 二者信息完全不同，且都很关键：逆转的收益 = 储存值 × 25% 的治疗 + 全额反弹给攻击者，
 * 也就是说「站着多挨几下」是<b>正收益</b>——这与玩家的全部本能相反。
 * 没有这个数字，玩家会本能地在无敌期间逃跑，白白浪费掉附魔最值钱的部分。
 * 有了它，玩家能看着数字往上跳，主动往人堆里站。
 * </p>
 * <p>
 * 两项会在逆转的 5 秒里<b>同时显示</b>（冷却在触发瞬间就开始走），
 * 因此刻意给了不同的名称与配色：冷却那项是原附魔名 + 金褐，
 * 本项是「回溯储存」+ 苍白金，不会看混。
 * </p>
 *
 * <h3>为什么上限填 0（不画进度条）</h3>
 * <p>
 * 储存伤害<b>没有任何上限</b>——机制上你挨多少就存多少。
 * 而 HUD 的进度条填充比例是 {@code count / max}，硬编一个假上限只会骗人：
 * 条满了玩家会以为「存够了、可以跑了」，实际上继续挨打仍在涨。
 * </p>
 * <p>
 * 故按 {@code StackDisplayRegistry} 既定的约定填 {@code max = 0} —— HUD 只显示数字、
 * 不画进度条，与米凯拉之刃、连击这两项同款处理。
 * </p>
 *
 * <h3>v1.1：门控口径改为 armorMaxLevel</h3>
 * <p>
 * 上一版这里写的是 {@code ctx.armorHas(...)}。改成
 * {@code ctx.armorMaxLevel(...) > 0} 有两个原因：
 * </p>
 * <ul>
 *     <li>与新增的 {@link CarianStyleFireStyleDisplay} 保持同一口径，
 *         两处护甲类门控读起来一致；</li>
 *     <li>{@code armorMaxLevel} 是本项目在设计阶段就确认存在的方法，
 *         而 {@code armorHas} 是否为 {@code EquipmentEnchantContext} 的公开 API
 *         我没有当面核对过。二者语义完全等价（都是「护甲上有没有这个附魔」），
 *         换成确定存在的那个可以排除一处编译风险。</li>
 * </ul>
 * <p>
 * <b>行为完全不变</b>：等级恒为正整数，{@code > 0} 与「有」是同一件事。
 * 顺带把 null 判断显式提到前面，不再依赖上下文方法对 {@code null} 的容忍度。
 * </p>
 *
 * <h3>为什么单开一个类而不是并进 CarianStyleStackDisplays</h3>
 * <p>
 * 那个类有七百余行、集中登记了 22 个显示项，为加一项而整体重写的风险
 * （抄错一个 key、漏掉一个常量）远大于收益。本类用
 * {@link FMLCommonSetupEvent} 自行注册，与那边互不干扰——
 * {@code StackDisplayRegistry.register} 只是往一个列表里追加，
 * 两处注册的先后顺序不影响任何行为，serialId 不冲突即可。
 * </p>
 * <p>
 * <b>以后要统一管理的话</b>，把 {@link #register()} 里那段
 * {@code StackDisplayRegistry.register(...)} 原样剪进
 * {@code CarianStyleStackDisplays.init()}、再把序列号常量搬过去，然后删掉本文件即可，
 * 其余代码一行都不用动。
 * </p>
 *
 * @author RoinFlam
 * @version 1.1
 */
@Deprecated
public final class CarianStyleTimeReversalDisplay {
    public static final int TIME_REVERSAL_STORED = 23;
    private static final String REVERSAL_DAMAGE_KEY = "time_reversal_damage";
    private static final String STORED_NAME_KEY = "carianstyle.hud.time_reversal_stored";
    private static final int STORED_COLOR = 0xF0E0B0;
    private CarianStyleTimeReversalDisplay() {
    }
}

