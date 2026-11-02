package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 * <p>对空射击附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "sky_shot",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentSkyShot extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "sky_shot";

    /**
     * 触发所需的目标高度差（格）
     * <p>默认 5.0，允许范围 0.0 ~ 128.0。</p>
     */
    private static final EnchantmentValues.Handle HEIGHT_THRESHOLD =
            EnchantmentValues.define(VALUE_ID, "height_threshold_blocks",
                    5.0D, 0.0D, 128.0D);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 基于目标当前生命的额外伤害占比
     * <p>默认 0.1，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle TARGET_HEALTH_DAMAGE =
            EnchantmentValues.define(VALUE_ID, "target_health_damage",
                    0.1D, 0.0D, 1.0D);

    public EnchantmentSkyShot() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
