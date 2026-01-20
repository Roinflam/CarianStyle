package pers.roinflam.carianstyle.dynamicattr;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户端同步属性配置
 * 将 DynamicAttribute 与客户端效果序列号关联
 */
public class ClientSyncAttribute {

    /**
     * 属性 -> 客户端效果序列号的映射表
     */
    private static final Map<DynamicAttribute, Integer> EFFECT_SERIAL_MAP = new HashMap<>();

    /**
     * 注册客户端同步属性
     *
     * @param attribute 动态属性
     * @param serialNumber 客户端效果序列号
     */
    public static void register(@Nonnull DynamicAttribute attribute, int serialNumber) {
        EFFECT_SERIAL_MAP.put(attribute, serialNumber);
    }

    /**
     * 获取客户端效果序列号
     *
     * @param attribute 动态属性
     * @return 客户端效果序列号，如果不需要客户端同步则返回null
     */
    public static Integer getEffectSerialNumber(@Nonnull DynamicAttribute attribute) {
        return EFFECT_SERIAL_MAP.get(attribute);
    }

    /**
     * 检查是否需要客户端同步
     *
     * @param attribute 动态属性
     * @return true表示该属性需要客户端同步渲染
     */
    public static boolean needsClientSync(@Nonnull DynamicAttribute attribute) {
        return EFFECT_SERIAL_MAP.containsKey(attribute);
    }
}