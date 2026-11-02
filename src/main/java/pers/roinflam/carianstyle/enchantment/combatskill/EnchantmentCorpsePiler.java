package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 * <p>尸山血海附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "corpse_piler",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true,
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
public class EnchantmentCorpsePiler extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "corpse_piler";

    /**
     * 每层击杀数每级提供的额外伤害倍率
     * <p>默认 0.01，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_KILL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_kill_per_level",
                    0.01D, 0.0D, 1.0D);

    /**
     * 每层击杀数每级提供的最大生命回复占比
     * <p>默认 0.0005，允许范围 0.0 ~ 0.1。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_KILL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_kill_per_level",
                    0.0005D, 0.0D, 0.1D);

    /**
     * 击杀层数上限
     * <p>默认 50，允许范围 1 ~ 999。</p>
     */
    private static final EnchantmentValues.Handle MAX_KILL_COUNT =
            EnchantmentValues.define(VALUE_ID, "max_kill_count",
                    50, 1, 999);

    /**
     * 击杀层数的保持时长（tick）
     * <p>默认 6000，允许范围 20 ~ 72000。</p>
     */
    private static final EnchantmentValues.Handle COUNT_DURATION_TICKS =
            EnchantmentValues.define(VALUE_ID, "count_duration_ticks",
                    6000, 20, 72000);

    private static final String KILL_COUNT_KEY = "corpse_piler_kills";

    public EnchantmentCorpsePiler() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
