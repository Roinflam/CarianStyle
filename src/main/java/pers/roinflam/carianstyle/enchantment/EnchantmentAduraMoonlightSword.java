package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 * <p>阿杜拉月光剑附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "adura_moonlight_sword",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentEpilepsyFire.class,
                EnchantmentEatShit.class,
                EnchantmentHypnoticSmoke.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class
        }
)
public class EnchantmentAduraMoonlightSword extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "adura_moonlight_sword";

    /**
     * AOE 搜索半径上限（格）
     * <p>默认 6，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    6, 1, 64);

    /**
     * 单次触发最大命中目标数
     * <p>默认 16，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    16, 1, 200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 施加冻伤的持续时间（tick）
     * <p>默认 200，允许范围 20 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle FROSTBITE_DURATION =
            EnchantmentValues.define(VALUE_ID, "frostbite_duration",
                    200, 20, 6000);

    /**
     * 冻伤可叠加到的最高等级（0 表示 I 级）
     * <p>默认 9，允许范围 0 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle FROSTBITE_MAX_AMPLIFIER =
            EnchantmentValues.define(VALUE_ID, "frostbite_max_amplifier",
                    9, 0, 127);

    public EnchantmentAduraMoonlightSword() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
