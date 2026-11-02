package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E2)
 * <p>血附魔 - 修复: getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "blood", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND})
public class EnchantmentBlood extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "blood";

    /**
     * 触发所需的连续攻击次数
     * <p>默认 2，允许范围 1 ~ 50。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_ATTACK_COUNT =
            EnchantmentValues.define(VALUE_ID, "trigger_attack_count",
                    2, 1, 50);

    /**
     * 按目标当前生命计算的额外伤害比例
     * <p>默认 0.12，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "damage_ratio",
                    0.12D, 0.0D, 2.0D);

    /**
     * 单次治疗量的最大生命占比上限
     * <p>默认 0.18，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_CAP_RATIO =
            EnchantmentValues.define(VALUE_ID, "heal_cap_ratio",
                    0.18D, 0.0D, 2.0D);

    /**
     * 三件套联动时施加的出血等级
     * <p>默认 7，允许范围 0 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle HEMORRHAGE_LEVEL_COMBO =
            EnchantmentValues.define(VALUE_ID, "hemorrhage_level_combo",
                    7, 0, 127);

    /**
     * 施加出血的持续时间（tick）
     * <p>默认 30，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle HEMORRHAGE_DURATION =
            EnchantmentValues.define(VALUE_ID, "hemorrhage_duration",
                    30, 1, 1200);

    /**
     * 攻击计数的保持时长（tick）
     * <p>默认 6000，允许范围 20 ~ 72000。</p>
     */
    private static final EnchantmentValues.Handle COUNTER_DURATION =
            EnchantmentValues.define(VALUE_ID, "counter_duration",
                    6000, 20, 72000);

    private static final String ATTACK_COUNT_KEY = "blood_attack_count";

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentBlood() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
