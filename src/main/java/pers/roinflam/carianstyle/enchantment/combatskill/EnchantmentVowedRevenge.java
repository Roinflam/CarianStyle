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
 * <p>誓复仇附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "vowed_revenge",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentVowedRevenge extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "vowed_revenge";

    /**
     * AOE 搜索半径上限（格）
     * <p>默认 8，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    8, 1, 64);

    /**
     * 每个周围目标每级提供的额外伤害倍率
     * <p>默认 0.025，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_TARGET_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_target_per_level",
                    0.025D, 0.0D, 1.0D);

    /**
     * 每级的复仇溅射伤害倍率
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle REVENGE_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "revenge_damage_per_level",
                    0.05D, 0.0D, 2.0D);

    /** 计数目标数硬上限：防止密集怪物场景下 entities.size() 作乘数导致伤害/性能爆炸 */
    private static final int MAX_COUNTED_TARGETS = 20;

    public EnchantmentVowedRevenge() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
