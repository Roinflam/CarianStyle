package pers.roinflam.carianstyle.handler;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.carianstyle.bus.SubscribeEvent;
import pers.roinflam.carianstyle.bus.EventBusSubscriber;
import pers.roinflam.carianstyle.event.ServerStoppedEvent;
import pers.roinflam.carianstyle.event.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.api.IEffectModifier;
import pers.roinflam.carianstyle.base.enchantment.EquipmentStamp;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;

/**
 * 药水效果修改处理器
 * <p>
 * 只负责找到所有实现了 IEffectModifier 的附魔并调用
 * 具体的等级获取和修改逻辑都在各个附魔类中
 * </p>
 * <p>
 * <b>性能优化：按实体缓存「装备上有哪些 IEffectModifier 附魔」，装备没变就一直复用，最多 20 tick 强制重扫一次。</b>
 * </p>
 * <p>
 * <b>问题：</b>本处理器由 {@code MixinEffectModifier} 挂在 {@code LivingEntity.addEffect(MobEffectInstance, Entity)}
 * 的方法头，全服每一次 addEffect 都要把护甲 + 主副手逐件跑一遍 {@code EnchantmentLevels.all}
 * （1.20.1 是 {@code EnchantmentHelper.getEnchantments}：把物品 NBT 里的附魔列表逐条反序列化、解析 ResourceLocation、查注册表；
 * 1.21.1 附魔已是组件，开销小了，但同 tick 重复扫描仍然没必要）。
 * 很多饰品每 tick 都给佩戴者续 buff，同一个玩家同一 tick 会连续进来好几次，装备根本没变，
 * 扫描结果每次都一样，有的饰品每次饰品 tick 会对佩戴者连调两次 addEffect。
 * 性能分析里本处理器的耗时大部分落在旧实现的 {@code addModifiersFromItem}
 * （即逐件 getEnchantments）里，且大多数调用栈经过饰品的 tick。
 * </p>
 * <p>
 * <b>做法与等价性：</b>缓存的只是「附魔集合」这一步，它只取决于扫描到的那几个 {@link ItemStack} 对象本身。
 * <ul>
 *   <li>扫描顺序、扫描来源与原实现完全相同：先 {@code getArmorSlots()} 再 {@code getHandSlots()}，
 *       按迭代顺序逐件读取。这一点不能改——三个 IEffectModifier 附魔的 {@code modifyEffect} 不可交换
 *       （例如 BeastRobust「等级×2+1」和 Lucidity「等级+1」先后次序不同结果不同），而 HashSet
 *       在两个元素落进同一个桶时按插入顺序迭代，所以插入顺序必须与原来一致。</li>
 *   <li>命中条件：缓存不到 {@link #MAX_AGE_TICKS} 个服务端 tick，且这次扫到的物品对象与缓存时<b>逐个引用相同</b>、
 *       每件的 {@code isEmpty()} 也与缓存时相同、每件的附魔组件对象也相同（{@link EquipmentStamp}）。
 *       换装、换物品会换成新对象；耐久耗尽不换对象（{@code hurtAndBreak} 是原地 shrink 到 0），所以另外比较空/非空状态；
 *       {@code /enchant}、{@code ItemStack.enchant}、铁砧、{@code set(ENCHANTMENTS, ...)} 都会换上新对象——都立即失效重算。</li>
 *   <li>{@code getEnchantmentLevel} <b>不缓存</b>，仍按原实现每次调用现算；{@code modifyEffect} 同样每次现算。</li>
 * </ul>
 * 1.21.1 正常途径改附魔都会换上新的组件对象，都看得出来；看不出来的只有绕开正常写法、在原组件上改的情况
 * （见 {@link EquipmentStamp} 类注释，原版和本模组都没有），之后 {@link #MAX_AGE_TICKS} tick 内的 addEffect 沿用改之前的附魔集合（换进来的会漏掉；换走的因为等级现算为 0 会被跳过），
 * 到时间强制重扫后恢复。
 * </p>
 * <p>
 * 以前缓存只在同一 tick 内有效（按「世界 + gameTime」换代就清表）：饰品每 tick 给佩戴者续 buff，
 * 每个玩家每 tick 至少重扫一次；玩家分在几个世界时连续调用在不同世界之间来回，表被反复清空。
 * </p>
 * <p>
 * <b>内存与线程：</b>每 {@link #MAX_AGE_TICKS} 个服务端 tick 把过期的条目清掉，表里只有最近 1 秒内被 addEffect 过的实体，
 * 不会随时间累积；键再用弱引用，快照里只存物品和组件对象、不存世界；关服时整张表清空，
 * 单人游戏回主菜单后不会因为静态表而拽住整个 ServerLevel。
 * 客户端在方法开头已经直接返回，不会进缓存；但 Mohist 的 addEffect 对非主线程调用只是跳过 Bukkit 事件
 * （{@code AsyncCatcher.catchAsync()} 只返回布尔值、不抛异常），而本钩子在方法头、比那个判断更早，
 * 所以确实可能被异步线程调到，缓存的读写都在 {@link #CACHE_LOCK} 内完成。
 * 调用链上有饰品 tick / 实体 tick，缓存逻辑出任何异常都退回原始扫描。
 * </p>
 *
 * @author RoinFlam
 */
