package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.world.effect.MobEffectCategory;
import pers.roinflam.carianstyle.base.potion.flame.FlameBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 注定死亡燃烧药水效果（隐藏）
 */
public class MobEffectDoomedDeathBurning extends FlameBase {

    public MobEffectDoomedDeathBurning(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);
        System.out.println("[CarianStyle-调试] MobEffectDoomedDeathBurning 构造完成，类: " + this.getClass().getName());
    }

    @Nonnull
    @Override
    protected String getLevelOneName() {
        return Reference.MOD_ID + ":block/crimson_flame_layer_0";
    }

    @Nonnull
    @Override
    protected String getLevelTwoName() {
        return Reference.MOD_ID + ":block/crimson_flame_layer_1";
    }
}