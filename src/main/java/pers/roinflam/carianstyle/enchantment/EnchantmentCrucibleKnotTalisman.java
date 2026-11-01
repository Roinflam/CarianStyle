// 文件：EnchantmentCrucibleKnotTalisman.java
// 路径：src/main/java/pers/roinflam/carianstyle/enchantment/EnchantmentCrucibleKnotTalisman.java
package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;

import javax.annotation.Nonnull;

/**
 * 熔炉瘤附魔
 *
 * 效果：
 * - 当攻击来源的高度大于自身高度时，受到的伤害降低 25% × 等级
 * - 最大等级：3
 */
@AutoRegisterEnchantment(
        id = "crucible_knot_talisman",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentCrucibleKnotTalisman extends EnchantmentBase {

    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 每级减伤 */
    private static final float REDUCTION_PER_LEVEL = 0.25F;

    /**
     * 胸甲附魔
     * <p>以前类型写成了头盔、槽位却是胸甲，附魔台和铁砧只能上头盔。</p>
     */
    public EnchantmentCrucibleKnotTalisman() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    /**
     * 受击时触发：如果攻击者高度大于自身高度，则减伤
     */
    @Override
    protected void onHurtAsVictim(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase victim = ctx.getHolder();
        EntityLivingBase attacker = ctx.getAttacker();

        // 必须有攻击者
        if (attacker == null) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = capLevel(level, LEVEL_CAP);

        // 判断攻击者的Y坐标是否高于受击者
        if (attacker.posY > victim.posY) {
            // 减伤 = 伤害 × 等级 × 25%
            float damageReduction = 1 - effectiveLevel * REDUCTION_PER_LEVEL;
            ctx.multiplyDamage(damageReduction);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}