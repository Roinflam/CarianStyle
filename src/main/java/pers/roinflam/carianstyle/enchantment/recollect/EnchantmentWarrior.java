package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

import javax.annotation.Nonnull;

/**
 * 战士附魔
 *
 * 攻击：增伤25%
 * 受击：伤害减半，剩余伤害分60tick扣血
 * 击杀：治疗损失血量×25%
 * <p>
 * 以前受击后的持续扣血每次各开一个任务直接改血量；现在交给持续伤害管理器统一结算。
 * 武器以前按「最近一次使用物品的那只手」取，现在一律看主手；怪物持有时按怪物触发附魔的开关处理。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "warrior",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentWarrior extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /** 受击后持续扣血的时长（tick） */
    private static final int DOT_DURATION = 60;
    /** 持续扣血的起始延迟（tick） */
    private static final int DOT_DELAY = 5;

    public EnchantmentWarrior() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage_attack(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        if (shouldBlockMobTrigger(attacker, false)) {
            return;
        }

        if (attacker.getHeldItemMainhand().isEmpty()) {
            return;
        }

        Enchantment warrior = EnchantmentRegistry.getEnchantmentByClass(EnchantmentWarrior.class);
        if (warrior == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                warrior,
                attacker.getHeldItemMainhand());

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level > 0) {
            evt.setAmount(evt.getAmount() * 1.25f);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage_hurter(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        if (shouldBlockMobTrigger(victim, false)) {
            return;
        }

        if (victim.getHeldItemMainhand().isEmpty()) {
            return;
        }

        Enchantment warrior = EnchantmentRegistry.getEnchantmentByClass(EnchantmentWarrior.class);
        if (warrior == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                warrior,
                victim.getHeldItemMainhand());

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        evt.setAmount(evt.getAmount() * 0.5f);

        // 剩下的一半分 60 tick 扣完；走持续伤害管理器，致死时直接处决，普拉顿桑克斯的回溯期间暂停
        float damagePerTick = evt.getAmount() / DOT_DURATION;
        DamageOverTimeManager.applyLinear(victim, damagePerTick, DOT_DURATION, DOT_DELAY, evt.getSource(), true);
    }

    @SubscribeEvent
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase killer = (EntityLivingBase) evt.getSource().getImmediateSource();

        if (shouldBlockMobTrigger(killer, false)) {
            return;
        }

        if (killer.getHeldItemMainhand().isEmpty()) {
            return;
        }

        Enchantment warrior = EnchantmentRegistry.getEnchantmentByClass(EnchantmentWarrior.class);
        if (warrior == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                warrior,
                killer.getHeldItemMainhand());

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level > 0) {
            killer.heal((killer.getMaxHealth() - killer.getHealth()) * 0.25f);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}