package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 * <p>硬箭附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "hard_arrow", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnumEnchantmentType.BOW, slots = {EntityEquipmentSlot.MAINHAND})
public class EnchantmentHardArrow extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "hard_arrow";

    /**
     * 每级的箭矢伤害倍率加成
     * <p>默认 0.8，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle ARROW_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "arrow_damage_per_level",
                    0.8D, 0.0D, 10.0D);

    /**
     * 每级的近战伤害倍率加成
     * <p>默认 0.8，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle MELEE_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "melee_damage_per_level",
                    0.8D, 0.0D, 10.0D);

    /**
     * 每级的击退强度加成
     * <p>默认 0.75，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle KNOCKBACK_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "knockback_per_level",
                    0.75D, 0.0D, 10.0D);

    /**
     * 范围搜索半径（格）
     * <p>默认 12，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle AOE_RADIUS =
            EnchantmentValues.define(VALUE_ID, "aoe_radius",
                    12, 1, 64);

    public EnchantmentHardArrow() { super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @Override public int getMinCost(int l) { return (int)((5 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }

    @Override protected boolean checkCompatibility(@Nonnull Enchantment ench) { return super.checkCompatibility(ench) && !ench.equals(Enchantments.POWER); }
}
