package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import pers.roinflam.carianstyle.base.potion.flame.FlameBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.NetworkRegistryHandler;
import pers.roinflam.carianstyle.utils.Reference;

public class MobEffectDestructionFireBurning extends FlameBase {

    public MobEffectDestructionFireBurning(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "destruction_fire_burning");
    }

    @Override
    public Potion getPotion() {
        return CarianStylePotion.DESTRUCTION_FIRE_BURNING;
    }

    @Override
    public void sendClientCustomPacket(EntityPlayer entityPlayer, int id, boolean add) {
        NetworkRegistryHandler.DestructionFireBurning.sendClientCustomPacket(entityPlayer, id, add);
    }

    @Override
    public boolean isAflame(int id) {
        return NetworkRegistryHandler.DestructionFireBurning.getEntitiesID().contains(id);
    }

    @Override
    protected String getLevelOneName() {
        return Reference.MOD_ID + ":blocks/white_flame_layer_0";
    }

    @Override
    protected String getLevelTwoName() {
        return Reference.MOD_ID + ":blocks/white_flame_layer_1";
    }

}
