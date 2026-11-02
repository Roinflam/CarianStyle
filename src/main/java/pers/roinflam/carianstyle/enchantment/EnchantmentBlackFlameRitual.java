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
 * PORT-STUB(E2)
 * <p>黑焰仪式附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "black_flame_ritual", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.ARMOR_CHEST, slots = {EntityEquipmentSlot.CHEST}, conflictsWith = {EnchantmentShelterOfFire.class, EnchantmentHealingByFire.class})
public class EnchantmentBlackFlameRitual extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "black_flame_ritual";

    /**
     * 每个负面效果提供的伤害加成
     * <p>默认 0.2，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle HARMFUL_BONUS =
            EnchantmentValues.define(VALUE_ID, "harmful_bonus",
                    0.2D, 0.0D, 5.0D);

    /**
     * 每个正面效果提供的伤害加成
     * <p>默认 0.1，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BENEFICIAL_BONUS =
            EnchantmentValues.define(VALUE_ID, "beneficial_bonus",
                    0.1D, 0.0D, 5.0D);

    /**
     * 自损结算间隔（tick）
     * <p>默认 20，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle TICK_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "tick_interval",
                    20, 1, 600);

    /**
     * 每次自损后保留的生命比例（越小掉血越快）
     * <p>默认 0.95，允许范围 0.5 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEALTH_RETAIN_RATIO =
            EnchantmentValues.define(VALUE_ID, "health_retain_ratio",
                    0.95D, 0.5D, 1.0D);

    public EnchantmentBlackFlameRitual() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.PROTECTION);
    }

    @Override
    public int getMinCost(int l) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
