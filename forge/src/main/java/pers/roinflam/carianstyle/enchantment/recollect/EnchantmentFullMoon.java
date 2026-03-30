package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.InteractionHand;
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
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentHealingByFire;
import pers.roinflam.carianstyle.enchantment.EnchantmentShelterOfFire;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import java.util.UUID;

/** 满月附魔 - 修复: DarkMoon检查getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1 */
@AutoRegisterEnchantment(id = "full_moon", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.ARMOR_CHEST, slots = {EquipmentSlot.CHEST}, conflictsWith = {EnchantmentHealingByFire.class, EnchantmentShelterOfFire.class})
@Mod.EventBusSubscriber
public class EnchantmentFullMoon extends EnchantmentBase {
    private static final String FULL_MOON_STATE_KEY = "full_moon_state";
    private static final String FULL_MOON_COOLDOWN_KEY = "full_moon_cooldown";
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentFullMoon() { super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST}); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getSource().isCreativePlayer()) return;
        LivingEntity holder = evt.getEntity();
        UUID uuid = holder.getUUID();
        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) return;
        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getItemEnchantmentLevel(fullMoon, armor);
        }
        if (ConfigLoader.levelLimit) totalLevel = Math.min(totalLevel, 10);
        if (totalLevel <= 0) return;
        if (!EnchantmentDataManager.isOnCooldown(FULL_MOON_COOLDOWN_KEY, uuid)) {
            evt.setCanceled(true);
            holder.setHealth(holder.getMaxHealth() * 0.0075f);
            EnchantmentDataManager.setCooldown(FULL_MOON_STATE_KEY, uuid, 400);
            // 修复：使用主手检查DarkMoon
            boolean hasDarkMoon = false;
            Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
            ItemStack heldItem = holder.getItemInHand(InteractionHand.MAIN_HAND);
            if (darkMoon != null && !heldItem.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem) > 0) {
                hasDarkMoon = true;
            }
            int duration = hasDarkMoon ? 400 : 200;
            new SynchronizationTask(1, 1) {
                private int tick = 1;
                @Override public void run() {
                    if (++tick > duration || !holder.isAlive()) { this.cancel(); EnchantmentDataManager.clearCooldown(FULL_MOON_STATE_KEY, uuid); return; }
                    holder.heal(holder.getMaxHealth() / 200);
                }
            }.start();
        }
        EnchantmentDataManager.setCooldown(FULL_MOON_COOLDOWN_KEY, uuid, holder.level().isDay() ? 3600 : 1800);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getSource().isCreativePlayer()) return;
        LivingEntity holder = evt.getEntity();
        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) return;
        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getItemEnchantmentLevel(fullMoon, armor);
        }
        if (totalLevel > 0 && EnchantmentDataManager.isOnCooldown(FULL_MOON_STATE_KEY, holder.getUUID())) {
            evt.setAmount(evt.getAmount() * 0.5f);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getEntity().level().isDay()) return;
        LivingEntity holder = evt.getEntity();
        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) return;
        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getItemEnchantmentLevel(fullMoon, armor);
        }
        if (totalLevel > 0) evt.setAmount(evt.getAmount() * 1.25f);
    }

    @Override protected boolean checkCompatibility(Enchantment ench) {
        if (ench instanceof EnchantmentTimeReversal) return false;
        return super.checkCompatibility(ench);
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
