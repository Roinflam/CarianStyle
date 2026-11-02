package pers.roinflam.carianstyle.dynamicattr;

import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import javax.annotation.Nonnull;

/**
 * PORT-STUB(B2a)
 *
 * 客户端同步效果辅助类
 * 负责在 DynamicAttribute 应用/移除时触发客户端网络同步
 */
@Deprecated
public class ClientSyncEffectHelper {
    public static void onAttributeApplied(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
    }
    public static void onAttributeRemoved(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
    }
    public static void clearAllEffects(@Nonnull LivingEntity entity) {
    }
}

