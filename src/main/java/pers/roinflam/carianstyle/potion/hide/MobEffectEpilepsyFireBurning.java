package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.flame.FlameBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.NetworkRegistryHandler;
import pers.roinflam.carianstyle.utils.Reference;

@Mod.EventBusSubscriber
public class MobEffectEpilepsyFireBurning extends FlameBase {

    public MobEffectEpilepsyFireBurning(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "epilepsy_fire_burning");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHeal(LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase healer = evt.getEntityLiving();
            if (healer.isPotionActive(this)) {
                evt.setAmount((float) (evt.getAmount() * 0.1));
            }
        }
    }

    @Override
    public int getSerialNumber() {
        return 1;
    }

    @Override
    protected String getLevelOneName() {
        return Reference.MOD_ID + ":blocks/yellow_flame_layer_0";
    }

    @Override
    protected String getLevelTwoName() {
        return Reference.MOD_ID + ":blocks/yellow_flame_layer_1";
    }

}
