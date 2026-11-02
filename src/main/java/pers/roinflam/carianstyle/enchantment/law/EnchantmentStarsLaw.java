package pers.roinflam.carianstyle.enchantment.law;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentDarkAbandonedChild;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 *
 * 星律附魔
 * <p>
 * 注意：此类使用了FROSTBITE（冻伤）效果，需要先在 DynamicAttributes 中定义该效果
 * <p>
 * 夜晚效果：
 * - 受到的恢复效果提高50%
 * - 提高25%移动速度
 * - 造成伤害或受到物理伤害时，目标/来源受到持续10秒的1级冻伤效果
 * - 若冻伤已存在则提升1级，最高10级
 * - 对已冻伤的目标造成的魔法伤害提高[冻伤等级]*7.5%
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@Deprecated
public class EnchantmentStarsLaw extends EnchantmentBase {
    private static final String VALUE_ID = "stars_law";
    private static final EnchantmentValues.Handle FROSTBITE_DURATION = EnchantmentValues.define(VALUE_ID, "frostbite_duration",
                    200, 20, 6000);
    private static final EnchantmentValues.Handle MAX_FROSTBITE_LEVEL = EnchantmentValues.define(VALUE_ID, "max_frostbite_level",
                    10, 1, 127);
    private static final EnchantmentValues.Handle SPEED_BOOST_LEVEL = EnchantmentValues.define(VALUE_ID, "speed_boost_level",
                    25, 0, 127);
    private static final EnchantmentValues.Handle DAMAGE_PER_STACK = EnchantmentValues.define(VALUE_ID, "damage_per_stack",
                    0.075D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle HEAL_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "heal_multiplier",
                    1.5D, 0.0D, 10.0D);
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    protected void onHurtAsAttackerLow(@NotNull EnchantmentContext ctx, int level) {
    }
    protected void onHurtAsVictim(@NotNull EnchantmentContext ctx, int level) {
    }
    protected void onHeal(@NotNull EnchantmentContext ctx, int level) {
    }
    protected void onPlayerTick(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
    protected boolean checkCompatibility(Enchantment ench) {
        return false;
    }
}

