package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;

@Mod.EventBusSubscriber
public class MobEffectGoldenVow extends IconBase {

    public MobEffectGoldenVow(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "golden_vow");
    }

    public static Potion getPotion() {
        return CarianStylePotion.GOLDEN_VOW;
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                if (hurter.isPotionActive(getPotion())) {
                    int amplifier = hurter.getActivePotionEffect(getPotion()).getAmplifier();
                    evt.setAmount((float) (evt.getAmount() - evt.getAmount() * (amplifier + 1) * 0.1));
                }
                if (damageSource.getTrueSource() instanceof EntityLivingBase) {
                    EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                    if (attacker.isPotionActive(getPotion())) {
                        int amplifier = attacker.getActivePotionEffect(getPotion()).getAmplifier();
                        evt.setAmount((float) (evt.getAmount() + evt.getAmount() * (amplifier + 1) * 0.15));
                    }
                }
            }
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/golden_vow.png");
    }
}
