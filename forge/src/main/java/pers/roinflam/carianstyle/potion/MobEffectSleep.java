package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
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
@Mod.EventBusSubscriber
public class MobEffectSleep extends IconBase {

    public MobEffectSleep(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                "5d59080b-eda9-f5b7-1b3c-51568e5b6682",
                -1,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    /**
     * 睡眠状态下无法被设为攻击目标
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingChangeTarget(@Nonnull LivingChangeTargetEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getNewTarget() == null) {
            return;
        }

        if (!(evt.getEntity() instanceof Mob)) {
            return;
        }

        Mob entityLiving = (Mob) evt.getEntity();
        if (entityLiving.hasEffect(CarianStylePotion.SLEEP.get())) {
            evt.setCanceled(true);
        }
    }

    /**
     * 睡眠状态下无法攻击
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) evt.getSource().getEntity();
        if (attacker.hasEffect(CarianStylePotion.SLEEP.get())) {
            evt.setCanceled(true);
        }
    }

    /**
     * 睡眠状态下受击：伤害加倍并解除睡眠
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        if (victim.hasEffect(CarianStylePotion.SLEEP.get())) {
            int amplifier = victim.getEffect(CarianStylePotion.SLEEP.get()).getAmplifier();
            // 伤害 = 原伤害×2 + 原伤害×等级×25%
            evt.setAmount(evt.getAmount() * 2 + evt.getAmount() * amplifier * 0.25f);
            victim.removeEffect(CarianStylePotion.SLEEP.get());
        }
    }

    /**
     * 睡眠状态下无法跳跃
     */
    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entityLiving = evt.getEntity();
        if (entityLiving.hasEffect(CarianStylePotion.SLEEP.get())) {
            EntityLivingUtil.setJumped(entityLiving);
        }
    }

    @Override
    public void applyEffectTick(@Nonnull LivingEntity entityLivingBaseIn, int amplifier) {
        // 清除生物的攻击目标
        if (entityLivingBaseIn instanceof Mob) {
            ((Mob) entityLivingBaseIn).setTarget(null);
        }
        // 施加失明效果
        entityLivingBaseIn.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 21));
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    @OnlyIn(Dist.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/mob_effect/sleep.png");
    }
}