package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E1)
 *
 * 切割附魔
 * <p>v2.2：onLivingDeath 击杀者视角接入怪物附魔触发开关。
 * onDamageAsAttacker 走中央事件分发器，已经在 scanEntity 入口被通用开关拦截。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "incision",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        },
        baseEnchantability = 35,
        levelMultiplier = 0
)
@Deprecated
public class EnchantmentIncision extends EnchantmentBase {
    private static final String VALUE_ID = "incision";
    private static final EnchantmentValues.Handle TRIGGER_HEALTH_RATIO = EnchantmentValues.define(VALUE_ID, "trigger_health_ratio",
                    0.75D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle SELF_COST_RATIO = EnchantmentValues.define(VALUE_ID, "self_cost_ratio",
                    0.5D, 0.0D, 0.95D);
    private static final EnchantmentValues.Handle SELF_EFFECT_DURATION = EnchantmentValues.define(VALUE_ID, "self_effect_duration",
                    200, 20, 6000);
    private static final EnchantmentValues.Handle HEAL_RATIO = EnchantmentValues.define(VALUE_ID, "heal_ratio",
                    0.25D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle HEAL_CAP_RATIO = EnchantmentValues.define(VALUE_ID, "heal_cap_ratio",
                    0.25D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle HEMORRHAGE_DURATION = EnchantmentValues.define(VALUE_ID, "hemorrhage_duration",
                    30, 1, 1200);
    private static final EnchantmentValues.Handle KILL_HEAL_RATIO = EnchantmentValues.define(VALUE_ID, "kill_heal_ratio",
                    0.1D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle KILL_DURATION_BONUS = EnchantmentValues.define(VALUE_ID, "kill_duration_bonus",
                    100, 0, 6000);
    public EnchantmentIncision() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onDamageAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
}

