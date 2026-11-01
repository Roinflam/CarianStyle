package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;

import javax.annotation.Nonnull;

/**
 * 出血药水效果
 * <p>
 * 效果：
 * - 每tick造成最大生命值×(7%+等级×1%)/30的直接扣血
 * - 可直接致死
 * </p>
 */
public class MobEffectHemorrhage extends IconBase {

    public MobEffectHemorrhage(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "hemorrhage");
    }

    /** 致死一击的伤害（倍最大生命） */
    private static final float LETHAL_DAMAGE_MULTIPLIER = 10.0F;

    @Override
    public void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
        // 扣血与致死判定只在服务端做
        if (entityLivingBaseIn.world.isRemote) {
            return;
        }

        // 回溯无敌期间不掉血：直扣血量拦不到，致死那一击的 10 倍最大生命还会被算进储存伤害
        if (EnchantmentTimeReversal.isReversing(entityLivingBaseIn)) {
            return;
        }

        float damage = entityLivingBaseIn.getMaxHealth() * (0.07f + 0.01f * amplifier) / 30;

        if (entityLivingBaseIn.getHealth() - damage * 2 > 0) {
            // 常规掉血：直扣血量，不走伤害管线（每秒 20 次走 attackEntityFrom 会让受击类附魔各触发 20 次）
            entityLivingBaseIn.setHealth(entityLivingBaseIn.getHealth() - damage);
        } else {
            // 致死一击走伤害管线，不死图腾挡得住；出血伤害是绝对伤害，抗性提升 V 挡不住
            entityLivingBaseIn.attackEntityFrom(NewDamageSource.HEMORRHAGE,
                    entityLivingBaseIn.getMaxHealth() * LETHAL_DAMAGE_MULTIPLIER);
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/hemorrhage.png");
    }
}