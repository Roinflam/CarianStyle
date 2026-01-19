package pers.roinflam.carianstyle.enchantment.recollect;

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

/**
 * 碎星附魔
 * <p>
 * 攻击者（血量>=50%）：夜晚伤害×2，白天×1.5
 * 受击者（血量<=50%）：夜晚减伤25%，白天减伤50%
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "broken_star",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentBrokenStar extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentBrokenStar() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        Enchantment brokenStar = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBrokenStar.class);
        if (brokenStar == null) {
            return;
        }

        // 攻击者视角
        if (evt.getSource().getDirectEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();

            ItemStack heldItem = attacker.getItemInHand(attacker.getUsedItemHand());
            if (!heldItem.isEmpty()) {
                int level = EnchantmentHelper.getItemEnchantmentLevel(brokenStar, heldItem);

                if (ConfigLoader.levelLimit) {
                    level = Math.min(level, 10);
                }

                if (level > 0) {
                    if (attacker.getHealth() >= attacker.getMaxHealth() / 2) {
                        if (!attacker.level().isDay()) {
                            evt.setAmount(evt.getAmount() * 2);
                        } else {
                            evt.setAmount(evt.getAmount() * 1.5f);
                        }
                    }
                }
            }
        }

        // 受击者视角
        if (!evt.getSource().isCreativePlayer()) {
            LivingEntity victim = evt.getEntity();

            ItemStack heldItem = victim.getItemInHand(victim.getUsedItemHand());
            if (!heldItem.isEmpty()) {
                int level = EnchantmentHelper.getItemEnchantmentLevel(brokenStar, heldItem);

                if (level > 0) {
                    if (victim.getHealth() <= victim.getMaxHealth() / 2) {
                        if (!victim.level().isDay()) {
                            evt.setAmount(evt.getAmount() * 0.75f);
                        } else {
                            evt.setAmount(evt.getAmount() * 0.5f);
                        }
                    }
                }
            }
        }
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