@EventBusSubscriber
public class EffectModifierHandler {

    /** 快照最多用这么多个服务端 tick，到了强制重扫（兜底看不出来的原地改动） */
    private static final int MAX_AGE_TICKS = 20;

    /**
     * 服务端 tick 计数（每个 tick 开头 +1）。
     * <p>不用 {@code level.getGameTime()}：各世界的 gameTime 不一定相同（插件建的世界有自己的），按它算年龄会错。</p>
     */
    private static volatile long serverTick = 0L;

    /**
     * 保护 {@link #CACHE}。
     * 锁内只做表操作（外加 WeakHashMap 查表时调用的实体 equals/hashCode），物品/附魔/装备读取都在锁外，
     * 避免持锁期间回调进别的模组逻辑。
     */
    private static final Object CACHE_LOCK = new Object();

    /**
     * 实体 → 最近一次的扫描快照。{@code Entity} 的 equals/hashCode 按实体 id 覆写，id 相同的两个对象会共用一格；
     * 这不影响正确性，因为命中还要求扫到的物品对象逐个引用相同，而结果只由这些物品对象决定。
     */
    private static final Map<LivingEntity, EquipmentSnapshot> CACHE = new WeakHashMap<>();

    /**
     * 一次装备扫描的结果：扫到的物品对象（按原扫描顺序）、当时各件是否为空（位掩码），
     * 以及从中找到的 IEffectModifier 附魔集合。字段创建后都不再修改，只在锁内发布，可以安全地被多次读取/迭代。
     */
    private static final class EquipmentSnapshot {
        final ItemStack[] equipment;
        final long emptyMask;
        final EquipmentStamp[] stamps;
        final long createdTick;
        final Set<IEffectModifier> modifiers;

        EquipmentSnapshot(ItemStack[] equipment, long emptyMask, EquipmentStamp[] stamps, long createdTick,
                          Set<IEffectModifier> modifiers) {
            this.equipment = equipment;
            this.emptyMask = emptyMask;
            this.stamps = stamps;
            this.createdTick = createdTick;
            this.modifiers = modifiers;
        }

        boolean expired(long now) {
            long age = now - createdTick;
            return age < 0 || age >= MAX_AGE_TICKS;
        }

