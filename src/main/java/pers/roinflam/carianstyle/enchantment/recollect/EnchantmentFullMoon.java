package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentHealingByFire;
import pers.roinflam.carianstyle.enchantment.EnchantmentShelterOfFire;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 满月附魔
 *
 * 死亡时触发：取消死亡，恢复微量血量，持续回血（有DarkMoon时加倍）
 * 满月状态下减伤50%
 * 夜晚治疗量+25%
 */
@AutoRegisterEnchantment(
        id = "full_moon",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        conflictsWith = {
                EnchantmentHealingByFire.class,
                EnchantmentShelterOfFire.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentFullMoon extends EnchantmentBase {

    private static final String FULL_MOON_STATE_KEY = "full_moon_state";
    private static final String FULL_MOON_COOLDOWN_KEY = "full_moon_cooldown";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentFullMoon() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    /**
     * 死亡时触发：取消死亡，持续回血
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();
        UUID uuid = holder.getUniqueID();

        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(fullMoon, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        if (!EnchantmentDataManager.isOnCooldown(FULL_MOON_COOLDOWN_KEY, uuid)) {
            if (!holder.isDead) {
                evt.setCanceled(true);
                holder.setHealth(holder.getMaxHealth() * 0.0075f);

                EnchantmentDataManager.setCooldown(FULL_MOON_STATE_KEY, uuid, 400);

                // 检查是否有DarkMoon附魔
                boolean hasDarkMoon = false;
                Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
                if (darkMoon != null && !holder.getHeldItem(holder.getActiveHand()).isEmpty()) {
                    if (EnchantmentHelper.getEnchantmentLevel(
                            darkMoon,
                            holder.getHeldItem(holder.getActiveHand())) > 0) {
                        hasDarkMoon = true;
                    }
                }

                int duration = hasDarkMoon ? 400 : 200;
                new SynchronizationTask(1, 1) {
                    private int tick = 1;

                    @Override
                    public void run() {
                        if (++tick > duration || !holder.isEntityAlive()) {
                            this.cancel();
                            EnchantmentDataManager.clearCooldown(FULL_MOON_STATE_KEY, uuid);
                            return;
                        }
                        holder.heal(holder.getMaxHealth() / 200);
                    }
                }.start();
            }
        }

        int cooldownTime = holder.world.isDaytime() ? 3600 : 1800;
        EnchantmentDataManager.setCooldown(FULL_MOON_COOLDOWN_KEY, uuid, cooldownTime);
    }

    /**
     * 满月状态下减伤50%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();
        UUID uuid = holder.getUniqueID();

        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(fullMoon, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        if (EnchantmentDataManager.isOnCooldown(FULL_MOON_STATE_KEY, uuid)) {
            evt.setAmount(evt.getAmount() * 0.5f);
        }
    }

    /**
     * 夜晚治疗量+25%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote || evt.getEntity().world.isDaytime()) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(fullMoon, armor);
            }
        }

        if (totalLevel > 0) {
            evt.setAmount(evt.getAmount() * 1.25f);
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        // 与死亡类附魔冲突（通过包名判断）
        if (isDeadEnchantment(ench)) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    /**
     * 判断是否是死亡类附魔
     */
    private boolean isDeadEnchantment(Enchantment ench) {
        // 死亡类附魔包括：FullMoon, TimeReversal 等
        return ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentTimeReversal.class));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}