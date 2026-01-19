package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentHealingByFire;
import pers.roinflam.carianstyle.enchantment.EnchantmentShelterOfFire;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import java.util.UUID;

/**
 * 满月附魔
 * <p>
 * 死亡时触发：取消死亡，恢复微量血量，持续回血（有DarkMoon时加倍）
 * 满月状态下减伤50%
 * 夜晚治疗量+25%
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "full_moon",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
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
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getSource().isCreativePlayer()) {
            return;
        }

        LivingEntity holder = evt.getEntity();
        UUID uuid = holder.getUUID();

        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(fullMoon, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        if (!EnchantmentDataManager.isOnCooldown(FULL_MOON_COOLDOWN_KEY, uuid)) {
            if (!holder.isDeadOrDying()) {
                evt.setCanceled(true);
                holder.setHealth(holder.getMaxHealth() * 0.0075f);

                EnchantmentDataManager.setCooldown(FULL_MOON_STATE_KEY, uuid, 400);

                // 检查是否有DarkMoon附魔
                boolean hasDarkMoon = false;
                Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
                ItemStack heldItem = holder.getItemInHand(holder.getUsedItemHand());
                if (darkMoon != null && !heldItem.isEmpty()) {
                    if (EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem) > 0) {
                        hasDarkMoon = true;
                    }
                }

                int duration = hasDarkMoon ? 400 : 200;
                new SynchronizationTask(1, 1) {
                    private int tick = 1;

                    @Override
                    public void run() {
                        if (++tick > duration || !holder.isAlive()) {
                            this.cancel();
                            EnchantmentDataManager.clearCooldown(FULL_MOON_STATE_KEY, uuid);
                            return;
                        }
                        holder.heal(holder.getMaxHealth() / 200);
                    }
                }.start();
            }
        }

        int cooldownTime = holder.level().isDay() ? 3600 : 1800;
        EnchantmentDataManager.setCooldown(FULL_MOON_COOLDOWN_KEY, uuid, cooldownTime);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getSource().isCreativePlayer()) {
            return;
        }

        LivingEntity holder = evt.getEntity();
        UUID uuid = holder.getUUID();

        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(fullMoon, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        if (EnchantmentDataManager.isOnCooldown(FULL_MOON_STATE_KEY, uuid)) {
            evt.setAmount(evt.getAmount() * 0.5f);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getEntity().level().isDay()) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(fullMoon, armor);
            }
        }

        if (totalLevel > 0) {
            evt.setAmount(evt.getAmount() * 1.25f);
        }
    }

    @Override
    protected boolean checkCompatibility(Enchantment ench) {
        if (isDeadEnchantment(ench)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    private boolean isDeadEnchantment(Enchantment ench) {
        return ench instanceof EnchantmentTimeReversal;
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}