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
 * <p>沙布里里嚎叫附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "howl_shabriri",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentHowlShabriri extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "howl_shabriri";

    /**
     * 自损持续时间（tick）
     * <p>默认 60，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle SELF_DOT_DURATION =
            EnchantmentValues.define(VALUE_ID, "self_dot_duration",
                    60, 1, 1200);

    /**
     * 自损起始延迟（tick）
     * <p>默认 5，允许范围 0 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle SELF_DOT_DELAY =
            EnchantmentValues.define(VALUE_ID, "self_dot_delay",
                    5, 0, 200);

    /**
     * 疯狂叠层的最高等级
     * <p>默认 5，允许范围 0 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle MAX_AMPLIFIER =
            EnchantmentValues.define(VALUE_ID, "max_amplifier",
                    5, 0, 127);

    /**
     * 每级的额外伤害倍率
     * <p>默认 0.15，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.15D, 0.0D, 5.0D);

    /**
     * 自损总量占最大生命的比例
     * <p>默认 0.05，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle SELF_DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_damage_ratio",
                    0.05D, 0.0D, 1.0D);

    /**
     * 每级叠层的持续秒数
     * <p>默认 3，允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle STACK_SECONDS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "stack_seconds_per_level",
                    3, 1, 120);

    public EnchantmentHowlShabriri() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
