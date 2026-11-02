package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 * <p>不动之盾附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "immutable_shield", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, customType = "SHIELD", slots = {EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND})
public class EnchantmentImmutableShield extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "immutable_shield";

    /**
     * 格挡成功时每级回复的最大生命占比
     * <p>默认 0.01，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.01D, 0.0D, 1.0D);

    /**
     * 每级的减伤比例
     * <p>默认 0.1，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.1D, 0.0D, 1.0D);

    public EnchantmentImmutableShield() {
        // 修复：BREAKABLE → SHIELD自定义类型
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EntityEquipmentSlot[]{
                EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND
        });
    }

    @Override public int getMinCost(int l) { return (int)((5 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class));
    }
}
