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
 * <p>唤星附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "call_star",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentLorettaBigBow.class,
                EnchantmentLorettaTrick.class
        }
)
public class EnchantmentCallStar extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "call_star";

    /**
     * 吸引效果的搜索半径上限（格）
     * <p>默认 12，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_ATTRACT_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_attract_radius",
                    12, 1, 64);

    /**
     * 落雷效果的搜索半径上限（格）
     * <p>默认 8，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_LIGHTNING_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_lightning_radius",
                    8, 1, 64);

    /**
     * 单次触发最大命中目标数
     * <p>默认 20，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    20, 1, 200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级的吸引拉力强度
     * <p>默认 0.35，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle ATTRACT_STRENGTH_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "attract_strength_per_level",
                    0.35D, 0.0D, 5.0D);

    /**
     * 每级的落雷伤害倍率
     * <p>默认 0.3，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.3D, 0.0D, 5.0D);

    /**
     * 夜间的伤害倍率（白天恒为 1）
     * <p>默认 3.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle NIGHT_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "night_multiplier",
                    3.0D, 0.0D, 20.0D);

    public EnchantmentCallStar() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
