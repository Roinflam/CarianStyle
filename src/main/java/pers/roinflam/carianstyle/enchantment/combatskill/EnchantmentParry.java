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
 * <p>招架附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "parry",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        // v2.3：type = BREAKABLE → customType = "SHIELD"，与下方构造函数保持一致
        customType = "SHIELD",
        slots = {EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND}
)
public class EnchantmentParry extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "parry";

    /**
     * 格挡成功后的反击窗口时长（tick）
     * <p>默认 10，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle WINDOW_TICKS =
            EnchantmentValues.define(VALUE_ID, "window_ticks",
                    10, 1, 200);

    /**
     * 窗口内每级的额外伤害倍率
     * <p>默认 0.25，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.25D, 0.0D, 5.0D);

    /**
     * 格挡冷却时长（tick）
     * <p>默认 40，允许范围 0 ~ 2400。</p>
     */
    private static final EnchantmentValues.Handle COOLDOWN_TICKS =
            EnchantmentValues.define(VALUE_ID, "cooldown_ticks",
                    40, 0, 2400);

    private static final String PARRY_LEVEL_KEY = "parry_level";

    private static final String PARRY_COOLDOWN_KEY = "parry_cooldown";

    public EnchantmentParry() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EntityEquipmentSlot[]{
                EntityEquipmentSlot.MAINHAND,
                EntityEquipmentSlot.OFFHAND
        });
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
