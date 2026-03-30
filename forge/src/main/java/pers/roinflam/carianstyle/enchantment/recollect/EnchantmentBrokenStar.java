package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

/** 碎星附魔 - 修复: getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1 */
@AutoRegisterEnchantment(id = "broken_star", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber
public class EnchantmentBrokenStar extends EnchantmentBase {
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentBrokenStar() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        Enchantment brokenStar = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBrokenStar.class);
        if (brokenStar == null) return;
        if (evt.getSource().getDirectEntity() instanceof LivingEntity attacker) {
            ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
            if (!heldItem.isEmpty()) {
                int level = EnchantmentHelper.getItemEnchantmentLevel(brokenStar, heldItem);
                if (ConfigLoader.levelLimit) level = Math.min(level, 10);
                if (level > 0 && attacker.getHealth() >= attacker.getMaxHealth() / 2) {
                    evt.setAmount(evt.getAmount() * (!attacker.level().isDay() ? 2 : 1.5f));
                }
            }
        }
        if (!evt.getSource().isCreativePlayer()) {
            LivingEntity victim = evt.getEntity();
            ItemStack heldItem = victim.getItemInHand(InteractionHand.MAIN_HAND);
            if (!heldItem.isEmpty()) {
                int level = EnchantmentHelper.getItemEnchantmentLevel(brokenStar, heldItem);
                if (level > 0 && victim.getHealth() <= victim.getMaxHealth() / 2) {
                    evt.setAmount(evt.getAmount() * (!victim.level().isDay() ? 0.75f : 0.5f));
                }
            }
        }
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
