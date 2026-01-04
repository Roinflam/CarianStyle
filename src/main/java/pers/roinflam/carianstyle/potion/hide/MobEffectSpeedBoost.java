package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;

/**
 * 速度提升药水效果（隐藏）
 * <p>
 * 效果：
 * - 移动速度+1%×等级
 * </p>
 */
public class MobEffectSpeedBoost extends HideBase {

    public MobEffectSpeedBoost(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "speed_boost");

        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.MOVEMENT_SPEED,
                "541f4474-0598-ad68-f26c-66ac0152f427",
                0.01,
                2
        );
    }
}