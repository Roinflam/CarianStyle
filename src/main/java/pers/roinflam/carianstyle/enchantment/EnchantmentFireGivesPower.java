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
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 *
 * 火焰赋予力量附魔
 * <p>
 * 武器附魔，火焰增益效果
 * 攻击时：
 * - 如果自己着火：伤害增加 7.5% × 等级，且续燃10秒（如果燃烧时间<200tick）
 * - 如果自己没着火：点燃自己10秒（创造模式免疫）
 * 受击时：
 * - 如果自己着火且伤害是物理伤害：减伤 3.75% × 等级
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "fire_gives_power",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 5,
        levelMultiplier = 10
)
@Deprecated
public class EnchantmentFireGivesPower extends EnchantmentBase {
    private static final String VALUE_ID = "fire_gives_power";
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.075D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.0375D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle IGNITE_SECONDS = EnchantmentValues.define(VALUE_ID, "ignite_seconds",
                    10, 1, 300);
    public EnchantmentFireGivesPower() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onDamageAsAttackerLow(@NotNull EnchantmentContext ctx, int level) {
    }
    protected void onDamageAsVictimLow(@NotNull EnchantmentContext ctx, int level) {
    }
}

