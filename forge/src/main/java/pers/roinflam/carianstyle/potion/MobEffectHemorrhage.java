package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 出血药水效果
 * <p>
 * 效果：
 * - 每tick造成最大生命值×(7%+等级×1%)/30的直接扣血
 * - 可直接致死
 * </p>
 * <p>
 * 性能优化 v3.0：
 * 移除 SynchronizationTask(1) 延迟调用，改为直接在 applyEffectTick 中扣血。
 * 原版 Poison/Wither 效果也直接在 applyEffectTick 中调用 hurt()，
 * 说明在此阶段修改血量是安全的。
 * </p>
 * <p>
 * 消除的开销：50个实体同时出血 = 原来每秒创建1000个匿名任务对象 → 现在0个。
 * </p>
 */
public class MobEffectHemorrhage extends IconBase {

    /**
     * 构造函数
     *
     * @param isBadEffectIn 是否为负面效果
     * @param liquidColorIn 液体颜色
     */
    public MobEffectHemorrhage(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);
    }

    /**
     * 每tick应用出血伤害（直接扣血，不再创建延迟任务）
     *
     * @param entityLivingBaseIn 受影响的实体
     * @param amplifier          效果等级
     */
    @Override
    public void applyEffectTick(@Nonnull LivingEntity entityLivingBaseIn, int amplifier) {
        float damage = entityLivingBaseIn.getMaxHealth() * (0.07f + 0.01f * amplifier) / 30;

        if (entityLivingBaseIn.getHealth() - damage * 2 > 0) {
            entityLivingBaseIn.setHealth(entityLivingBaseIn.getHealth() - damage);
        } else {
            EntityLivingUtil.kill(entityLivingBaseIn, NewDamageSource.hemorrhage(entityLivingBaseIn.level()));
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    @OnlyIn(Dist.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/mob_effect/hemorrhage.png");
    }
}
