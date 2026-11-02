package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 *
 * 剑舞附魔
 * <p>
 * 减少敌人无敌帧（减半），可快速连续造成伤害
 * 如果是玩家攻击，伤害 × 攻击冷却进度（最低0.4倍）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "sword_dance",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true,
        baseEnchantability = 35,
        levelMultiplier = 0
)
@Deprecated
public class EnchantmentSwordDance extends EnchantmentBase {
    private static final String VALUE_ID = "sword_dance";
    private static final EnchantmentValues.Handle INVULNERABLE_DIVISOR = EnchantmentValues.define(VALUE_ID, "invulnerable_divisor",
                    2.0D, 1.0D, 20.0D);
    private static final EnchantmentValues.Handle ATTACK_SCALE_FLOOR = EnchantmentValues.define(VALUE_ID, "attack_scale_floor",
                    0.4D, 0.0D, 1.0D);
    public EnchantmentSwordDance() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
}

