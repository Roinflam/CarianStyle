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
 * <p>黑焰庇护附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "black_flame_shelter",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST},
        conflictsWith = {
                EnchantmentShelterOfFire.class,
                EnchantmentHealingByFire.class
        }
)
public class EnchantmentBlackFlameShelter extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "black_flame_shelter";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级对物理伤害的减免比例（魔法伤害和无视护甲的伤害不减；键名沿用旧的 melee，改了老配置就对不上）
     * <p>默认 0.125，允许范围 0.0 ~ 0.5。</p>
     */
    private static final EnchantmentValues.Handle MELEE_REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "melee_reduction_per_level",
                    0.125D, 0.0D, 0.5D);

    /**
     * 每级的治疗削减比例（代价；键名沿用旧的 projectile，改了老配置就对不上）
     * <p>默认 0.25，允许范围 0.0 ~ 0.5。</p>
     */
    private static final EnchantmentValues.Handle PROJECTILE_REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "projectile_reduction_per_level",
                    0.25D, 0.0D, 0.5D);

    /**
     * 参与计算的累加等级硬上限。
     * <p>
     * 减伤为每级 12.5%，8 级系数归零、9 级起为负并导致<b>伤害反向变成治疗</b>
     * （详见类注释）。卡在 {@value} 级 = 减伤 87.5%，是符号翻转前的最后一级。
     * </p>
     * <p>
     * <b>无条件生效</b>，不受 {@code ConfigLoader.levelLimit} 影响——
     * 这是正确性约束，不是平衡开关。
     * </p>
     * <p>
     * <b>public 的原因：</b>{@code CarianStyleConditionDisplay} 的 HUD 要显示当前减伤百分比，
     * 必须用与这里完全相同的上限，否则会出现「HUD 显示 125%、实际只有 87.5%」。
     * 在那边复制一份 {@code 7} 的字面量是不安全的——改了这里而那边没跟上，
     * HUD 会安静地开始说谎。
     * </p>
     */
    public static final int MAX_EFFECTIVE_LEVEL = 7;

    public EnchantmentBlackFlameShelter() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        if (ench == Enchantments.PROTECTION) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
