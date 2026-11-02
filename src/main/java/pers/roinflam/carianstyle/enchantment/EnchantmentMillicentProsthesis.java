package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 * <p>米莉森义肢附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "millicent_prosthesis",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class,
                EnchantmentRedFeatheredBranchsword.class,
                EnchantmentBlueFeatheredBranchsword.class
        }
)
public class EnchantmentMillicentProsthesis extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "millicent_prosthesis";

    /**
     * 叠层满时每级的额外伤害倍率
     * <p>默认 0.1，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL_FULL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level_full",
                    0.1D, 0.0D, 5.0D);

    /**
     * 叠层未满时每级的额外伤害倍率
     * <p>默认 0.05，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL_PARTIAL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level_partial",
                    0.05D, 0.0D, 5.0D);

    public EnchantmentMillicentProsthesis() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
