package pers.roinflam.carianstyle.dynamicattr;

import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;

import javax.annotation.Nonnull;

/**
 * 客户端同步效果辅助类
 * 负责在 DynamicAttribute 应用/移除时触发客户端网络同步
 */
public class ClientSyncEffectHelper {

    /**
     * 在应用动态属性时调用
     * 如果该属性需要客户端同步，则触发网络同步
     *
     * @param entity 目标实体
     * @param attribute 应用的属性
     */
    public static void onAttributeApplied(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        if (entity.level().isClientSide) {
            return;
        }

        if (ClientSyncAttribute.needsClientSync(attribute)) {
            Integer serialNumber = ClientSyncAttribute.getEffectSerialNumber(attribute);
            if (serialNumber != null) {
                ClientSyncEffectManager.addEntity(entity, serialNumber);
            }
        }
    }

    /**
     * 在移除动态属性时调用
     * 如果该属性需要客户端同步，则触发网络同步
     *
     * @param entity 目标实体
     * @param attribute 移除的属性
     */
    public static void onAttributeRemoved(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        if (entity.level().isClientSide) {
            return;
        }

        if (ClientSyncAttribute.needsClientSync(attribute)) {
            Integer serialNumber = ClientSyncAttribute.getEffectSerialNumber(attribute);
            if (serialNumber != null) {
                ClientSyncEffectManager.removeEntity(entity, serialNumber);
            }
        }
    }

    /**
     * 清除实体的所有客户端同步效果
     *
     * @param entity 目标实体
     */
    public static void clearAllEffects(@Nonnull LivingEntity entity) {
        if (entity.level().isClientSide) {
            return;
        }

        // 移除所有已注册的客户端同步效果序列号
        for (DynamicAttribute attribute : getAllClientSyncAttributes()) {
            Integer serialNumber = ClientSyncAttribute.getEffectSerialNumber(attribute);
            if (serialNumber != null) {
                ClientSyncEffectManager.removeEntity(entity, serialNumber);
            }
        }
    }

    /**
     * 获取所有需要客户端同步的属性
     * 由 DynamicAttributes 提供
     */
    private static DynamicAttribute[] getAllClientSyncAttributes() {
        return new DynamicAttribute[] {
                DynamicAttributes.DOOMED_DEATH_BURNING,
                DynamicAttributes.DESTRUCTION_FIRE_BURNING,
                DynamicAttributes.EPILEPSY_FIRE_BURNING,
                DynamicAttributes.STEALTH
        };
    }
}