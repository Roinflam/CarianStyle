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
 * @author RoinFlam
 * @version 3.1
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

        DamageOverTimeManager.applyLinear(
                victim,
                damagePerTick,
                DOT_DURATION,
                DOT_DELAY,
                evt.getSource(),
                true
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
