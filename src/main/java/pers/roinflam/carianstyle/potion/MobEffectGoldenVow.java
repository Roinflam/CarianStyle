package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 黄金誓约药水效果
 * <p>
 * 效果：
 * - 受到伤害时减免10%×(等级+1)，最高100%
 * - 造成伤害时增加15%×(等级+1)
 * </p>
 */
@EventBusSubscriber
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
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (damageSource.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // 受击者减伤
        if (victim.hasEffect(pers.roinflam.carianstyle.init.CarianStylePotion.GOLDEN_VOW)) {
            int amplifier = victim.getEffect(pers.roinflam.carianstyle.init.CarianStylePotion.GOLDEN_VOW).getAmplifier();
            // 倍率不能小于 0：效果等级还可能被野兽健壮这类附魔抬高，负的伤害量会被后面的增减伤带歪
            evt.setAmount(evt.getAmount() * Math.max(0.0f, 1 - (amplifier + 1) * 0.1f));
        }

        // 攻击者增伤
        if (damageSource.getEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) damageSource.getEntity();
            if (attacker.hasEffect(pers.roinflam.carianstyle.init.CarianStylePotion.GOLDEN_VOW)) {
                int amplifier = attacker.getEffect(pers.roinflam.carianstyle.init.CarianStylePotion.GOLDEN_VOW).getAmplifier();
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
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "textures/mob_effect/golden_vow.png");
    }
}