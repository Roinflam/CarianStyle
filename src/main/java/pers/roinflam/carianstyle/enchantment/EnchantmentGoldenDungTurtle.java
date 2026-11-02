package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E4)
 * 黄金粪金龟附魔 - 修复: getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1 */
@Deprecated
public class EnchantmentGoldenDungTurtle extends EnchantmentBase {
    private static final String VALUE_ID = "golden_dung_turtle";
    private static final EnchantmentValues.Handle EXP_BONUS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "exp_bonus_per_level",
                    0.3D, 0.0D, 10.0D);
    public int getMinCost(int l) {
        return 0;
    }
    public int getMaxCost(int l) {
        return 0;
    }
}

