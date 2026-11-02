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
 * <p>洛蕾塔大弓附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "loretta_big_bow",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentLorettaBigBow extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "loretta_big_bow";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 箭矢基础伤害的倍率
     * <p>默认 1.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle ARROW_DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "arrow_damage_multiplier",
                    1.5D, 0.0D, 10.0D);

    /**
     * 爆炸强度
     * <p>默认 2.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "explosion_strength",
                    2.0D, 0.0D, 20.0D);

    /**
     * 火箭矢的爆炸强度
     * <p>默认 3.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH_ON_FIRE =
            EnchantmentValues.define(VALUE_ID, "explosion_strength_on_fire",
                    3.0D, 0.0D, 20.0D);

    public EnchantmentLorettaBigBow() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (25 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
