package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.potion.Potion;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;

public class MobEffectDoomedDeath extends HideBase {

    public MobEffectDoomedDeath(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "doomed_death");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.MAX_HEALTH, "58993fe2-d11c-2b97-4958-6a8304ff8ad8", -0.25, 2);
    }

    public static Potion getPotion() {
        return CarianStylePotion.DOOMED_DEATH;
    }

}
