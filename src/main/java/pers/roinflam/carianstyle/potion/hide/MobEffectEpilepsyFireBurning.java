package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import pers.roinflam.carianstyle.base.potion.flame.FlameBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.NetworkRegistryHandler;
import pers.roinflam.carianstyle.utils.Reference;

public class MobEffectEpilepsyFireBurning extends FlameBase {

    public MobEffectEpilepsyFireBurning(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "epilepsy_fire_burning");
    }

    @Override
    public void sendClientCustomPacket(EntityPlayer entityPlayer, int id, boolean add) {
        NetworkRegistryHandler.EpilepsyFireBurning.sendClientCustomPacket(entityPlayer, id, add);
    }

    @Override
    public boolean isAflame(int id) {
        return NetworkRegistryHandler.EpilepsyFireBurning.getEntitiesID().contains(id);
    }

    @Override
    public Potion getPotion() {
        return CarianStylePotion.EPILEPSY_FIRE_BURNING;
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
