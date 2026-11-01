package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;

import javax.annotation.Nonnull;

/**
 * 蓝羽枝剑附魔
 *
 * 血量<=20%时受击减伤（等级×10%）
 */
@AutoRegisterEnchantment(
        id = "blue_feathered_branchsword",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        conflictsWith = {
                EnchantmentCorruptedWingSword.class
        }
)
public class EnchantmentBlueFeatheredBranchsword extends EnchantmentBase {

    /** 参与计算的等级上限（levelLimit 开时） */
    private static final int LEVEL_CAP = 10;
    /**
     * 生效等级的硬上限，不受 levelLimit 开关影响，最多减伤 90%
     * <p>以前 10 级时系数是 0（完全免疫生物伤害），关掉 levelLimit 到 11 级起系数为负，挨打反而加血。</p>
     */
    private static final int MAX_EFFECTIVE_LEVEL = 9;
    /** 每级减伤 */
    private static final float REDUCTION_PER_LEVEL = 0.1F;
    /** 触发的生命比例门槛 */
    private static final float HEALTH_THRESHOLD = 0.2F;

    public EnchantmentBlueFeatheredBranchsword() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsVictim(@Nonnull EnchantmentContext ctx, int level) {
        // 只处理有攻击者的伤害
        if (ctx.getAttacker() == null) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = capLevel(level, LEVEL_CAP);
        effectiveLevel = Math.min(effectiveLevel, MAX_EFFECTIVE_LEVEL);

        // 血量 <= 20%时触发
        if (ctx.getHolder().getHealth() <= ctx.getHolder().getMaxHealth() * HEALTH_THRESHOLD) {
            // 减伤 = 伤害 × 等级 × 10%
            ctx.multiplyDamage(1 - effectiveLevel * REDUCTION_PER_LEVEL);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}