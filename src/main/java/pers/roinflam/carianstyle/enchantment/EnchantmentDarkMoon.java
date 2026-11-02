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
 * <p>暗月附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "dark_moon", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentScarletCorruption.class, EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class, EnchantmentVicDragonThunder.class})
public class EnchantmentDarkMoon extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "dark_moon";

    /**
     * 未同时装备满月时的减伤/增伤比例
     * <p>默认 0.25，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION =
            EnchantmentValues.define(VALUE_ID, "reduction",
                    0.25D, 0.0D, 1.0D);

    /**
     * 同时装备满月时的减伤/增伤比例
     * <p>默认 0.375，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_WITH_FULL_MOON =
            EnchantmentValues.define(VALUE_ID, "reduction_with_full_moon",
                    0.375D, 0.0D, 1.0D);

    /**
     * 未同时装备满月时的追加伤害比例
     * <p>默认 0.05，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle EXTRA_DAMAGE =
            EnchantmentValues.define(VALUE_ID, "extra_damage",
                    0.05D, 0.0D, 1.0D);

    /**
     * 同时装备满月时的追加伤害比例
     * <p>默认 0.075，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle EXTRA_DAMAGE_WITH_FULL_MOON =
            EnchantmentValues.define(VALUE_ID, "extra_damage_with_full_moon",
                    0.075D, 0.0D, 1.0D);

    /**
     * 夜视效果的持续时间（tick）
     * <p>默认 210，允许范围 20 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle NIGHT_VISION_TICKS =
            EnchantmentValues.define(VALUE_ID, "night_vision_ticks",
                    210, 20, 6000);

    public EnchantmentDarkMoon() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @Override public int getMinCost(int l) { return (int)(35 * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
