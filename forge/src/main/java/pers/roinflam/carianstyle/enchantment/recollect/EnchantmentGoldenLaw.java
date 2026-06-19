package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import java.util.UUID;

/**
 * 黄金律法附魔
 * <p>v2.2：双向监听器入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "golden_law", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber
public class EnchantmentGoldenLaw extends EnchantmentBase {
    private static final String IMMUNITY_COOLDOWN_KEY = "golden_law_immunity";
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentGoldenLaw() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getSource().isCreativePlayer()) return;
        Enchantment goldenLaw = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenLaw.class);
        if (goldenLaw == null) return;

        // 攻击者视角
        if (evt.getSource().getDirectEntity() instanceof LivingEntity attacker) {
            // ⭐ v2.2：怪物附魔触发开关（攻击者视角）
            if (!EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) {
                ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
                if (!heldItem.isEmpty()) {
                    int level = EnchantmentHelper.getItemEnchantmentLevel(goldenLaw, heldItem);
                    if (level > 0) {
                        float healthRatio = attacker.getHealth() / attacker.getMaxHealth();
                        evt.setAmount(evt.getAmount() + evt.getAmount() * 0.15f + evt.getAmount() * 0.45f * (1 - healthRatio));
                    }
                }
            }
        }

        // 受击者视角
        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack heldItem = victim.getItemInHand(InteractionHand.MAIN_HAND);
        if (!heldItem.isEmpty()) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(goldenLaw, heldItem);
            if (level > 0) evt.setAmount(evt.getAmount() * 0.85f);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getSource().isCreativePlayer()) return;
        LivingEntity holder = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，"小伤免疫"非濒死触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        UUID uuid = holder.getUUID();
        ItemStack mainHand = holder.getMainHandItem();
        if (mainHand.isEmpty()) return;
        Enchantment goldenLaw = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenLaw.class);
        if (goldenLaw == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(goldenLaw, mainHand);
        if (level <= 0) return;
        if (evt.getAmount() <= holder.getHealth() * 0.15) { evt.setCanceled(true); return; }
        if (!EnchantmentDataManager.isOnCooldown(IMMUNITY_COOLDOWN_KEY, uuid)) {
            evt.setCanceled(true);
            EnchantmentDataManager.setCooldown(IMMUNITY_COOLDOWN_KEY, uuid, 100);
        }
    }

    @Override protected boolean checkCompatibility(Enchantment ench) {
        if (ench.getClass().getPackage().getName().contains("law") && !ench.equals(this)) return false;
        return super.checkCompatibility(ench);
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
