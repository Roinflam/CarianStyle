// 文件：EnchantmentFullMoon.java
// 路径：forge/src/main/java/pers/roinflam/carianstyle/enchantment/recollect/EnchantmentFullMoon.java
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
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentHealingByFire;
import pers.roinflam.carianstyle.enchantment.EnchantmentShelterOfFire;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import java.util.UUID;

/**
 * 满月附魔
 * <p>修复: DarkMoon检查getUsedItemHand -> InteractionHand.MAIN_HAND</p>
 * <p>v2.2新增: onLivingDeath 入口接入怪物附魔触发开关，
 * 怪物身上的"濒死复活"效果可由配置 allowMobTriggerDeathEnchantments 控制</p>
 * <p>v2.3新增: onLivingHeal 入口补齐怪物附魔触发开关，
 * 怪物在夜晚的治疗加成由 allowMobTriggerEnchantments 控制</p>
 *
 * @version 2.3
 */
@AutoRegisterEnchantment(id = "full_moon", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.ARMOR_CHEST, slots = {EquipmentSlot.CHEST}, conflictsWith = {EnchantmentHealingByFire.class, EnchantmentShelterOfFire.class})
@Mod.EventBusSubscriber
public class EnchantmentFullMoon extends EnchantmentBase {
    private static final String FULL_MOON_STATE_KEY = "full_moon_state";
    private static final String FULL_MOON_COOLDOWN_KEY = "full_moon_cooldown";
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentFullMoon() { super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST}); }

    /**
     * 监听生物死亡事件 - 触发濒死复活机制
     * <p>v2.2新增：怪物附魔触发开关（濒死类）拦截</p>
     *
     * @param evt 死亡事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getSource().isCreativePlayer()) return;
        LivingEntity holder = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关 —— 满月属于濒死复活类，怪物身上不触发
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, true)) return;

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

    /**
     * 监听生物受伤事件 - 复活期间50%伤害减免
     * <p>注意：本事件不属于"死亡触发"，未接入怪物附魔开关；
     * 若 onLivingDeath 已被拦截，复活状态本来就不会被设置，本方法的伤害减免不会触发</p>
     *
     * @param evt 受伤事件
     */
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

    /**
     * 监听生物治疗事件 - 夜晚恢复效果+25%
     * <p>v2.3：补齐怪物附魔触发开关（受治疗者视角，非濒死触发）。
     * 此前缺失开关检查，导致怪物在夜晚被治疗时仍获得 25% 加成。</p>
     *
     * @param evt 治疗事件
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getEntity().level().isDay()) return;
        LivingEntity holder = evt.getEntity();

        // ⭐ v2.3：怪物附魔触发开关（受治疗者视角，治疗加成非濒死触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

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