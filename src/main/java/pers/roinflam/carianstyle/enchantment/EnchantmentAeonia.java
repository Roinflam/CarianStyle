package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * PORT-STUB(E3)
 * 艾奥尼亚附魔 - 优化: LivingTickEvent -> PlayerTickEvent @version 2.1 */
@AutoRegisterEnchantment(id = "aeonia", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.VERY_RARE, type = EnchantTarget.WEAPON, slots = {EquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class}, baseEnchantability = 35, levelMultiplier = 0)
@Deprecated
public class EnchantmentAeonia extends EnchantmentBase {
    private static final String VALUE_ID = "aeonia";
    private static final EnchantmentValues.Handle HEAL_RATIO = EnchantmentValues.define(VALUE_ID, "heal_ratio",
                    0.1D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle TICK_INTERVAL = EnchantmentValues.define(VALUE_ID, "tick_interval",
                    20, 1, 600);
    private static final EnchantmentValues.Handle SELF_ROT_DURATION = EnchantmentValues.define(VALUE_ID, "self_rot_duration",
                    21, 1, 600);
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentAeonia() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
    }
}

