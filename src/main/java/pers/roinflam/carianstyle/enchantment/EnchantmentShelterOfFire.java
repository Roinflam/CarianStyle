package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;

/**
 * 火焰庇护附魔
 *
 * 护甲附魔，着火时减伤并回血
 * 着火时：
 * - 受到伤害减少 2% × 等级（多件盔甲各自生效、叠乘；单件 50 级时完全免疫）
 * - 每秒恢复 0.1% × 等级 最大生命值
 */
@AutoRegisterEnchantment(
        id = "shelter_of_fire",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
public class EnchantmentShelterOfFire extends EnchantmentBase {

    // 每级（按件）减伤比例
    private static final float REDUCTION_PER_LEVEL = 0.02F;
    // 每级（按件）每次回复的最大生命值比例
    private static final float HEAL_PER_LEVEL = 0.001F;
    // 回血间隔（tick）
    private static final int HEAL_INTERVAL_TICKS = 20;
    // 单件等级上限（开了等级限制时）
    private static final int LEVEL_CAP = 10;

    public EnchantmentShelterOfFire() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 着火时减伤
     * <p>
     * 按件结算：每件盔甲各自乘一次 (1 - 该件等级×2%)，多件是叠乘；单件减伤到 100% 才完全免疫。
     * 以前是全身等级加起来再一次性减（加算），四件 5 级是 40%，现在是 1-0.9^4≈34.4%。
     * </p>
     */
    @Override
    protected void onDamageAsVictimLow(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase victim = ctx.getHolder();

        // 必须着火
        if (EntityUtil.getFire(victim) <= 0) {
            return;
        }

        float damageReduction = capLevel(level, LEVEL_CAP) * REDUCTION_PER_LEVEL;
        if (damageReduction >= 1.0F) {
            ctx.cancelEvent();
        } else {
            ctx.multiplyDamage(1.0F - damageReduction);
        }
    }

    /**
     * 着火时回血
     * <p>
     * 每秒回一次（每件盔甲各回一份，总量等于全身等级和），以前是每 tick 回 1/20，
     * 每 tick 都会触发一次治疗事件。
     * </p>
     */
    @Override
    protected void onPlayerTick(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase entity = ctx.getHolder();

        if (EntityUtil.getFire(entity) <= 0 || !entity.isEntityAlive()) {
            return;
        }

        if (entity.ticksExisted % HEAL_INTERVAL_TICKS != 0) {
            return;
        }

        float healAmount = entity.getMaxHealth() * capLevel(level, LEVEL_CAP) * HEAL_PER_LEVEL;
        if (healAmount > 0) {
            entity.heal(healAmount);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}