package pers.roinflam.carianstyle.enchantment.law;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentDarkAbandonedChild;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

import javax.annotation.Nonnull;

/**
 * 星律附魔
 * <p>
 * 夜晚效果：
 * - 受到的恢复效果提高50%
 * - 提高25%移动速度
 * - 造成伤害或受到物理伤害时，目标/来源受到持续10秒的1级冻伤效果
 * - 若冻伤已存在则提升1级，最高10级
 * - 对已冻伤的目标造成的魔法伤害提高[冻伤等级]*7.5%
 * </p>
 * <p>
 * 冻伤是真药水（{@code CarianStylePotion.FROSTBITE}），移速走 dynamicattr 的 {@link DynamicAttributes#SPEED_BOOST}。
 * 1.12.2 版：「夜晚」读服务端世界的 {@code isDaytime()}（{@link EnchantmentContext#isDaytime}），
 * 判断兼容的附魔分类按类全名截包名（{@link #packageNameOf}），原版附魔在 1.12.2 没有包信息。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "stars_law",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.LAW,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class
        }
)
public class EnchantmentStarsLaw extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "stars_law";

    /**
     * 施加冻伤的持续时间（tick）
     * <p>默认 200，允许范围 20 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle FROSTBITE_DURATION =
            EnchantmentValues.define(VALUE_ID, "frostbite_duration",
                    200, 20, 6000);

    /**
     * 冻伤可叠加到的最高层数
     * <p>默认 10，允许范围 1 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle MAX_FROSTBITE_LEVEL =
            EnchantmentValues.define(VALUE_ID, "max_frostbite_level",
                    10, 1, 127);

    /**
     * 提供的速度效果等级（1 级 = 移速 +1%，0 表示不加速）
     * <p>默认 25，允许范围 0 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle SPEED_BOOST_LEVEL =
            EnchantmentValues.define(VALUE_ID, "speed_boost_level",
                    25, 0, 127);

    /**
     * 每层冻伤提供的额外伤害倍率
     * <p>默认 0.075，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_STACK =
            EnchantmentValues.define(VALUE_ID, "damage_per_stack",
                    0.075D, 0.0D, 2.0D);

    /**
     * 治疗量的倍率
     * <p>默认 1.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "heal_multiplier",
                    1.5D, 0.0D, 10.0D);

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentStarsLaw() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        EntityLivingBase victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        PotionEffect frostbite = victim.getActivePotionEffect(CarianStylePotion.FROSTBITE.get());
        // 如果目标已有冻伤效果
        if (frostbite != null) {
            // 对冻伤目标的魔法伤害加成
            if (ctx.isMagicDamage()) {
                int frostbiteLevel = frostbite.getAmplifier();
                // 伤害加成 = 当前伤害 * (冻伤等级 + 1) * 7.5%
                float bonusDamage = ctx.getDamage() * (frostbiteLevel + 1) * (float) DAMAGE_PER_STACK.get();
                ctx.addDamage(bonusDamage);
            }

            // 提升冻伤等级（最高10级，即amplifier=9）
            int currentLevel = frostbite.getAmplifier();
            int newLevel = Math.min(currentLevel + 1, MAX_FROSTBITE_LEVEL.getInt() - 1);
            ctx.addPotionToOpponent(CarianStylePotion.FROSTBITE.get(), FROSTBITE_DURATION.getInt(), newLevel);
        } else {
            // 添加1级冻伤（amplifier=0表示1级）
            ctx.addPotionToOpponent(CarianStylePotion.FROSTBITE.get(), FROSTBITE_DURATION.getInt(), 0);
        }
    }

    @Override
    protected void onHurtAsVictim(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        if (ctx.isMagicDamage()) {
            return;
        }

        EntityLivingBase attacker = ctx.getAttacker();
        if (attacker == null) {
            return;
        }

        PotionEffect frostbite = attacker.getActivePotionEffect(CarianStylePotion.FROSTBITE.get());
        // 如果攻击者已有冻伤效果
        if (frostbite != null) {
            // 提升冻伤等级（最高10级，即amplifier=9）
            int currentLevel = frostbite.getAmplifier();
            int newLevel = Math.min(currentLevel + 1, MAX_FROSTBITE_LEVEL.getInt() - 1);
            attacker.addPotionEffect(new PotionEffect(
                    CarianStylePotion.FROSTBITE.get(),
                    FROSTBITE_DURATION.getInt(),
                    newLevel
            ));
        } else {
            // 添加1级冻伤（amplifier=0表示1级）
            attacker.addPotionEffect(new PotionEffect(
                    CarianStylePotion.FROSTBITE.get(),
                    FROSTBITE_DURATION.getInt(),
                    0
            ));
        }
    }

    @Override
    protected void onHeal(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        float currentHeal = ctx.getHealAmount();
        ctx.setHealAmount(currentHeal * (float) HEAL_MULTIPLIER.get());
    }

    @Override
    protected void onPlayerTick(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        // 添加速度提升效果（createInstance 收的是 amplifier，等级要减 1）
        int speedLevel = SPEED_BOOST_LEVEL.getInt();
        if (speedLevel > 0) {
            DynamicAttributeManager.apply(ctx.getHolder(),
                    DynamicAttributes.SPEED_BOOST.createInstance(2, speedLevel - 1));
        }
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
        return packageNameOf(ench).contains("recollect")
                || packageNameOf(ench).contains("law");
    }

    private boolean isDarkAbandonedChild(Enchantment ench) {
        return ench instanceof EnchantmentDarkAbandonedChild;
    }
}
