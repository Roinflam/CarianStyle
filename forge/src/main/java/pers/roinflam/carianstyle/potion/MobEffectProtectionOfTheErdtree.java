package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 黄金树庇护药水效果
 * <p>
 * 效果：
 * - 非生物来源的伤害(环境伤害)减免20%×(等级+1)
 * - 不对绝对伤害生效
 * </p>
 */
@Mod.EventBusSubscriber  // ⭐ 添加这个注解！
public class MobEffectProtectionOfTheErdtree extends IconBase {

    public MobEffectProtectionOfTheErdtree(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);
    }

    /**
     * 处理环境伤害减免
     *
     * @param evt 伤害事件
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {  // ⭐ 改为static方法
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        // 排除创造模式伤害、绝对伤害
        if (damageSource.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        // 排除生物来源伤害
        if (damageSource.getEntity() instanceof LivingEntity) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        if (victim.hasEffect(pers.roinflam.carianstyle.init.CarianStylePotion.PROTECTION_OF_THE_ERDTREE.get())) {
            int amplifier = victim.getEffect(pers.roinflam.carianstyle.init.CarianStylePotion.PROTECTION_OF_THE_ERDTREE.get()).getAmplifier();
            evt.setAmount(evt.getAmount() * (1 - (amplifier + 1) * 0.2f));
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
        return new ResourceLocation(Reference.MOD_ID, "textures/mob_effect/protection_of_the_erdtree.png");
    }
}