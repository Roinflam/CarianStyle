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
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 战士附魔
 *
 * 攻击：增伤25%
 * 受击：伤害减半，剩余伤害分60tick扣血
 * 击杀：治疗损失血量×25%
 */
@AutoRegisterEnchantment(
        id = "warrior",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentWarrior extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;

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

        if (attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment warrior = EnchantmentRegistry.getEnchantmentByClass(EnchantmentWarrior.class);
        if (warrior == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                warrior,
                attacker.getHeldItem(attacker.getActiveHand()));

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

        if (victim.getHeldItem(victim.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment warrior = EnchantmentRegistry.getEnchantmentByClass(EnchantmentWarrior.class);
        if (warrior == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                warrior,
                victim.getHeldItem(victim.getActiveHand()));

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        evt.setAmount(evt.getAmount() * 0.5f);

        float damagePerTick = evt.getAmount() / 60;

        new SynchronizationTask(5, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (++tick > 60 || !victim.isEntityAlive()) {
                    this.cancel();
                    return;
                }

                if (victim.getHealth() - damagePerTick * 2 > 0) {
                    victim.setHealth(victim.getHealth() - damagePerTick);
                } else {
                    EntityLivingUtil.kill(victim, evt.getSource());
                    this.cancel();
                }
            }
        }.start();
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

        if (killer.getHeldItem(killer.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment warrior = EnchantmentRegistry.getEnchantmentByClass(EnchantmentWarrior.class);
        if (warrior == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                warrior,
                killer.getHeldItem(killer.getActiveHand()));

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