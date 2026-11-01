package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

import javax.annotation.Nonnull;

/**
 * 黑焰刃附魔
 *
 * 攻击时施加灭绝火焰燃烧效果
 * 持续伤害：伤害×等级×0.15/100 每tick，持续100tick
 */
@AutoRegisterEnchantment(
        id = "black_flame_blade",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        forceTreasure = true,
        conflictsWith = {
                EnchantmentInvisibleWeapon.class
        }
)
public class EnchantmentBlackFlameBlade extends EnchantmentBase {

    /** 燃烧时长（tick） */
    private static final int DOT_DURATION = 100;
    /** 起始延迟（tick） */
    private static final int DOT_DELAY = 5;
    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 燃烧火焰显示秒数 */
    private static final int BURNING_SECONDS = 5;
    /** 每级燃烧比例（按本次伤害） */
    private static final float DAMAGE_PER_LEVEL = 0.15F;

    public EnchantmentBlackFlameBlade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 目标在 5 秒内燃烧「本次伤害 ×15% × 等级」
     * <p>
     * 本次伤害取护甲和抗性减免之前的值（以前取减免之后的，抗性提升 V 下是 0，一点都烧不掉）；
     * 燃烧剩不到两跳时直接处决。
     * </p>
     */
    @Override
    protected void onHurtAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        int effectiveLevel = capLevel(level, LEVEL_CAP);

        victim.addPotionEffect(new PotionEffect(CarianStylePotion.DESTRUCTION_FIRE_BURNING, BURNING_SECONDS * 20 + 5, 0));

        float damagePerTick = ctx.getDamage() * effectiveLevel * DAMAGE_PER_LEVEL / DOT_DURATION;
        DamageOverTimeManager.applyLinear(victim, damagePerTick, DOT_DURATION, DOT_DELAY, ctx.getDamageSource(), true);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}