package pers.roinflam.carianstyle.potion.hide;

import pers.roinflam.carianstyle.base.potion.flame.FlameBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 毁灭火焰燃烧药水效果（隐藏）
 * <p>
 * 火焰外观：白色火焰
 * </p>
 */
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