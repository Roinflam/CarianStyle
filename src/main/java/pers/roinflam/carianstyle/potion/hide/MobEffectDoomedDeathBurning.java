package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import pers.roinflam.carianstyle.base.potion.flame.FlameBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.NetworkRegistryHandler;
import pers.roinflam.carianstyle.utils.Reference;

public class MobEffectDoomedDeathBurning extends FlameBase {

    public MobEffectDoomedDeathBurning(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "doomed_death_burning");
    }

    @Override
    public void sendClientCustomPacket(EntityPlayer entityPlayer, int id, boolean add) {
        NetworkRegistryHandler.DoomeDeathBurning.sendClientCustomPacket(entityPlayer, id, add);
    }

    @Override
    public boolean isAflame(int id) {
        return NetworkRegistryHandler.DoomeDeathBurning.getEntitiesID().contains(id);
    }

    @Override
    public Potion getPotion() {
        return CarianStylePotion.DOOMED_DEATH_BURNING;
    }

    @Override
    protected String getLevelOneName() {
        return Reference.MOD_ID + ":blocks/crimson_flame_layer_0";
    }

    @Override
    protected String getLevelTwoName() {
        return Reference.MOD_ID + ":blocks/crimson_flame_layer_1";
    }

}
