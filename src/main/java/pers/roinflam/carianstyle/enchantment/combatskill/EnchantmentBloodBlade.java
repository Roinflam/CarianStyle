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
 * <p>血刃附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "blood_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
public class EnchantmentBloodBlade extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "blood_blade";

    /**
     * 每级基于本次伤害的额外伤害倍率
     * <p>默认 0.33，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.33D, 0.0D, 5.0D);

    /**
     * 每级基于目标当前生命的额外伤害倍率
     * <p>默认 0.033，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_HEALTH_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "victim_health_per_level",
                    0.033D, 0.0D, 1.0D);

    /**
     * 每次触发自伤的最大生命占比
     * <p>默认 0.15，允许范围 0.0 ~ 0.9。</p>
     */
    private static final EnchantmentValues.Handle SELF_DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_damage_ratio",
                    0.15D, 0.0D, 0.9D);

    public EnchantmentBloodBlade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 获取最低附魔能力需求
     *
     * @param enchantmentLevel 附魔等级
     * @return 最低附魔能力值
     */
    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    /**
     * 获取最高附魔能力需求
     *
     * @param enchantmentLevel 附魔等级
     * @return 最高附魔能力值
     */
    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
