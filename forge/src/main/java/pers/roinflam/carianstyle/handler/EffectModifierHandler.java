package pers.roinflam.carianstyle.handler;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.api.IEffectModifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 药水效果修改处理器
 * <p>
 * 只负责找到所有实现了 IEffectModifier 的附魔并调用
 * 具体的等级获取和修改逻辑都在各个附魔类中
 * </p>
 * <p>
 * <b>2026-09-23 性能优化：同一 tick 内按实体缓存「装备上有哪些 IEffectModifier 附魔」。</b>
 * </p>
 * <p>
 * <b>问题：</b>本处理器由 {@code MixinEffectModifier} 挂在 {@code LivingEntity.addEffect(MobEffectInstance, Entity)}
 * 的方法头，全服每一次 addEffect 都要把护甲 + 主副手逐件跑一遍 {@code EnchantmentHelper.getEnchantments}
 * （把物品 NBT 里的附魔列表逐条反序列化、解析 ResourceLocation、查注册表）。
 * 很多饰品每 tick 都给佩戴者续 buff，同一个玩家同一 tick 会连续进来好几次，装备根本没变，
 * 扫描结果每次都一样。例：无尽贪婪 {@code CuriosTools$1.curioTick} 每次饰品 tick 对佩戴者连调两次 addEffect
 * （核对过 Re-Avaritia 1.3.9.7 的实现）。性能分析里本处理器的耗时大部分落在旧实现的 {@code addModifiersFromItem}
 * （即逐件 getEnchantments）里，且大多数调用栈经过 Curios 的饰品 tick。
 * </p>
 * <p>
 * <b>做法与等价性：</b>缓存的只是「附魔集合」这一步，它只取决于扫描到的那几个 {@link ItemStack} 对象本身。
 * <ul>
 *   <li>扫描顺序、扫描来源与原实现完全相同：先 {@code getArmorSlots()} 再 {@code getHandSlots()}，
 *       按迭代顺序逐件读取。这一点不能改——三个 IEffectModifier 附魔的 {@code modifyEffect} 不可交换
 *       （例如 BeastRobust「等级×2+1」和 Lucidity「等级+1」先后次序不同结果不同），而 HashSet
 *       在两个元素落进同一个桶时按插入顺序迭代，所以插入顺序必须与原来一致。</li>
 *   <li>命中条件：同一世界、同一 gameTime（即同一 tick），且这次扫到的物品对象与缓存时<b>逐个引用相同</b>、
 *       每件的 {@code isEmpty()} 也与缓存时相同。换装、换物品会换成新对象；耐久耗尽不换对象
 *       （{@code hurtAndBreak} 是原地 shrink 到 0），所以另外比较空/非空状态——两种情况都立即失效重算。</li>
 *   <li>{@code getEnchantmentLevel} <b>不缓存</b>，仍按原实现每次调用现算；{@code modifyEffect} 同样每次现算。</li>
 * </ul>
 * 与原实现唯一可能不同的情况：同一 tick 内有代码<b>原地</b>改了一件已扫描过的装备的附魔 NBT
 * （同一个物品对象上增删 Lucidity / BeastRobust / BeastVitality，而不是换物品对象），且该实体在同一 tick
 * 稍后又被 addEffect——这一次沿用改之前的附魔集合（新增的会漏掉；删掉的因为等级现算为 0 会被跳过，
 * 但若同一附魔在别的部位还有、且与另一附魔落进同一 HashSet 桶，应用先后可能与原实现不同），下一 tick 自动恢复。
 * 原版铁砧、砂轮都是 copy 出新物品对象；附魔台和 {@code /enchant} 是原地改，但前者改的是附魔台格子里的物品，
 * 后者只改主手且要权限。
 * CarianStyle 自己没有任何原地改附魔的代码（全库无 {@code .enchant(} / {@code setEnchantments} 调用）。
 * </p>
 * <p>
 * <b>内存与线程：</b>缓存只对「当前这一个 (世界, gameTime)」有效，一旦看到别的世界或别的 tick 就整张表清空，
 * 所以表里最多只有「最近这一小段连续调用里出现过的实体」，不会随时间累积；键再用弱引用，
 * 单人游戏关服回主菜单后也不会因为静态表而拽住整个 ServerLevel（除非某个物品对象反过来强引用着持有它的实体，
 * 那也只会留住最后一代的几条，下一次换代即清空）。
 * 客户端在方法开头已经直接返回，不会进缓存；但 Mohist 的 addEffect 对非主线程调用只是跳过 Bukkit 事件
 * （{@code AsyncCatcher.catchAsync()} 只返回布尔值、不抛异常），而本钩子在方法头、比那个判断更早，
 * 所以确实可能被异步线程调到，缓存的读写都在 {@link #CACHE_LOCK} 内完成。
 * 调用链上有饰品 tick / 实体 tick，缓存逻辑出任何异常都退回原始扫描。
 * </p>
 *
 * @author RoinFlam
 */
public class EffectModifierHandler {

    /**
     * 保护 {@link #CACHE}、{@link #cacheDimension}、{@link #cacheGameTime}。
     * 锁内只做表操作（外加 WeakHashMap 查表时调用的实体 equals/hashCode），物品/附魔/装备读取都在锁外，
     * 避免持锁期间回调进别的模组逻辑。
     */
    private static final Object CACHE_LOCK = new Object();

    /**
     * 实体 → 本 tick 的扫描快照。只存当前 {@link #cacheDimension} + {@link #cacheGameTime} 这一代的数据，
     * 换代即 clear。{@code Entity} 的 equals/hashCode 按实体 id 覆写，id 相同的两个对象会共用一格；
     * 这不影响正确性，因为命中还要求扫到的物品对象逐个引用相同，而结果只由这些物品对象决定。
     */
    private static final Map<LivingEntity, EquipmentSnapshot> CACHE = new WeakHashMap<>();

    private static ResourceKey<Level> cacheDimension = null;
    private static long cacheGameTime = Long.MIN_VALUE;

    /**
     * 一次装备扫描的结果：扫到的物品对象（按原扫描顺序）、当时各件是否为空（位掩码），
     * 以及从中找到的 IEffectModifier 附魔集合。字段创建后都不再修改，只在锁内发布，可以安全地被多次读取/迭代。
     */
    private static final class EquipmentSnapshot {
        final ItemStack[] equipment;
        final long emptyMask;
        final Set<IEffectModifier> modifiers;

        EquipmentSnapshot(ItemStack[] equipment, long emptyMask, Set<IEffectModifier> modifiers) {
            this.equipment = equipment;
            this.emptyMask = emptyMask;
            this.modifiers = modifiers;
        }

        boolean matches(ItemStack[] current, long currentEmptyMask) {
            if (this.equipment.length != current.length || this.emptyMask != currentEmptyMask) {
                return false;
            }
            for (int i = 0; i < current.length; i++) {
                if (this.equipment[i] != current[i]) {
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
     * 取本 tick、当前装备下的附魔集合；命中缓存直接复用，否则重新扫描并写回。
     */
    private static Set<IEffectModifier> resolveModifiers(@NotNull LivingEntity entity) {
        Level level = entity.level();
        ResourceKey<Level> dimension = level.dimension();
        long gameTime = level.getGameTime();
        ItemStack[] equipment = captureEquipment(entity);
        if (equipment.length > Long.SIZE) {
            // 空/非空状态用 long 位掩码记录；装备件数超过 64 的实体（不存在于原版，防御用）不走缓存
            return collectModifiers(equipment);
        }
        long emptyMask = emptyMask(equipment);

        synchronized (CACHE_LOCK) {
            // 原版/模组维度走 DerivedLevelData，共用主世界的 gameTime；但 Bukkit 插件 createWorld 建的世界
            // 有自己的 PrimaryLevelData 和 gameTime（CraftServer 核实），所以「代」要用 (世界, gameTime) 一起判定。
            // ResourceKey 是驻留的（ResourceKey.create 走 VALUES 表）且没覆写 equals，!= 即按值比较。
            if (gameTime != cacheGameTime || dimension != cacheDimension) {
                CACHE.clear();
                cacheGameTime = gameTime;
                cacheDimension = dimension;
            } else {
                EquipmentSnapshot cached = CACHE.get(entity);
                if (cached != null && cached.matches(equipment, emptyMask)) {
                    return cached.modifiers;
                }
            }
        }

        Set<IEffectModifier> modifiers = collectModifiers(equipment);

        synchronized (CACHE_LOCK) {
            // 扫描期间别的线程可能已经换代，只有还在同一代时才写回
            if (gameTime == cacheGameTime && dimension == cacheDimension) {
                CACHE.put(entity, new EquipmentSnapshot(equipment, emptyMask, modifiers));
            }
        }
        return modifiers;
    }

    /**
     * 按原实现的顺序取出要扫描的物品对象：先 {@code getArmorSlots()}，再 {@code getHandSlots()}。
     * <p>
     * 故意不改用 {@code getItemBySlot}：模组实体可以各自覆写这两个 Iterable
     * （比如 CustomNPCs 的 {@code EntityNPCInterface.getArmorSlots}），逐槽取值不一定与之相同，
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

            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(item);
            for (Enchantment enchantment : enchantments.keySet()) {
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