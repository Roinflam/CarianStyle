package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import java.util.List;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * PORT-STUB(E1)
 *
 * 圣地附魔
 * <p>v2.2：受击减伤光环入口接入怪物附魔触发开关。
 * PlayerTickEvent 仅玩家触发，无需开关检查。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "holy_ground",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.SHIELD,
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND},
        baseEnchantability = 20,
        levelMultiplier = 15
)
@Deprecated
public class EnchantmentHolyGround extends EnchantmentBase {
    private static final String VALUE_ID = "holy_ground";
    private static final EnchantmentValues.Handle DAMAGE_REDUCTION_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_reduction_per_level",
                    0.05D, 0.0D, 0.2D);
    private static final EnchantmentValues.Handle PULSE_INTERVAL_TICKS = EnchantmentValues.define(VALUE_ID, "pulse_interval_ticks",
                    60, 5, 1200);
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.015D, 0.0D, 0.5D);
    private static final EnchantmentValues.Handle ABSORPTION_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "absorption_per_level",
                    0.03D, 0.0D, 0.5D);
    public EnchantmentHolyGround() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        });
    }
}

