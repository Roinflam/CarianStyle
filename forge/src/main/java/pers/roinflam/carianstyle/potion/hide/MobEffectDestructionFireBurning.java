package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.world.effect.MobEffectCategory;
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
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);
    }

    @Nonnull
    @Override
    protected String getLevelOneName() {
        return Reference.MOD_ID + ":block/white_flame_layer_0";
    }

    @Nonnull
    @Override
    protected String getLevelTwoName() {
        return Reference.MOD_ID + ":block/white_flame_layer_1";
    }
}