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
 * <p>刺客赌局附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "assassin_gambit",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentAssassinGambit extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "assassin_gambit";

    /**
     * 参与计算的等级上限，防止超高等级导致伤害失控
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级对潜行目标的额外伤害倍率
     * <p>默认 0.25，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.25D, 0.0D, 5.0D);

    /**
     * 每级提供的潜行持续时间（tick）
     * <p>默认 20，允许范围 1 ~ 400。</p>
     */
    private static final EnchantmentValues.Handle STEALTH_TICKS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "stealth_ticks_per_level",
                    20, 1, 400);

    /**
     * 潜行状态下的暴击倍率
     * <p>默认 2.0，允许范围 1.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle CRIT_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "crit_multiplier",
                    2.0D, 1.0D, 20.0D);

    public EnchantmentAssassinGambit() {
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
