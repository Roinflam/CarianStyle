package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;


public class MobEffectDragoncrestGreatshield extends HideBase {

    public MobEffectDragoncrestGreatshield(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "dragoncrest_greatshield");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR, "024ae079-00fa-5f4c-88ca-c88f217a719e", 0.075, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR_TOUGHNESS, "39a196e1-530e-c8ab-93fd-5f3f67d25f85", 0.075, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.MOVEMENT_SPEED, "e864bfe0-57bd-da1c-1c85-881295377cf6", -0.01, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.FLYING_SPEED, "9bed7f27-3367-08be-fe90-5b8605b2df0a", -0.01, 2);
    }

}
