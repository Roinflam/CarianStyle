// 文件：MobEffectCragblade.java
// 路径：src/main/java/pers/roinflam/carianstyle/potion/hide/MobEffectCragblade.java
package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 岩石剑药水效果（隐藏）
 *
 * 效果：
 * - 攻击力提高 10% × 等级
 * - 击退抗性 +10% × 等级
 * - 护甲提高 10% × 等级
 * - 韧性提高 10% × 等级
 * - 无法跳跃
 */
public class MobEffectCragblade extends HideBase {

    public MobEffectCragblade(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "cragblade");

        // 攻击力提高 10% × 等级
        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.ATTACK_DAMAGE,
                "1288f431-abde-d1dc-0daa-6f3e8a2b1c0d",
                0.1,
                2
        );

        // 击退抗性 +10% × 等级
        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.KNOCKBACK_RESISTANCE,
                "8c4a9e3d-7b2f-4e1a-9d5c-6f3e8a2b1c0d",
                0.1,
                2
        );

        // 护甲提高 10% × 等级
        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.ARMOR,
                "8c4a9e3d-7b2f-4e1a-9d5c-6f3e8a2b1c0a",
                0.1,
                2
        );

        // 韧性提高 10% × 等级
        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.ARMOR_TOUGHNESS,
                "8c4a9e3d-7b2f-4e1a-9d5c-6f3e8a2b1c0c",
                0.1,
                2
        );
    }

    /**
     * 岩石剑状态下无法跳跃
     */
    @SubscribeEvent
    public void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        EntityLivingBase entity = evt.getEntityLiving();
        if (entity.isPotionActive(this)) {
            EntityLivingUtil.setJumped(entity);
        }
    }
}