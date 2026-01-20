package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 黄金誓约药水效果
 * <p>
 * 效果：
 * - 受到伤害时减免10%×(等级+1)
 * - 造成伤害时增加15%×(等级+1)
 * </p>
 */
@Mod.EventBusSubscriber  // ⭐ 添加这个注解！
public class MobEffectGoldenVow extends IconBase {

    public MobEffectGoldenVow(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);
    }

    /**
     * 处理伤害增减效果
     *
     * @param evt 受伤事件
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {  // ⭐ 改为static方法
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (damageSource.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // 受击者减伤
        if (victim.hasEffect(pers.roinflam.carianstyle.init.CarianStylePotion.GOLDEN_VOW.get())) {
            int amplifier = victim.getEffect(pers.roinflam.carianstyle.init.CarianStylePotion.GOLDEN_VOW.get()).getAmplifier();
            evt.setAmount(evt.getAmount() * (1 - (amplifier + 1) * 0.1f));
        }

        // 攻击者增伤
        if (damageSource.getEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) damageSource.getEntity();
            if (attacker.hasEffect(pers.roinflam.carianstyle.init.CarianStylePotion.GOLDEN_VOW.get())) {
                int amplifier = attacker.getEffect(pers.roinflam.carianstyle.init.CarianStylePotion.GOLDEN_VOW.get()).getAmplifier();
                evt.setAmount(evt.getAmount() * (1 + (amplifier + 1) * 0.15f));
            }
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
        return new ResourceLocation(Reference.MOD_ID, "textures/mob_effect/golden_vow.png");
    }
}