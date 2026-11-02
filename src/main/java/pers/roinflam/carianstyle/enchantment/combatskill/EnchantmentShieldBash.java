package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 * <p>盾击附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "shield_bash",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        customType = "SHIELD",
        slots = {EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND}
)
public class EnchantmentShieldBash extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "shield_bash";

    /**
     * 每级的击退强度
     * <p>默认 0.25，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle KNOCKBACK_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "knockback_per_level",
                    0.25D, 0.0D, 5.0D);

    public EnchantmentShieldBash() {
        // 修复：BREAKABLE → SHIELD自定义类型
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EntityEquipmentSlot[]{
                EntityEquipmentSlot.MAINHAND,
                EntityEquipmentSlot.OFFHAND
        });
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
