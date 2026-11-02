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
 * <p>剑舞附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "sword_dance",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentSwordDance extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "sword_dance";

    /**
     * 命中后无敌帧的缩短倍数（2 表示减半，越大攻击频率越高）
     * <p>默认 2.0，允许范围 1.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle INVULNERABLE_DIVISOR =
            EnchantmentValues.define(VALUE_ID, "invulnerable_divisor",
                    2.0D, 1.0D, 20.0D);

    /**
     * 蓄力进度的下限：即使连点也至少按此比例结算伤害
     * <p>默认 0.4，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle ATTACK_SCALE_FLOOR =
            EnchantmentValues.define(VALUE_ID, "attack_scale_floor",
                    0.4D, 0.0D, 1.0D);

    public EnchantmentSwordDance() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
