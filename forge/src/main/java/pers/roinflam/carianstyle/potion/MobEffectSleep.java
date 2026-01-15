package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 睡眠药水效果
 * <p>
 * 效果：
 * - 移动速度降为0（无法移动）
 * - 无法跳跃
 * - 生物无法设置攻击目标
 * - 无法攻击其他实体
 * - 受到攻击时伤害×2 + 伤害×等级×25%，并解除睡眠
 * - 持续施加失明效果
 * </p>
 */
public class MobEffectSleep extends IconBase {

    public MobEffectSleep(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "sleep");

        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.MOVEMENT_SPEED,
                "5d59080b-eda9-f5b7-1b3c-51568e5b6682",
                -1,
                2
        );
    }

    /**
     * 睡眠状态下无法被设为攻击目标
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingSetAttackTarget(@Nonnull LivingSetAttackTargetEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getTarget() == null) {
            return;
        }

        if (!(evt.getEntityLiving() instanceof EntityLiving)) {
            return;
        }

        EntityLiving entityLiving = (EntityLiving) evt.getEntityLiving();
        if (entityLiving.isPotionActive(this)) {
            entityLiving.setAttackTarget(null);
        }
    }

    /**
     * 睡眠状态下无法攻击
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
        if (attacker.isPotionActive(this)) {
            evt.setCanceled(true);
        }
    }

    /**
     * 睡眠状态下受击：伤害加倍并解除睡眠
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        if (victim.isPotionActive(this)) {
            int amplifier = victim.getActivePotionEffect(this).getAmplifier();
            // 伤害 = 原伤害×2 + 原伤害×等级×25%
            evt.setAmount(evt.getAmount() * 2 + evt.getAmount() * amplifier * 0.25f);
            victim.removePotionEffect(this);
        }
    }

    /**
     * 睡眠状态下无法跳跃
     */
    @SubscribeEvent
    public void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        EntityLivingBase entityLiving = evt.getEntityLiving();
        if (entityLiving.isPotionActive(this)) {
            EntityLivingUtil.setJumped(entityLiving);
        }
    }

    @Override
    public void performEffect(EntityLivingBase entityLivingBaseIn, int amplifier) {
        // 清除生物的攻击目标
        if (entityLivingBaseIn instanceof EntityLiving) {
            ((EntityLiving) entityLivingBaseIn).setAttackTarget(null);
        }
        // 施加失明效果
        entityLivingBaseIn.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 21));
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/sleep.png");
    }
}