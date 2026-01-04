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

    public EnchantmentAduraMoonlightSword() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 玩家需要刚挥剑，非玩家直接触发
        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        // 伤害变为魔法伤害
        if (ctx.getDamageSource() != null) {
            ctx.getDamageSource().setMagicDamage();
        }

        // 获取目标周围的敌人（范围=等级）
        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                effectiveLevel,
                entity -> !entity.equals(attacker)
        );

        // 施加冻伤效果
        boolean isNight = !attacker.world.isDaytime();
        int stackIncrease = isNight ? 2 : 1;
        int initialLevel = isNight ? 1 : 0;

        for (EntityLivingBase entity : nearbyEntities) {
            PotionEffect existingEffect = entity.getActivePotionEffect(CarianStylePotion.FROSTBITE);

            int newLevel;
            if (existingEffect != null) {
                newLevel = Math.min(existingEffect.getAmplifier() + stackIncrease, 9);
            } else {
                newLevel = initialLevel;
            }

            entity.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, 200, newLevel));
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}