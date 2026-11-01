package pers.roinflam.carianstyle.enchantment.law;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentDarkAbandonedChild;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;

/**
 * 星律附魔
 *
 * 夜晚效果：
 * - 攻击时给敌人叠加冻伤，敌人有冻伤时魔法伤害增加
 * - 治疗量+50%
 * - 持续获得速度提升
 */
/**
 * 群星律法附魔
 * <p>
 * 夜晚时：满蓄力造成伤害或受到物理伤害时对对方施加 1 级冻伤（10 秒，已有则 +1 级，至多 10 级）；
 * 满蓄力攻击已冻伤的目标时魔法伤害 +冻伤等级×7.5%；治疗量 ×1.5；移速 +25%。
 * </p>
 * <p>
 * 以前没有「受到物理伤害时给对方上冻伤」这一半；移速给的是 26 级速度提升（+26%），现在是 25 级。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "stars_law",
        category = EnchantmentCategory.LAW,
        rarity = EnchantmentRarity.VERY_RARE,
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class
        }
)
public class EnchantmentStarsLaw extends EnchantmentBase {

    // 追忆类附魔通用的附魔难度
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /** 冻伤持续时间（tick） */
    private static final int FROSTBITE_DURATION = 200;
    /** 冻伤等级上限 */
    private static final int MAX_FROSTBITE_LEVEL = 10;
    /** 每级冻伤给魔法伤害的加成 */
    private static final float DAMAGE_PER_STACK = 0.075F;
    /** 夜晚治疗倍率 */
    private static final float HEAL_MULTIPLIER = 1.5F;
    /** 夜晚速度提升等级（每级 +1% 移速） */
    private static final int SPEED_BOOST_LEVEL = 25;

    public EnchantmentStarsLaw() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        if (!isFullyCharged(ctx.getHolder())) {
            return;
        }

        EntityLivingBase victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        if (victim.isPotionActive(CarianStylePotion.FROSTBITE)) {
            if (ctx.isMagicDamage()) {
                int frostbiteLevel = victim.getActivePotionEffect(CarianStylePotion.FROSTBITE).getAmplifier();
                float bonusDamage = ctx.getDamage() * (frostbiteLevel + 1) * DAMAGE_PER_STACK;
                ctx.addDamage(bonusDamage);
            }
        }
        addFrostbiteStack(victim);
    }

    /**
     * 夜晚受到非魔法伤害时，给伤害来源的生物叠一层冻伤
     */
    @Override
    protected void onHurtAsVictim(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        if (ctx.isMagicDamage()) {
            return;
        }

        EntityLivingBase attacker = ctx.getAttacker();
        if (attacker == null || attacker == ctx.getHolder()) {
            return;
        }

        addFrostbiteStack(attacker);
    }

    /**
     * 叠一层冻伤：没有就上 1 级，有就 +1 级（至多 10 级），时长刷新为 10 秒
     *
     * @param target 目标
     */
    private static void addFrostbiteStack(@Nonnull EntityLivingBase target) {
        PotionEffect existing = target.getActivePotionEffect(CarianStylePotion.FROSTBITE);
        int newLevel = existing == null ? 0 : Math.min(existing.getAmplifier() + 1, MAX_FROSTBITE_LEVEL - 1);
        target.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, FROSTBITE_DURATION, newLevel));
    }

    @Override
    protected void onHeal(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        float currentHeal = ctx.getHealAmount();
        ctx.setHealAmount(currentHeal * HEAL_MULTIPLIER);
    }

    @Override
    protected void onPlayerTick(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        // 效果等级 = amplifier + 1，25 级要传 24
        ctx.addPotionToHolder(CarianStylePotion.SPEED_BOOST, 2, SPEED_BOOST_LEVEL - 1);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        // 与其他追忆类附魔冲突（暗弃子除外）
        if (isRecollectEnchantment(ench) &&
                !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkAbandonedChild.class))) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    /**
     * 判断是否是追忆类附魔
     */
    private boolean isRecollectEnchantment(Enchantment ench) {
        // 这里需要根据实际的追忆类附魔列表来判断
        // 简化处理：通过包名或注解来判断
        return ench.getClass().getPackage().getName().contains("recollect")
                || ench.getClass().getPackage().getName().contains("law");
    }
}