        boolean matches(ItemStack[] current, long currentEmptyMask, long now) {
            if (expired(now) || this.equipment.length != current.length || this.emptyMask != currentEmptyMask) {
                return false;
            }
            for (int i = 0; i < current.length; i++) {
                if (this.equipment[i] != current[i]) {
                    return false;
                }
            }
            for (int i = 0; i < current.length; i++) {
                if (!this.stamps[i].matches(current[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 处理药水效果修改
     * <p>
     * 找到实体装备上所有的 IEffectModifier 附魔，依次调用它们的修改方法
     * </p>
     * <p>
     * Mohist 系服务端正在 tick 该实体的药水效果时原样返回：那次调用只入队，
     * 重放时会再进来一次并在那时改写，保证每个效果只被改一次（见 {@link BukkitTickingEffectsProbe}）。
     * </p>
     *
     * @param entity 接受药水效果的实体
     * @param effectInstance 原始药水效果实例
     * @return 修改后的药水效果实例
     */
    public static MobEffectInstance handleEffectModification(@NotNull LivingEntity entity,
                                                             @NotNull MobEffectInstance effectInstance) {
        // 客户端不处理
        if (entity.level().isClientSide) {
            return effectInstance;
        }

        // Mohist（CraftBukkit 系）药水 tick 期间：这次 addEffect 只会入队，tickEffects 末尾重放时会再从方法头进来一次，
        // 交给那一次改；这里就改的话同一个效果会被改两次。原版 Forge、非 Mohist 结构的混合端上恒为 false。详见 BukkitTickingEffectsProbe
        if (BukkitTickingEffectsProbe.isTickingEffects(entity)) {
            return effectInstance;
        }

        // 收集实体装备上所有实现了 IEffectModifier 的附魔（去重）；同 tick 同装备时复用上次的结果
        Set<IEffectModifier> modifiers;
        try {
            modifiers = resolveModifiers(entity);
        } catch (Throwable t) {
            // 缓存逻辑出任何意外都退回原始扫描；若异常本来就来自扫描本身，这里会原样再抛一次，与原实现一致
            modifiers = collectModifiers(captureEquipment(entity));
        }

        if (modifiers.isEmpty()) {
            return effectInstance;
        }

        // 依次调用每个附魔的修改方法
        MobEffectInstance result = effectInstance;
        for (IEffectModifier modifier : modifiers) {
            // 让附魔自己获取等级（不缓存，与原实现一样每次现算）
            int level = modifier.getEnchantmentLevel(entity);

            if (level > 0) {
                MobEffectInstance modified = modifier.modifyEffect(entity, result, level);
                if (modified != null) {
                    result = modified;
                }
            }
        }

        return result;
    }

    /**
     * 取当前装备下的附魔集合；命中缓存直接复用，否则重新扫描并写回。
     */
    private static Set<IEffectModifier> resolveModifiers(@NotNull LivingEntity entity) {
        long now = serverTick;
        ItemStack[] equipment = captureEquipment(entity);
        if (equipment.length > Long.SIZE) {
            // 空/非空状态用 long 位掩码记录；装备件数超过 64 的实体（不存在于原版，防御用）不走缓存
            return collectModifiers(equipment);
        }
        long emptyMask = emptyMask(equipment);

        EquipmentSnapshot cached;
        synchronized (CACHE_LOCK) {
            cached = CACHE.get(entity);
        }
        // 判命中要读物品组件，放在锁外（快照创建后不再修改，可以安全读）
        if (cached != null && cached.matches(equipment, emptyMask, now)) {
            return cached.modifiers;
        }

        // 先记判据再扫描：扫描读到的就是记下的这几个对象
        EquipmentStamp[] stamps = new EquipmentStamp[equipment.length];
        for (int i = 0; i < equipment.length; i++) {
            stamps[i] = new EquipmentStamp();
            stamps[i].update(equipment[i]);
        }
        Set<IEffectModifier> modifiers = collectModifiers(equipment);

        synchronized (CACHE_LOCK) {
            CACHE.put(entity, new EquipmentSnapshot(equipment, emptyMask, stamps, now, modifiers));
        }
        return modifiers;
    }

    @SubscribeEvent
    public static void onServerTick(@NotNull ServerTickEvent.Pre event) {
        long now = ++serverTick;
        if (now % MAX_AGE_TICKS == 0) {
            synchronized (CACHE_LOCK) {
                CACHE.values().removeIf(snapshot -> snapshot.expired(now));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(@NotNull ServerStoppedEvent event) {
        synchronized (CACHE_LOCK) {
            CACHE.clear();
        }
    }

    /**
     * 按原实现的顺序取出要扫描的物品对象：先 {@code getArmorSlots()}，再 {@code getHandSlots()}。
     * <p>
     * 故意不改用 {@code getItemBySlot}：模组实体可以各自覆写这两个 Iterable
     * （比如一些 NPC 模组的实体），逐槽取值不一定与之相同，
     * 且槽位顺序会影响 HashSet 的迭代顺序（见类注释）。玩家/普通生物都是 4 + 2 = 6 件，数组一次分配到位。
     * </p>
     */
    private static ItemStack[] captureEquipment(@NotNull LivingEntity entity) {
        ItemStack[] buffer = new ItemStack[6];
        int size = 0;

        // 遍历所有护甲槽位
        for (ItemStack armor : entity.getArmorSlots()) {
            if (size == buffer.length) {
                buffer = Arrays.copyOf(buffer, size * 2);
            }
            buffer[size++] = armor;
        }

        // 遍历手持物品（主手 + 副手）
        for (ItemStack hand : entity.getHandSlots()) {
            if (size == buffer.length) {
                buffer = Arrays.copyOf(buffer, size * 2);
            }
            buffer[size++] = hand;
        }

        return size == buffer.length ? buffer : Arrays.copyOf(buffer, size);
    }

    /**
     * 第 i 位为 1 表示第 i 件物品当前 {@code isEmpty()}。与 {@link #collectModifiers} 跳过空物品的判断一致。
     */
    private static long emptyMask(@NotNull ItemStack[] equipment) {
        long mask = 0L;
        for (int i = 0; i < equipment.length; i++) {
            if (equipment[i].isEmpty()) {
                mask |= 1L << i;
            }
        }
        return mask;
    }

    /**
     * 收集实体装备上的所有 IEffectModifier 附魔（去重）
     * <p>
     * 按 {@link #captureEquipment} 给出的顺序逐件找出所有实现了接口的附魔，插入顺序与原实现一致
     * </p>
     *
     * @param equipment 要扫描的物品（护甲在前、手持在后）
     * @return 实现了 IEffectModifier 的附魔集合；没有则返回不可变空集合（调用方只判空、不修改）
     */
    private static Set<IEffectModifier> collectModifiers(@NotNull ItemStack[] equipment) {
        Set<IEffectModifier> modifiers = null;

        for (ItemStack item : equipment) {
            if (item.isEmpty()) {
                continue;
            }

            Map<EnchantmentBase, Integer> enchantments = EnchantmentLevels.all(item);
            for (EnchantmentBase enchantment : enchantments.keySet()) {
                if (enchantment instanceof IEffectModifier) {
                    if (modifiers == null) {
                        modifiers = new HashSet<>();
                    }
                    modifiers.add((IEffectModifier) enchantment);
                }
            }
        }

        return modifiers == null ? Collections.emptySet() : modifiers;
    }
}