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
 *   原实现：new ArrayList&lt;&gt;(instances) 创建快照 → 遍历 → 再创建 expired 列表 → 遍历移除。
 *   每个有动态属性的实体每tick产生2个临时ArrayList对象。
 *   优化后：直接用 Iterator 遍历原列表，过期项在遍历中就地处理并移除。
 *   列表清空后从Map移除entry，减少后续containsKey的查找范围。
 * - onLivingTick()：增加 ENTITY_ATTRIBUTES.isEmpty() 和 containsKey() 快速退出，
 *   避免对没有动态属性的实体（服务器上数千个）执行 getUUID() 和 Map.get()。
 * </p>
 *
 * <h3>性能优化 v2.2（本次新增，效果数值与持续时间完全不变）</h3>
 * <p>
 * <b>问题：重复 apply 同等级效果会整体重建实例。</b>
 * 本模组有大量附魔在<b>高频反复调用</b> {@link #apply}，且每次传入的等级都相同：
 * </p>
 * <ul>
 *     <li>{@code EnchantmentConcealingVeil} —— 潜行时<b>每 tick</b> 施加 STEALTH（时长 2 tick）</li>
 *     <li>{@code EnchantmentStarsLaw} —— 夜间<b>每 tick</b> 施加 SPEED_BOOST（时长 2 tick）</li>
 *     <li>{@code EnchantmentQuickstep} —— 每 4 tick 施加 SPEED_BOOST</li>
 *     <li>{@code EnchantmentCragblade} / {@code EnchantmentDragoncrestGreatshield} /
 *         {@code EnchantmentMillicentProsthesis} —— 每次攻击 / 受击施加</li>
 * </ul>
 * <p>
 * 而优化前的判定是 {@code shouldOverride}：等级相同、新时长更长 → 走<b>完整覆盖</b>路径，
 * 即「移除全部属性修正器 → 注销事件处理器 → 触发 onRemoved 回调 → 重新加修正器 →
 * 新建并注册事件处理器 → 触发 onApplied 回调」。
 * 然而<b>属性修正器的数值只由等级决定</b>——等级没变，拆了再装回去的是一模一样的东西。
 * </p>
 * <p>
 * <b>这带来三重浪费，其中第三条相当严重：</b>
 * </p>
 * <ol>
 *     <li>移除 + 重加 {@link AttributeModifier} 会把该属性标记为脏，
 *         导致服务端每次都向客户端下发一个 {@code ClientboundUpdateAttributesPacket}；</li>
 *     <li>事件处理器被反复 {@code MinecraftForge.EVENT_BUS.unregister/register}，
 *         每次都要操作全局监听器表；</li>
 *     <li><b>onRemoved / onApplied 回调被反复触发。</b>对 STEALTH 这类注册了客户端同步的属性，
 *         这意味着每 2 tick 就向<b>该维度所有玩家</b>广播一次「移除隐身」+「添加隐身」——
 *         一名潜行玩家、60 人同维度，就是每秒约 1200 个网络包，而且客户端的隐身状态在
 *         「隐→显→隐」之间高频抖动，肉眼可见闪烁。</li>
 * </ol>
 * <p>
 * <b>修复：</b>{@link #apply} 中若已存在实例且<b>等级相同</b>，只刷新持续时间、直接返回，
 * 不再走覆盖路径。逐条核对行为一致性：
 * </p>
 * <ul>
 *     <li>修正器数值：只由等级决定，等级未变 → 完全一致；</li>
 *     <li>持续时间：优化前等级相同且新时长更长时取新时长，否则取旧时长；
 *         优化后统一取 {@code max(旧, 新)} —— <b>两者结果完全相同</b>；</li>
 *     <li>等级不同时（更高 / 更低）走原有 {@code shouldOverride} 逻辑，一字未改。</li>
 * </ul>
 * <p>
 * <b>另：热路径去 stream。</b>{@link #has} / {@link #getAmplifier} / {@link #apply}
 * 原先用 {@code stream().filter().findFirst()}，每次调用都要分配 Stream、Predicate 与
 * Optional 对象，而这三个方法在战斗中调用极频繁。改为普通 for 循环，语义完全一致、零分配。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
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
     * <p>
     * v2.2：等级相同的重复 apply 只刷新持续时间，不再重建实例
     * （避免属性同步包、事件总线注册抖动与客户端同步广播风暴，详见类注释）。
     * </p>
     *
     * @param entity   目标实体
     * @param instance 属性实例
     */
    public static void apply(@Nonnull LivingEntity entity, @Nonnull DynamicAttributeInstance instance) {
        UUID entityId = entity.getUUID();
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.computeIfAbsent(entityId, k -> new ArrayList<>());

        // v2.2：普通循环替代 stream，避免每次调用分配 Stream / Predicate / Optional
        DynamicAttributeInstance existing = null;
        for (int i = 0; i < instances.size(); i++) {
            DynamicAttributeInstance candidate = instances.get(i);
            if (candidate.getAttribute().equals(instance.getAttribute())) {
                existing = candidate;
                break;
            }
        }

        if (existing != null) {
            // ⭐ v2.2 核心：等级相同 → 修正器数值必然一致，拆了重装毫无意义。
            // 只刷新持续时间即可，跳过「移除修正器 / 注销处理器 / onRemoved / onApplied」全套开销。
            // 时长取 max(旧, 新)，与优化前的结果完全相同（推导见类注释）
            if (instance.getAmplifier() == existing.getAmplifier()) {
                existing.refresh(Math.max(existing.getDuration(), instance.getDuration()));
                return;
            }

            // 等级不同：沿用原有覆盖判定，行为一字未改
            if (instance.shouldOverride(existing)) {
                remove(entity, existing);
                instances.remove(existing);
            } else {
                existing.refresh(Math.max(existing.getDuration(), instance.getDuration()));
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
     * <p>v2.2：普通循环替代 stream。</p>
     *
     * @param entity    目标实体
     * @param attribute 要移除的属性
     */
    public static void remove(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        UUID entityId = entity.getUUID();
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.get(entityId);
        if (instances == null) return;

        for (int i = 0; i < instances.size(); i++) {
            DynamicAttributeInstance instance = instances.get(i);
            if (!instance.getAttribute().equals(attribute)) {
                continue;
            }

            removeModifiers(entity, instance);
            instance.unregisterEventHandler();

            // 触发移除回调
            attribute.triggerOnRemoved(entity);

            instances.remove(i);
            return;
        }
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
     * <p>v2.2：普通循环替代 stream（本方法在战斗中调用极频繁）。</p>
     *
     * @param entity    目标实体
     * @param attribute 要检查的属性
     * @return true表示拥有
     */
    public static boolean has(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.get(entity.getUUID());
        if (instances == null) return false;

        for (int i = 0; i < instances.size(); i++) {
            if (instances.get(i).getAttribute().equals(attribute)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取实体指定动态属性的等级
     * <p>v2.2：普通循环替代 stream（本方法在战斗中调用极频繁）。</p>
     *
     * @param entity    目标实体
     * @param attribute 要查询的属性
     * @return 等级，如果不存在返回-1
     */
    public static int getAmplifier(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.get(entity.getUUID());
        if (instances == null) return -1;

        for (int i = 0; i < instances.size(); i++) {
            DynamicAttributeInstance instance = instances.get(i);
            if (instance.getAttribute().equals(attribute)) {
                return instance.getAmplifier();
            }
        }
        return -1;
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
     * 原实现：new ArrayList&lt;&gt;(instances) + new ArrayList&lt;&gt;() for expired → 2次分配/tick/entity
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
