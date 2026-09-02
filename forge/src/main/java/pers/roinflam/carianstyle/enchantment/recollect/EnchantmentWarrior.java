package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

/**
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
 * @author RoinFlam
 * @version 3.2
 */
@AutoRegisterEnchantment(
        id = "warrior",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentWarrior extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;
    /** 持续伤害时长（tick） */
    private static final int DOT_DURATION = 60;
    /** 持续伤害初始延迟（tick） */
    private static final int DOT_DELAY = 5;

    /**
     * 本附魔在 {@link DamageOverTimeManager} 中的来源标签（v3.3 新增）。
     * <p>
     * {@code DamageOverTimeManager} 是<b>七个附魔共用的池子</b>（注定死亡、死亡之刃、
     * 黑焰刃、癫火、战士、沙布里里嚎叫、空癫火）。HUD 要显示「战士流血剩余」，
     * 就必须能把本附魔的条目从池子里挑出来——否则一个同时中了癫火的玩家，
     * 那一行会把癫火的伤害也算进来，数字看着有但意义是错的。
     * </p>
     * <p>
     * <b>public 是刻意的：</b>{@code CarianStyleCombatStateDisplay} 直接引用这个常量，
     * 而不是自己硬编码一份同样的字符串——两处字符串迟早会因为一次改名而不同步。
     * </p>
     */
    public static final String DOT_TAG = "warrior";

    public EnchantmentWarrior() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 攻击者视角：伤害×1.25
     * <p>v3.1：怪物作为攻击者时，由通用开关 allowMobTriggerEnchantments 控制</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage_attack(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity attacker)) return;

        // ⭐ v3.1：怪物附魔触发开关（攻击者视角，非死亡触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;

        Enchantment warrior = EnchantmentRegistry.getEnchantmentByClass(EnchantmentWarrior.class);
        if (warrior == null) return;

        int level = EnchantmentHelper.getItemEnchantmentLevel(warrior, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);

        if (level > 0) {
            evt.setAmount(evt.getAmount() * 1.25f);
        }
    }

    /**
     * 受击者视角：即时伤害降为50%，剩余50%持续60tick扣血
     * <p>
     * v3.0优化：SynchronizationTask(5, 1) → DamageOverTimeManager.applyLinear
     * v3.1：怪物作为受击者时，由通用开关 allowMobTriggerEnchantments 控制
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage_hurter(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) return;

        LivingEntity victim = evt.getEntity();

        // ⭐ v3.1：怪物附魔触发开关（受击者视角，非死亡触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack heldItem = victim.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;

        Enchantment warrior = EnchantmentRegistry.getEnchantmentByClass(EnchantmentWarrior.class);
        if (warrior == null) return;

        int level = EnchantmentHelper.getItemEnchantmentLevel(warrior, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;

        // 即时伤害降为50%
        evt.setAmount(evt.getAmount() * 0.5f);

        // 剩余50%分60tick持续扣血
        float damagePerTick = evt.getAmount() / DOT_DURATION;

        // ⭐ v3.2：改用带来源标签的重载，使 HUD 能查到「本附魔造成的」剩余流血。
        // 行为与不带标签的重载完全一致，只是多记一个字符串引用
        DamageOverTimeManager.applyLinear(
                victim,
                damagePerTick,
                DOT_DURATION,
                DOT_DELAY,
                evt.getSource(),
                true,
                DOT_TAG
        );
    }

    /**
     * 击杀时回复25%已损失生命值
     * <p>v3.1：怪物作为击杀者时，由通用开关 allowMobTriggerEnchantments 控制</p>
     */
    @SubscribeEvent
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity killer)) return;

        // ⭐ v3.1：怪物附魔触发开关（击杀者视角，击杀奖励不属于濒死触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(killer, false)) return;

        ItemStack heldItem = killer.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;

        Enchantment warrior = EnchantmentRegistry.getEnchantmentByClass(EnchantmentWarrior.class);
        if (warrior == null) return;

        int level = EnchantmentHelper.getItemEnchantmentLevel(warrior, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);

        if (level > 0) {
            killer.heal((killer.getMaxHealth() - killer.getHealth()) * 0.25f);
        }
    }

    @Override
    public int getMinCost(int l) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
