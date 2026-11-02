package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 *
 * 米莉森义肢附魔
 * <p>
 * 武器附魔，连续攻击增强
 * 攻击时：
 * - 叠加攻击增益效果（最多叠到等级×7层）
 * - 满层后刷新为等级×16层，持续60tick
 * 伤害加成：
 * - 满层时增伤10%×等级
 * - 未满层时增伤5%×等级
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "millicent_prosthesis",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class,
                EnchantmentRedFeatheredBranchsword.class,
                EnchantmentBlueFeatheredBranchsword.class
        },
        baseEnchantability = 30,
        levelMultiplier = 25
)
@Deprecated
public class EnchantmentMillicentProsthesis extends EnchantmentBase {
    private static final String VALUE_ID = "millicent_prosthesis";
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL_FULL = EnchantmentValues.define(VALUE_ID, "damage_per_level_full",
                    0.1D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL_PARTIAL = EnchantmentValues.define(VALUE_ID, "damage_per_level_partial",
                    0.05D, 0.0D, 5.0D);
    public EnchantmentMillicentProsthesis() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onAttackLowest(@NotNull EnchantmentContext ctx, int level) {
    }
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
}

