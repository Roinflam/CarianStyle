package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;

/**
 * 注定死亡药水效果（隐藏）
 * <p>
 * 效果：
 * - 最大生命值-25%×等级
 * </p>
 */
public class MobEffectDoomedDeath extends HideBase {

    public MobEffectDoomedDeath(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "doomed_death");

        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.MAX_HEALTH,
                "58993fe2-d11c-2b97-4958-6a8304ff8ad8",
                -0.25,
                2
        );
    }
}