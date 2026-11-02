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
 * <p>洛蕾塔戏法附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "loretta_trick",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND},
        conflictsWith = {EnchantmentLorettaBigBow.class},
        forceTreasure = true
)
public class EnchantmentLorettaTrick extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "loretta_trick";

    /**
     * 箭矢基础伤害的削减比例
     * <p>默认 0.25，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle ARROW_DAMAGE_PENALTY =
            EnchantmentValues.define(VALUE_ID, "arrow_damage_penalty",
                    0.25D, 0.0D, 1.0D);

    /**
     * 爆炸强度
     * <p>默认 3.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "explosion_strength",
                    3.0D, 0.0D, 20.0D);

    /**
     * 火箭矢的爆炸强度
     * <p>默认 4.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH_ON_FIRE =
            EnchantmentValues.define(VALUE_ID, "explosion_strength_on_fire",
                    4.0D, 0.0D, 20.0D);

    public EnchantmentLorettaTrick() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int l) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
