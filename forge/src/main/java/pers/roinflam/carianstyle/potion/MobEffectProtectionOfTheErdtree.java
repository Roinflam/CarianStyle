package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 黄金树庇护药水效果
 * <p>
 * 效果：
 * - 非生物来源的伤害（环境伤害）减免20%×(等级+1)
 * - 不对绝对伤害生效
 * </p>
 */
public class MobEffectProtectionOfTheErdtree extends IconBase {

    public MobEffectProtectionOfTheErdtree(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "protection_of_the_erdtree");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        // 排除创造模式伤害、绝对伤害、生物来源伤害
        if (damageSource.canHarmInCreative() || damageSource.isDamageAbsolute()) {
            return;
        }
        if (damageSource.getTrueSource() instanceof EntityLivingBase) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        if (victim.isPotionActive(this)) {
            int amplifier = victim.getActivePotionEffect(this).getAmplifier();
            evt.setAmount(evt.getAmount() * (1 - (amplifier + 1) * 0.2f));
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/protection_of_the_erdtree.png");
    }
}