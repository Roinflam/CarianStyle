//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package pers.roinflam.carianstyle.base.potion.icon;

import javax.annotation.Nonnull;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.PotionBase;

public abstract class IconBase extends PotionBase {
    protected IconBase(@Nonnull MobEffectCategory category, int liquidColor) {
        super(category, liquidColor);
    }

    @Nonnull
    @OnlyIn(Dist.CLIENT)
    protected abstract ResourceLocation getIconTexture();
}
