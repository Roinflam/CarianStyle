package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E1)
 *
 * 刺客赌局附魔
 * <p>v2.2：双向监听器+暴击事件入口接入怪物附魔触发开关。
 * CriticalHitEvent 仅玩家触发，开关检查作为安全网保留。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "assassin_gambit",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true,
        baseEnchantability = 25,
        levelMultiplier = 10
)
@Deprecated
public class EnchantmentAssassinGambit extends EnchantmentBase {
    private static final String VALUE_ID = "assassin_gambit";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.25D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle STEALTH_TICKS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "stealth_ticks_per_level",
                    20, 1, 400);
    private static final EnchantmentValues.Handle CRIT_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "crit_multiplier",
                    2.0D, 1.0D, 20.0D);
    public EnchantmentAssassinGambit() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
}

