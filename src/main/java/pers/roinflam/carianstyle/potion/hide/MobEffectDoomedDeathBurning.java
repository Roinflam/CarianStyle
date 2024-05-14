package pers.roinflam.carianstyle.potion.hide;

import pers.roinflam.carianstyle.base.potion.flame.FlameBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;


public class MobEffectDoomedDeathBurning extends FlameBase {

    public MobEffectDoomedDeathBurning(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "doomed_death_burning");
    }

    @Override
    public int getSerialNumber() {
        return 0;
    }

    @Nonnull
    @Override
    protected String getLevelOneName() {
        return Reference.MOD_ID + ":blocks/crimson_flame_layer_0";
    }

    @Nonnull
    @Override
    protected String getLevelTwoName() {
        return Reference.MOD_ID + ":blocks/crimson_flame_layer_1";
    }

}
