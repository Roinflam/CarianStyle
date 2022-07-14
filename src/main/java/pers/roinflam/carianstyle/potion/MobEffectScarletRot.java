package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

@Mod.EventBusSubscriber
public class MobEffectScarletRot extends IconBase {

    public MobEffectScarletRot(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "scarlet_rot");
    }

    public static Potion getPotion() {
        return CarianStylePotion.SCARLET_ROT;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase healer = evt.getEntityLiving();
            if (healer.isPotionActive(getPotion())) {
                evt.setAmount((float) (evt.getAmount() * 0.75));
            }
        }
    }

    @Override
    public void performEffect(EntityLivingBase entityLivingBaseIn, int amplifier) {
        if (EntityUtil.getFire(entityLivingBaseIn) <= 0 || RandomUtil.percentageChance(25)) {
            float damage = (float) (entityLivingBaseIn.getHealth() * 0.02 + entityLivingBaseIn.getMaxHealth() * 0.0005);
            damage += damage * amplifier * 0.25;
            entityLivingBaseIn.attackEntityFrom(NewDamageSource.SCARLET_ROT, damage);
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/scarlet_rot.png");
    }
}
