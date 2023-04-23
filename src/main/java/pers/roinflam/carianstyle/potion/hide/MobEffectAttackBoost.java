package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;


public class MobEffectAttackBoost extends HideBase {

    public MobEffectAttackBoost(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "attack_boost");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_DAMAGE, "74817132-1c8f-0594-1350-1a7734e34205", 0.01, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_SPEED, "bb938acd-fd3f-a0e5-d625-0352b8f23fd9", 0.02, 2);
    }

}
