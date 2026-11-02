package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E2)
 *
 * 战士附魔
 * <p>
 * 攻击者视角：伤害×1.25
 * 受击者视角：即时伤害降为50%，剩余50%在60tick内持续扣血
 * 击杀时：回复25%已损失生命值
 * </p>
 * <p>
 * 性能优化 v3.0：受击持续伤害改用 DamageOverTimeManager
 * 修复保留 v2.1：getUsedItemHand -> InteractionHand.MAIN_HAND
 * v3.1新增：三个独立监听器入口接入怪物附魔触发开关
 * </p>
 *
 * <h3>视觉反馈由 HUD 承担，不做世界特效</h3>
 * <p>
 * 本附魔三段效果没有一段适合做瞬时演出：攻击者视角的 ×1.25 是每次攻击都生效的常驻数值，
 * 加视觉会变成每挥一次就闪一下的噪音；受击者视角的「50% 伤害转 60 tick 持续流失」
 * 本身就是个持续状态；击杀回血虽是离散事件，但击杀那一刻屏幕上本来就够热闹了。
 * </p>
 * <p>
 * 真正缺失的信息是<b>「我还要掉多少血才停」</b>——玩家会看到自己在没人打的时候还在掉血。
 * 这个由 {@code CarianStyleCombatStateDisplay} 在 HUD 上显示剩余流血量，
 * 数据取自下面 {@link #DOT_TAG} 标记的 DoT 条目。
 * </p>
 *
 * <h3>v3.1 补充：给持续伤害打上来源标签</h3>
 * <p>
 * 注册 DoT 时改用 {@code applyLinear} 的标签重载并传入 {@link #DOT_TAG}，
 * 让 {@code CarianStyleCombatStateDisplay} 的「流血剩余」HUD 能把本附魔的条目
 * 从七个附魔共用的 DoT 池子里挑出来。<b>行为完全不变。</b>
 * </p>
 *
 * <h3>结算时机：哪些读数里看不到加成</h3>
 * <p>
 * 攻击者视角的倍率挂在 {@link LivingDamageEvent} 的 LOW 优先级，这时护甲、保护附魔、抗性
 * 和伤害吸收（黄心）都已经结算完。下面几种现象都是这个时机带来的现有行为，不是没生效：
 * </p>
 * <ul>
 *     <li>{@code LivingHurtEvent} 的所有优先级、{@code LivingDamageEvent} 的 HIGHEST ~ NORMAL
 *     读到的都是乘倍率之前的数值，只在这些阶段取伤害的显示或统计看不到加成，要以目标实际掉的血为准。
 *     钻石剑满蓄力打无甲僵尸 6.888 → 8.61，打全套钻石甲僵尸 1.4 → 1.75，都正好 ×1.25。</li>
 *     <li>倍率只乘在穿过吸收值之后的那部分上：吸收值吃得下整次伤害时这一下没有加成，
 *     吃不下时只有溢出到生命值的部分 ×1.25（目标有 4 点吸收时 6.888 → 7.61，不是 8.61）。</li>
 *     <li>只认直接实体是生物、附魔在它主手上的伤害：箭矢、投掷物，以及以召唤物 / 斩击实体为直接实体的伤害
 *     （拔刀剑的幻影剑之类）不吃加成，副手拿战士剑也不生效。</li>
 *     <li>数值与等级无关（最高 1 级），2、3 级和 1 级完全一样。</li>
 *     <li>双方都持有战士时，攻击者 ×1.25 先算、受击者 ×0.5 后算：即时掉血只有普通一击的 0.625 倍，
 *     另一半在 3 秒内流失完，合计仍是 ×1.25。</li>
 *     <li>×1.25 不一定跨过「几刀砍死」的断点：下界合金剑打 20 血僵尸，两刀后还剩 0.32 血，照样要三刀。</li>
 * </ul>
 *
 * @author RoinFlam
 * @version 3.2
 */
@AutoRegisterEnchantment(
        id = "warrior",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 35,
        levelMultiplier = 0
)
@Deprecated
public class EnchantmentWarrior extends EnchantmentBase {
    private static final String VALUE_ID = "warrior";
    private static final EnchantmentValues.Handle DOT_DURATION = EnchantmentValues.define(VALUE_ID, "dot_duration",
                    60, 1, 1200);
    private static final EnchantmentValues.Handle DOT_DELAY = EnchantmentValues.define(VALUE_ID, "dot_delay",
                    5, 0, 200);
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    1.25D, 0.0D, 10.0D);
    private static final EnchantmentValues.Handle REDUCTION_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "reduction_multiplier",
                    0.5D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle KILL_HEAL_RATIO = EnchantmentValues.define(VALUE_ID, "kill_heal_ratio",
                    0.25D, 0.0D, 2.0D);
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public static final String DOT_TAG = "warrior";
    public EnchantmentWarrior() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
}

