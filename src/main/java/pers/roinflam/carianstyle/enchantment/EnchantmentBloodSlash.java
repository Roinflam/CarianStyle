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
 * <p>血斩附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "blood_slash", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentScarletCorruption.class, EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class, EnchantmentVicDragonThunder.class, EnchantmentDarkMoon.class})
public class EnchantmentBloodSlash extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "blood_slash";

    /**
     * 每级按目标当前生命计算的额外伤害比例
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.05D, 0.0D, 2.0D);

    /**
     * 每次触发自伤的最大生命占比
     * <p>默认 0.1，允许范围 0.0 ~ 0.9。</p>
     */
    private static final EnchantmentValues.Handle SELF_DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_damage_ratio",
                    0.1D, 0.0D, 0.9D);

    /**
     * 同时装备「血的收藏」时击杀每级回复的最大生命占比
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL_WITH_COLLECTION =
            EnchantmentValues.define(VALUE_ID, "heal_per_level_with_collection",
                    0.05D, 0.0D, 2.0D);

    /**
     * 击杀每级回复的最大生命占比
     * <p>默认 0.025，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.025D, 0.0D, 2.0D);

    public EnchantmentBloodSlash() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @Override public int getMinCost(int l) { return (int)((20 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
