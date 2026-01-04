package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * 重力附魔
 *
 * 攻击时激活重力场，给敌人施加重力效果
 * 激活期间周围12格内其他生物持续受到轻微重力效果
 */
@AutoRegisterEnchantment(
        id = "gravitas",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentGravitas extends EnchantmentBase {

    private static final String GRAVITAS_ACTIVE_KEY = "gravitas_active";

    public EnchantmentGravitas() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时激活重力场并给敌人施加重力效果
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();

        if (attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment gravitas = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGravitas.class);
        if (gravitas == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                gravitas,
                attacker.getHeldItem(attacker.getActiveHand()));

        if (level <= 0) {
            return;
        }

        // 激活重力场状态
        int activeDuration = level * 4 * 20;
        EnchantmentDataManager.setCooldown(GRAVITAS_ACTIVE_KEY, attacker.getUniqueID(), activeDuration);

        // 给敌人施加重力效果
        int potionDuration = level * 2 * 20;
        int potionLevel = 10 + level * 4 - 1;
        victim.addPotionEffect(new PotionEffect(CarianStylePotion.GRAVITAS, potionDuration, potionLevel));
    }

    /**
     * 死亡时清理状态
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }
        EnchantmentDataManager.clearCooldown(GRAVITAS_ACTIVE_KEY, evt.getEntityLiving().getUniqueID());
    }

    /**
     * 激活状态时，周围生物持续受到轻微重力效果
     */
    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();
        if (!holder.isEntityAlive()) {
            return;
        }

        UUID uuid = holder.getUniqueID();
        if (!EnchantmentDataManager.isOnCooldown(GRAVITAS_ACTIVE_KEY, uuid)) {
            return;
        }

        // 周围12格内其他生物受到轻微重力效果
        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                holder,
                12,
                entity -> !entity.equals(holder)
        );

        for (EntityLivingBase entity : nearbyEntities) {
            entity.addPotionEffect(new PotionEffect(CarianStylePotion.GRAVITAS, 2, 9));
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        // 与击退冲突
        if (ench == Enchantments.KNOCKBACK) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }
}