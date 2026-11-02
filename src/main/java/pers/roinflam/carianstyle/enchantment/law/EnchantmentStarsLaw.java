package pers.roinflam.carianstyle.enchantment.law;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentDarkAbandonedChild;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 * <p>星律附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "stars_law",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.LAW,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class
        }
)
public class EnchantmentStarsLaw extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "stars_law";

    /**
     * 施加冻伤的持续时间（tick）
     * <p>默认 200，允许范围 20 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle FROSTBITE_DURATION =
            EnchantmentValues.define(VALUE_ID, "frostbite_duration",
                    200, 20, 6000);

    /**
     * 冻伤可叠加到的最高层数
     * <p>默认 10，允许范围 1 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle MAX_FROSTBITE_LEVEL =
            EnchantmentValues.define(VALUE_ID, "max_frostbite_level",
                    10, 1, 127);

    /**
     * 提供的速度效果等级（1 级 = 移速 +1%，0 表示不加速）
     * <p>默认 25，允许范围 0 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle SPEED_BOOST_LEVEL =
            EnchantmentValues.define(VALUE_ID, "speed_boost_level",
                    25, 0, 127);

    /**
     * 每层冻伤提供的额外伤害倍率
     * <p>默认 0.075，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_STACK =
            EnchantmentValues.define(VALUE_ID, "damage_per_stack",
                    0.075D, 0.0D, 2.0D);

    /**
     * 治疗量的倍率
     * <p>默认 1.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "heal_multiplier",
                    1.5D, 0.0D, 10.0D);

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentStarsLaw() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(Enchantment ench) {
        if (isRecollectEnchantment(ench) && !isDarkAbandonedChild(ench)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    private boolean isRecollectEnchantment(Enchantment ench) {
        return packageNameOf(ench).contains("recollect")
                || packageNameOf(ench).contains("law");
    }

    private boolean isDarkAbandonedChild(Enchantment ench) {
        return ench instanceof EnchantmentDarkAbandonedChild;
    }
}
