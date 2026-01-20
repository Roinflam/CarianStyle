package pers.roinflam.carianstyle.enchantment.law;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentDarkAbandonedChild;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.init.CarianStylePotion;

/**
 * 星律附魔
 * <p>
 * ⚠️ 注意：此类使用了FROSTBITE（冻伤）效果，需要先在 DynamicAttributes 中定义该效果
 * <p>
 * 夜晚效果：
 * - 受到的恢复效果提高50%
 * - 提高25%移动速度
 * - 造成伤害或受到物理伤害时，目标/来源受到持续10秒的1级冻伤效果
 * - 若冻伤已存在则提升1级，最高10级
 * - 对已冻伤的目标造成的魔法伤害提高[冻伤等级]*7.5%
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "stars_law",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.LAW,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class
        }
)
public class EnchantmentStarsLaw extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;
    private static final int FROSTBITE_DURATION = 200;
    private static final int MAX_FROSTBITE_LEVEL = 10;
    private static final int SPEED_BOOST_LEVEL = 25;

    public EnchantmentStarsLaw() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLow(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        LivingEntity victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        // 如果目标已有冻伤效果
        if (victim.hasEffect(CarianStylePotion.FROSTBITE.get())) {
            // 对冻伤目标的魔法伤害加成
            if (ctx.isMagicDamage()) {
                int frostbiteLevel = victim.getEffect(CarianStylePotion.FROSTBITE.get()).getAmplifier();
                // 伤害加成 = 当前伤害 * (冻伤等级 + 1) * 7.5%
                float bonusDamage = ctx.getDamage() * (frostbiteLevel + 1) * 0.075f;
                ctx.addDamage(bonusDamage);
            }

            // 提升冻伤等级（最高10级，即amplifier=9）
            int currentLevel = victim.getEffect(CarianStylePotion.FROSTBITE.get()).getAmplifier();
            int newLevel = Math.min(currentLevel + 1, MAX_FROSTBITE_LEVEL - 1);
            ctx.addPotionToOpponent(CarianStylePotion.FROSTBITE.get(), FROSTBITE_DURATION, newLevel);
        } else {
            // 添加1级冻伤（amplifier=0表示1级）
            ctx.addPotionToOpponent(CarianStylePotion.FROSTBITE.get(), FROSTBITE_DURATION, 0);
        }
    }

    @Override
    protected void onHurtAsVictim(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        if (ctx.isMagicDamage()) {
            return;
        }

        LivingEntity attacker = ctx.getAttacker();
        if (attacker == null) {
            return;
        }

        // 如果攻击者已有冻伤效果
        if (attacker.hasEffect(CarianStylePotion.FROSTBITE.get())) {
            // 提升冻伤等级（最高10级，即amplifier=9）
            int currentLevel = attacker.getEffect(CarianStylePotion.FROSTBITE.get()).getAmplifier();
            int newLevel = Math.min(currentLevel + 1, MAX_FROSTBITE_LEVEL - 1);
            attacker.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    CarianStylePotion.FROSTBITE.get(),
                    FROSTBITE_DURATION,
                    newLevel
            ));
        } else {
            // 添加1级冻伤（amplifier=0表示1级）
            attacker.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    CarianStylePotion.FROSTBITE.get(),
                    FROSTBITE_DURATION,
                    0
            ));
        }
    }

    @Override
    protected void onHeal(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        float currentHeal = ctx.getHealAmount();
        ctx.setHealAmount(currentHeal * 1.5f);
    }

    @Override
    protected void onPlayerTick(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        // 添加速度提升效果
        DynamicAttributeManager.apply(ctx.getHolder(),
                DynamicAttributes.SPEED_BOOST.createInstance(2, SPEED_BOOST_LEVEL));
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(Enchantment ench) {
        if (isRecollectEnchantment(ench) && !isDarkAbandonedChild(ench)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    private boolean isRecollectEnchantment(Enchantment ench) {
        return ench.getClass().getPackage().getName().contains("recollect")
                || ench.getClass().getPackage().getName().contains("law");
    }

    private boolean isDarkAbandonedChild(Enchantment ench) {
        return ench instanceof EnchantmentDarkAbandonedChild;
    }
}