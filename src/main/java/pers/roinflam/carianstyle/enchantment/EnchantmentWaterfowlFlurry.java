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
 * <p>水鸟乱舞附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "waterfowl_flurry",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentWaterfowlFlurry extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "waterfowl_flurry";

    /**
     * 「水鸟乱舞伤害」标记的保持时长（tick，需覆盖全部段数）
     * <p>默认 100，允许范围 20 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle MARKER_DURATION =
            EnchantmentValues.define(VALUE_ID, "marker_duration",
                    100, 20, 1200);

    /**
     * 两段攻击之间的间隔（tick）
     * <p>默认 2，允许范围 1 ~ 40。</p>
     */
    private static final EnchantmentValues.Handle HIT_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "hit_interval",
                    2, 1, 40);

    /**
     * 每段命中后目标的无敌帧（tick）
     * <p>默认 10，允许范围 0 ~ 40。</p>
     */
    private static final EnchantmentValues.Handle HIT_INVULNERABLE_TIME =
            EnchantmentValues.define(VALUE_ID, "hit_invulnerable_time",
                    10, 0, 40);

    private static final String DAMAGE_TYPE_MARKER = "waterfowl_dance_marker";

    public EnchantmentWaterfowlFlurry() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 30) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
