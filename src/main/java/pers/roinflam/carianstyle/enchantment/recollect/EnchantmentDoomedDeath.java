package pers.roinflam.carianstyle.enchantment.recollect;

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
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;
import pers.roinflam.carianstyle.utils.util.BossUtil;

import javax.annotation.Nonnull;

/**
 * 命定之死
 * <p>
 * 满蓄力命中后（非玩家持有不要求），目标在 2 秒内燃烧「本次伤害 ×20% + 目标最大生命 ×2%」，
 * 伤害逐 tick 递增；同时 45 秒内最大生命 -10%。对 BOSS 燃烧伤害减半（最大生命降低照常）。
 * </p>
 * <p>
 * 本次伤害取护甲和抗性减免之前的值（伤害事件 LivingHurt），烧伤按目标最大生命算、剩不到两跳时直接处决。
 * 以前取的是减免之后的值、按当前生命的 10% 烧：抗性提升 V 下那一下是 0，剩下的部分越烧越少，
 * 永远到不了致死线，带抗性的目标怎么烧都不死。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "doomed_death",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
public class EnchantmentDoomedDeath extends EnchantmentBase {

    /** 燃烧时长（tick） */
    private static final int DOT_DURATION = 40;
    /** 起始延迟（tick） */
    private static final int DOT_DELAY = 5;
    /** 燃烧火焰显示秒数（与燃烧同时结束） */
    private static final int BURNING_SECONDS = 2;
    /** 最大生命降低持续秒数 */
    private static final int DOOM_SECONDS = 45;
    /** 按本次伤害计的燃烧比例 */
    private static final float DAMAGE_RATIO = 0.2F;
    /** 按目标最大生命计的燃烧比例 */
    private static final float VICTIM_MAX_HEALTH_RATIO = 0.02F;
    /** 对 BOSS 的燃烧倍率 */
    private static final float BOSS_MULTIPLIER = 0.5F;

    public EnchantmentDoomedDeath() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase victim = ctx.getVictim();
        if (victim == null || victim.world.isRemote) {
            return;
        }

        if (!isFullyCharged(ctx.getHolder())) {
            return;
        }

        float multiplier = BossUtil.isBoss(victim) ? BOSS_MULTIPLIER : 1.0F;

        // 先按挂减益之前的最大生命算燃烧总量，再挂最大生命降低
        float totalDamage = (ctx.getDamage() * DAMAGE_RATIO + victim.getMaxHealth() * VICTIM_MAX_HEALTH_RATIO) * multiplier;
        float baseDamagePerTick = totalDamage / DOT_DURATION;

        victim.addPotionEffect(new PotionEffect(CarianStylePotion.DOOMED_DEATH_BURNING, BURNING_SECONDS * 20 + 5, 0));
        victim.addPotionEffect(new PotionEffect(CarianStylePotion.DOOMED_DEATH, DOOM_SECONDS * 20 + 5, 0));

        // 递增伤害：base × 0.3 + base × elapsed / ((duration + 1) / 2) × 0.7。
        // elapsed 从 1 数到 duration，平均正好是 (duration + 1) / 2，所以总量恰好是 base × duration
        float halfSpan = (DOT_DURATION + 1) / 2.0F;
        DamageOverTimeManager.applyScaling(
                victim,
                baseDamagePerTick,
                DOT_DURATION,
                DOT_DELAY,
                ctx.getDamageSource(),
                true,
                (base, elapsed) -> base * 0.3f + base * elapsed / halfSpan * 0.7f
        );
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}
