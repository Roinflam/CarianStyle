package pers.roinflam.carianstyle.potion.hide;

import pers.roinflam.carianstyle.base.potion.flame.FlameBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;


public class MobEffectDestructionFireBurning extends FlameBase {

    public MobEffectDestructionFireBurning(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "destruction_fire_burning");
    }

    @Override
    public int getSerialNumber() {
        return 2;
    }

    @Nonnull
    @Override
    protected String getLevelOneName() {
        return Reference.MOD_ID + ":blocks/white_flame_layer_0";
    }

    @Nonnull
    @Override
    protected String getLevelTwoName() {
        return Reference.MOD_ID + ":blocks/white_flame_layer_1";
    }

}
