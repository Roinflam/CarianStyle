package pers.roinflam.carianstyle.dynamicattr;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态属性管理器
 * 负责应用、移除、更新实体的动态属性
 * <p>
 * 性能优化记录 v2.1：
 * - processEntityTick()：移除每tick创建的 ArrayList 快照。
 *   原实现：new ArrayList<>(instances) 创建快照 → 遍历 → 再创建 expired 列表 → 遍历移除。
 *   每个有动态属性的实体每tick产生2个临时ArrayList对象。
 *   优化后：直接用 Iterator 遍历原列表，过期项在遍历中就地处理并移除。
 *   列表清空后从Map移除entry，减少后续containsKey的查找范围。
 * - onLivingTick()：增加 ENTITY_ATTRIBUTES.isEmpty() 和 containsKey() 快速退出，
 *   避免对没有动态属性的实体（服务器上数千个）执行 getUUID() 和 Map.get()。
 * </p>
 */
@Mod.EventBusSubscriber
public class DynamicAttributeManager {

    private static final String NAMESPACE = "carianstyle:dynamic_attribute:";

    /**
     * 实体UUID → 动态属性实例列表
     * <p>
     * ConcurrentHashMap 保证 key 级别的线程安全，但 value(ArrayList) 本身不是线程安全的。
     * 所有对 List 的操作都应在主线程（服务端tick）中进行。
     * </p>
     */
    private static final Map<UUID, List<DynamicAttributeInstance>> ENTITY_ATTRIBUTES = new ConcurrentHashMap<>();

    /**
     * 应用动态属性到实体
     *
     * @param entity   目标实体
     * @param instance 属性实例
     */
    public static void apply(@Nonnull LivingEntity entity, @Nonnull DynamicAttributeInstance instance) {
        UUID entityId = entity.getUUID();
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.computeIfAbsent(entityId, k -> new ArrayList<>());

        Optional<DynamicAttributeInstance> existing = instances.stream()
                .filter(i -> i.getAttribute().equals(instance.getAttribute()))
                .findFirst();

        if (existing.isPresent()) {
            DynamicAttributeInstance old = existing.get();
            if (instance.shouldOverride(old)) {
                remove(entity, old);
                instances.remove(old);
            } else {
                old.refresh(Math.max(old.getDuration(), instance.getDuration()));
                return;
            }
        }

        instances.add(instance);
        applyModifiers(entity, instance);

        if (instance.getAttribute().hasEventHandler()) {
            Object handler = instance.getAttribute().createEventHandler(entity);
            instance.setEventHandler(handler);
        }

        // 触发应用回调
        instance.getAttribute().triggerOnApplied(entity);
    }

    /**
     * 移除实体的指定动态属性
     *
     * @param entity    目标实体
     * @param attribute 要移除的属性
     */
    public static void remove(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        UUID entityId = entity.getUUID();
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.get(entityId);
        if (instances == null) return;

        instances.stream()
                .filter(i -> i.getAttribute().equals(attribute))
                .findFirst()
                .ifPresent(instance -> {
                    removeModifiers(entity, instance);
                    instance.unregisterEventHandler();

                    // 触发移除回调
                    attribute.triggerOnRemoved(entity);

                    instances.remove(instance);
                });
    }

    /**
     * 移除动态属性实例(内部方法)
     *
     * @param entity   目标实体
     * @param instance 要移除的实例
     */
    private static void remove(@Nonnull LivingEntity entity, @Nonnull DynamicAttributeInstance instance) {
        removeModifiers(entity, instance);
        instance.unregisterEventHandler();

        // 触发移除回调
        instance.getAttribute().triggerOnRemoved(entity);

        UUID entityId = entity.getUUID();
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.get(entityId);
        if (instances != null) {
            instances.remove(instance);
        }
    }

    /**
     * 检查实体是否拥有指定动态属性
     *
     * @param entity    目标实体
     * @param attribute 要检查的属性
     * @return true表示拥有
     */
    public static boolean has(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.get(entity.getUUID());
        if (instances == null) return false;
        return instances.stream().anyMatch(i -> i.getAttribute().equals(attribute));
    }

    /**
     * 获取实体指定动态属性的等级
     *
     * @param entity    目标实体
     * @param attribute 要查询的属性
     * @return 等级，如果不存在返回-1
     */
    public static int getAmplifier(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.get(entity.getUUID());
        if (instances == null) return -1;

        return instances.stream()
                .filter(i -> i.getAttribute().equals(attribute))
                .findFirst()
                .map(DynamicAttributeInstance::getAmplifier)
                .orElse(-1);
    }

    /**
     * 获取实体身上的所有动态属性实例
     *
     * @param entity 目标实体
     * @return 动态属性实例列表，如果没有则返回null
     */
    @Nullable
    public static List<DynamicAttributeInstance> getInstances(@Nonnull LivingEntity entity) {
        return ENTITY_ATTRIBUTES.get(entity.getUUID());
    }

    /**
     * 清除实体的所有动态属性
     *
     * @param entity 目标实体
     */
    public static void clearAll(@Nonnull LivingEntity entity) {
        UUID entityId = entity.getUUID();
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.remove(entityId);
        if (instances != null) {
            // clearAll不在tick循环中调用，安全性优先，复制后遍历
            List<DynamicAttributeInstance> snapshot = new ArrayList<>(instances);
            for (DynamicAttributeInstance instance : snapshot) {
                removeModifiers(entity, instance);
                instance.unregisterEventHandler();
                instance.getAttribute().triggerOnRemoved(entity);
            }
        }
    }

