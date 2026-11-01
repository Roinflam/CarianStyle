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
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 阿杜拉月光剑附魔
 *
 * 攻击变为魔法伤害，对目标周围敌人施加冻伤
 * 白天：叠加+1等级，夜晚：叠加+2等级
 */
@AutoRegisterEnchantment(
        id = "adura_moonlight_sword",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        conflictsWith = {
                EnchantmentEpilepsyFire.class,
                EnchantmentEatShit.class,
                EnchantmentHypnoticSmoke.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class
        }
)
public class EnchantmentAduraMoonlightSword extends EnchantmentBase {

    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 冻伤范围半径上限（格），等级再高也不超过它 */
    private static final int MAX_SEARCH_RADIUS = 6;
    /** 一次至多冻伤的目标数 */
    private static final int MAX_TARGETS = 16;
    /** 冻伤持续时间（tick） */
    private static final int FROSTBITE_DURATION = 200;
    /** 冻伤叠加的等级上限（amplifier，9 即 10 级） */
    private static final int FROSTBITE_MAX_AMPLIFIER = 9;

    public EnchantmentAduraMoonlightSword() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 满蓄力攻击转为魔法伤害，并冻伤目标和周围的生物
     * <p>
     * 半径 = 附魔等级，至多 6 格；一次至多 16 个。被打的目标一定吃冻伤，排在最前面不会被上限挤掉；
     * 周围的只波及「别人」：同类（玩家不冻别的玩家）、队友和自己养的宠物不冻。
     * 以前半径能到 10 格、没有数量上限，连队友和自己的宠物一起冻；改在普通优先级结算。
     * </p>
     */
    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        int effectiveLevel = capLevel(level, LEVEL_CAP);

        // 玩家需要满蓄力，非玩家直接触发
        if (!isFullyCharged(attacker)) {
            return;
        }

        // 伤害变为魔法伤害
        if (ctx.getDamageSource() != null) {
            ctx.getDamageSource().setMagicDamage();
        }

        int searchRadius = Math.min(effectiveLevel, MAX_SEARCH_RADIUS);

        List<EntityLivingBase> nearbyEntities = new ArrayList<>(EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                searchRadius,
                entity -> entity != null && (entity.equals(victim) || !isSameKindOrAlly(attacker, entity))
        ));

        // 被打的目标排到最前面，保证它一定吃冻伤
        if (nearbyEntities.remove(victim)) {
            nearbyEntities.add(0, victim);
        }

        boolean isNight = !attacker.world.isDaytime();
        int stackIncrease = isNight ? 2 : 1;
        int initialLevel = isNight ? 1 : 0;

        int hitCount = 0;
        for (EntityLivingBase entity : nearbyEntities) {
            if (hitCount >= MAX_TARGETS) {
                break;
            }

            PotionEffect existingEffect = entity.getActivePotionEffect(CarianStylePotion.FROSTBITE);

            int newLevel;
            if (existingEffect != null) {
                newLevel = Math.min(existingEffect.getAmplifier() + stackIncrease, FROSTBITE_MAX_AMPLIFIER);
            } else {
                newLevel = initialLevel;
            }

            entity.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, FROSTBITE_DURATION, newLevel));
            hitCount++;
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}