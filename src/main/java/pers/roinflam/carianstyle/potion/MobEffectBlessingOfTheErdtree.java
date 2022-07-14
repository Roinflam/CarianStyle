package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;

@Mod.EventBusSubscriber
public class MobEffectBlessingOfTheErdtree extends IconBase {

    public MobEffectBlessingOfTheErdtree(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "blessing_of_the_erdtree");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.MAX_HEALTH, "c407bffa-97df-adf8-51db-5681fdef4b8c", 0.15, 2);
    }

    public static Potion getPotion() {
        return CarianStylePotion.BLESSING_OF_THE_ERDTREE;
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 60 == 0;
    }

    @Override
    public void performEffect(EntityLivingBase entityLivingBaseIn, int amplifier) {
        entityLivingBaseIn.heal((float) (entityLivingBaseIn.getMaxHealth() * (amplifier + 1) * 0.04));
    }

    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/blessing_of_the_erdtree.png");
    }

}