    /**
     * 应用属性修改器到实体
     *
     * @param entity   目标实体
     * @param instance 属性实例
     */
    private static void applyModifiers(@Nonnull LivingEntity entity, @Nonnull DynamicAttributeInstance instance) {
        DynamicAttribute attribute = instance.getAttribute();

        for (Map.Entry<Attribute, DynamicAttribute.ModifierConfig> entry : attribute.getModifierConfigs().entrySet()) {
            Attribute targetAttr = entry.getKey();
            DynamicAttribute.ModifierConfig config = entry.getValue();

            AttributeInstance attrInstance = entity.getAttribute(targetAttr);
            if (attrInstance == null) continue;

            UUID modifierId = getModifierUUID(attribute.getRegistryName(), targetAttr);

            AttributeModifier oldModifier = attrInstance.getModifier(modifierId);
            if (oldModifier != null) {
                attrInstance.removeModifier(oldModifier);
            }

            double finalValue = config.calculate(instance.getAmplifier());

            AttributeModifier newModifier = new AttributeModifier(
                    modifierId,
                    "DynamicAttribute:" + attribute.getRegistryName(),
                    finalValue,
                    config.operation
            );

            attrInstance.addPermanentModifier(newModifier);
        }
    }

    /**
     * 移除属性修改器
     *
     * @param entity   目标实体
     * @param instance 属性实例
     */
    private static void removeModifiers(@Nonnull LivingEntity entity, @Nonnull DynamicAttributeInstance instance) {
        DynamicAttribute attribute = instance.getAttribute();

        for (Attribute targetAttr : attribute.getModifierConfigs().keySet()) {
            AttributeInstance attrInstance = entity.getAttribute(targetAttr);
            if (attrInstance == null) continue;

            UUID modifierId = getModifierUUID(attribute.getRegistryName(), targetAttr);
            AttributeModifier modifier = attrInstance.getModifier(modifierId);

            if (modifier != null) {
                attrInstance.removeModifier(modifier);
            }
        }
    }

    /**
     * 使用确定性方法生成修改器UUID
     * 基于属性名和目标属性名生成固定的UUID，确保游戏重启后UUID保持一致
     *
     * @param attributeName 动态属性名
     * @param targetAttr    目标属性
     * @return 固定的UUID
     */
    private static UUID getModifierUUID(String attributeName, Attribute targetAttr) {
        String fullName = NAMESPACE + attributeName + ":" + targetAttr.getDescriptionId();
        return UUID.nameUUIDFromBytes(fullName.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 玩家Tick事件监听
     * 处理动态属性的时间流逝和Tick回调
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.player.level().isClientSide()) {
            return;
        }

        processEntityTick(event.player);
    }

    /**
     * 所有生物实体Tick事件监听（包括Mob、动物等）
     * 处理非玩家实体的动态属性
     * <p>
     * v2.1优化：增加快速退出检查。
     * ENTITY_ATTRIBUTES 通常只包含少量有动态属性的实体UUID，
     * 但 LivingTickEvent 对加载区块中的所有生物触发（可能数千个）。
     * 通过 isEmpty() 和 containsKey() 快速退出，避免无效的 getUUID() 调用。
     * </p>
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity.level().isClientSide() || entity instanceof net.minecraft.world.entity.player.Player) {
            return;
        }

        // v2.1优化：全局无动态属性时直接退出（覆盖和平时期99%的情况）
        if (ENTITY_ATTRIBUTES.isEmpty()) {
            return;
        }

        // v2.1优化：该实体无动态属性时直接退出
        if (!ENTITY_ATTRIBUTES.containsKey(entity.getUUID())) {
            return;
        }

        processEntityTick(entity);
    }

    /**
     * 处理实体的Tick逻辑（提取公共方法）
     * <p>
     * v2.1优化：消除每tick的ArrayList快照分配。
     * 原实现：new ArrayList<>(instances) + new ArrayList<>() for expired → 2次分配/tick/entity
     * 优化后：使用 Iterator 直接遍历，过期项就地处理并通过 iterator.remove() 移除，零额外分配。
     * 列表清空后从Map移除entry，减少后续查找范围。
     * </p>
     *
     * @param entity 要处理的实体
     */
    private static void processEntityTick(LivingEntity entity) {
        UUID entityId = entity.getUUID();
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.get(entityId);

        if (instances == null || instances.isEmpty()) return;

        // v2.1优化：使用 Iterator 直接遍历并移除，避免创建快照ArrayList和expired列表
        Iterator<DynamicAttributeInstance> iterator = instances.iterator();
        while (iterator.hasNext()) {
            DynamicAttributeInstance instance = iterator.next();

            // 时间流逝检查：返回true表示已过期
            if (instance.tick(1)) {
                // 过期：就地移除并执行清理回调
                iterator.remove();
                removeModifiers(entity, instance);
                instance.unregisterEventHandler();
                instance.getAttribute().triggerOnRemoved(entity);
                continue;
            }

            // Tick回调检查
            if (instance.shouldTriggerTick()) {
                DynamicAttribute.EffectCallback onTick = instance.getAttribute().getOnTickCallback();
                if (onTick != null) {
                    try {
                        EffectContext context = new EffectContext(
                                entity,
                                instance,
                                instance.calculateTotalTicks(),
                                instance.getTotalTicksTriggered()
                        );
                        onTick.accept(context);
                    } catch (Exception e) {
                        System.err.println("动态属性tick异常: " + instance.getAttribute().getRegistryName());
                        e.printStackTrace();
                    }
                }
            }
        }

        // v2.1：列表清空后从Map移除entry，减少后续 containsKey/isEmpty 的查找范围
        if (instances.isEmpty()) {
            ENTITY_ATTRIBUTES.remove(entityId);
        }
    }

    /**
     * 实体移除时清理数据
     *
     * @param entity 被移除的实体
     */
    public static void onEntityRemove(@Nonnull LivingEntity entity) {
        clearAll(entity);
    }
}
