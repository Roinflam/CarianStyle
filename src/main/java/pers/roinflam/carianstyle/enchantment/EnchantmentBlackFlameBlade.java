package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E2)
 * <p>黑焰刃附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "black_flame_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true,
        conflictsWith = {
                EnchantmentInvisibleWeapon.class
        }
)
public class EnchantmentBlackFlameBlade extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "black_flame_blade";

    /**
     * 持续伤害的总时长（tick）
     * <p>默认 100，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DURATION =
            EnchantmentValues.define(VALUE_ID, "dot_duration",
                    100, 1, 1200);

    /**
     * 持续伤害的起始延迟（tick）
     * <p>默认 5，允许范围 0 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DELAY =
            EnchantmentValues.define(VALUE_ID, "dot_delay",
                    5, 0, 200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 黑炎燃烧视觉的持续秒数
     * <p>默认 5，允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle BURNING_SECONDS =
            EnchantmentValues.define(VALUE_ID, "burning_seconds",
                    5, 1, 120);

    /**
     * 每级的持续伤害总量倍率
     * <p>默认 0.15，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.15D, 0.0D, 5.0D);

    public EnchantmentBlackFlameBlade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
