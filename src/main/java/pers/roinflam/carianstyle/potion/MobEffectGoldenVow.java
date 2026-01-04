package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
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
public class MobEffectGoldenVow extends IconBase {

    public MobEffectGoldenVow(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "golden_vow");
    }

    @SubscribeEvent
    public void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (damageSource.canHarmInCreative()) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        // 受击者减伤
        if (victim.isPotionActive(this)) {
            int amplifier = victim.getActivePotionEffect(this).getAmplifier();
            evt.setAmount(evt.getAmount() * (1 - (amplifier + 1) * 0.1f));
        }

        // 攻击者增伤
        if (damageSource.getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
            if (attacker.isPotionActive(this)) {
                int amplifier = attacker.getActivePotionEffect(this).getAmplifier();
                evt.setAmount(evt.getAmount() * (1 + (amplifier + 1) * 0.15f));
            }
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/golden_vow.png");
    }
}