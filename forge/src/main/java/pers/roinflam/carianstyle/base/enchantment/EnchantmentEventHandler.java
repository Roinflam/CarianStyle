package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 附魔事件处理器
 * <p>
 * 性能优化记录：
 * - 原实现：每种事件×5优先级=15次遍历所有装备槽位，每次调用getEnchantments()反序列化NBT
 *   一次近战攻击（Attack→Hurt→Damage）= 15次×(攻击者6槽+受害者6槽) = 180次NBT解析
 * - 优化后：每种事件在HIGHEST优先级时扫描一次并缓存，后续4个优先级直接用缓存分发
 *   getEnchantments调用从15次降为3次，NBT解析从180次降为36次
 * - 使用事件对象identityHashCode作为缓存key，每个事件处理完LOWEST后自动清除
 * </p>
 * <p>
 * 修复记录 v2.2：
 * - LOWEST的三个方法加上 receiveCanceled = true，防止事件被cancel后缓存永远不清除导致内存泄漏
 * </p>
 * <p>
 * 修复记录 v2.3：
 * - EVENT_CACHE 从 HashMap 改为 ConcurrentHashMap
 *   虽然 Forge 事件通常在主线程触发，但部分优化mod可能在异步线程触发伤害事件，
 *   HashMap 在并发修改时可能导致死循环或数据丢失
 * </p>
 * <p>
 * 前置条件：需要EnchantmentBase中的dispatchLivingAttackEvent、dispatchLivingHurtEvent、
 * dispatchLivingDamageEvent三个方法改为public static（原来是private static）
 * </p>
 *
 * @version 2.3
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EnchantmentEventHandler {

    // ==================== 扫描缓存 ====================

    /**
     * 扫描结果缓存
     * <p>
     * key = 事件对象的identityHashCode
     * value = 该事件中所有需要触发的附魔信息列表
     * </p>
     * <p>
     * 生命周期：HIGHEST时创建 → LOWEST后清除
     * </p>
     * <p>
     * v2.3修复：HashMap → ConcurrentHashMap，防止并发安全问题
     * </p>
     */
    private static final Map<Integer, List<CachedEnchantmentEntry>> EVENT_CACHE = new ConcurrentHashMap<>();

    /**
     * 缓存条目：一个附魔在某个物品上需要触发的信息
     */
    private static class CachedEnchantmentEntry {
        /** 附魔实例 */
        final EnchantmentBase enchantment;
        /** 附魔等级（已应用等级上限） */
        final int level;
        /** 附魔所在的物品 */
        final ItemStack stack;
        /** 持有该物品的实体 */
        final LivingEntity holder;
        /** 是否为攻击方（true=攻击者装备，false=受害者装备） */
        final boolean isAttacker;

        CachedEnchantmentEntry(EnchantmentBase enchantment, int level, ItemStack stack,
                               LivingEntity holder, boolean isAttacker) {
            this.enchantment = enchantment;
            this.level = level;
            this.stack = stack;
            this.holder = holder;
            this.isAttacker = isAttacker;
        }
    }

    /**
     * 扫描实体的所有装备槽位，收集CarianStyle附魔信息
     * <p>
     * 核心优化点：每个槽位只调用一次getEnchantments()，结果缓存给后续4个优先级复用
     * </p>
     *
     * @param entries    收集结果的列表
     * @param holder     装备持有者
     * @param isAttacker 是否为攻击方
     * @param slots      需要扫描的槽位
     */
    private static void scanEntity(@Nonnull List<CachedEnchantmentEntry> entries,
                                   @Nonnull LivingEntity holder,
                                   boolean isAttacker,
                                   @Nonnull EquipmentSlot[] slots) {
        for (EquipmentSlot slot : slots) {
            ItemStack stack = holder.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            // 核心优化点：getEnchantments()只在这里调用一次
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                if (!(entry.getKey() instanceof EnchantmentBase base)) continue;
                int level = base.applyLevelLimit(entry.getValue());
                if (level <= 0) continue;
                entries.add(new CachedEnchantmentEntry(base, level, stack, holder, isAttacker));
            }
        }
    }

    /**
     * 为伤害类事件（Attack/Hurt/Damage）构建缓存
     * <p>
     * 扫描攻击者（全槽位）和受害者（全槽位）
     * </p>
     *
     * @param victim   受害者
     * @param attacker 攻击者（可能为null）
     * @return 缓存条目列表
     */
    private static List<CachedEnchantmentEntry> buildDamageEventCache(
            @Nonnull LivingEntity victim,
            @Nullable LivingEntity attacker) {
        List<CachedEnchantmentEntry> entries = new ArrayList<>();

        // 攻击者：所有槽位
        if (attacker != null) {
            scanEntity(entries, attacker, true, EquipmentSlot.values());
        }

        // 受害者：所有槽位
        scanEntity(entries, victim, false, EquipmentSlot.values());

        return entries;
    }

    /**
     * 从缓存分发事件到对应优先级的模板方法
     *
     * @param cacheKey 缓存key（事件对象的identityHashCode）
     * @param event    事件对象
     * @param priority 当前优先级
     * @param source   伤害来源
     * @param victim   受害者
     */
    private static void dispatchFromCache(int cacheKey, @Nonnull Object event,
                                          @Nonnull EventPriority priority,
                                          @Nullable net.minecraft.world.damagesource.DamageSource source,
                                          @Nullable LivingEntity victim) {
        List<CachedEnchantmentEntry> entries = EVENT_CACHE.get(cacheKey);
        if (entries == null) return;

        for (CachedEnchantmentEntry entry : entries) {
            // 攻击者装备：attacker就是holder自己
            // 受害者装备：attacker需要从DamageSource获取（与原EnchantmentBase逻辑一致）
            LivingEntity ctxAttacker = entry.isAttacker ? entry.holder :
                    (source != null && source.getEntity() instanceof LivingEntity le ? le : null);

            EnchantmentContext ctx = new EnchantmentContext(
                    event, entry.holder, entry.stack, entry.level,
                    ctxAttacker, victim, source
            );

            if (event instanceof LivingAttackEvent) {
                EnchantmentBase.dispatchLivingAttackEvent(entry.enchantment, ctx, entry.level, priority, entry.isAttacker);
            } else if (event instanceof LivingHurtEvent) {
                EnchantmentBase.dispatchLivingHurtEvent(entry.enchantment, ctx, entry.level, priority, entry.isAttacker);
            } else if (event instanceof LivingDamageEvent) {
                EnchantmentBase.dispatchLivingDamageEvent(entry.enchantment, ctx, entry.level, priority, entry.isAttacker);
            }
        }
    }

    /**
     * 从DamageSource提取攻击者实体
     *
     * @param source 伤害来源
     * @return 攻击者LivingEntity，若非生物实体则返回null
     */
    @Nullable
    private static LivingEntity getAttacker(@Nonnull net.minecraft.world.damagesource.DamageSource source) {
        if (source.getDirectEntity() instanceof LivingEntity le) return le;
        if (source.getEntity() instanceof LivingEntity le) return le;
        return null;
    }

    // ==================== LivingAttackEvent ====================

    /** LivingAttackEvent HIGHEST：构建缓存并分发 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingAttackHighest(@Nonnull LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        int key = System.identityHashCode(event);
        LivingEntity victim = event.getEntity();
        LivingEntity attacker = getAttacker(event.getSource());
        EVENT_CACHE.put(key, buildDamageEventCache(victim, attacker));
        dispatchFromCache(key, event, EventPriority.HIGHEST, event.getSource(), victim);
    }

    /** LivingAttackEvent HIGH：从缓存分发 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void handleLivingAttackHigh(@Nonnull LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.HIGH, event.getSource(), event.getEntity());
    }

    /** LivingAttackEvent NORMAL：从缓存分发 */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingAttackNormal(@Nonnull LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.NORMAL, event.getSource(), event.getEntity());
    }

    /** LivingAttackEvent LOW：从缓存分发 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void handleLivingAttackLow(@Nonnull LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.LOW, event.getSource(), event.getEntity());
    }

    /**
     * LivingAttackEvent LOWEST：从缓存分发并清除缓存
     * <p>
     * v2.2修复：receiveCanceled = true，确保即使事件被cancel也能清除缓存
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void handleLivingAttackLowest(@Nonnull LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        int key = System.identityHashCode(event);
        // 只有未被cancel的事件才分发附魔逻辑
        if (!event.isCanceled()) {
            dispatchFromCache(key, event, EventPriority.LOWEST, event.getSource(), event.getEntity());
        }
        // 无论是否cancel都必须清除缓存
        EVENT_CACHE.remove(key);
    }

    // ==================== LivingHurtEvent ====================

    /** LivingHurtEvent HIGHEST：构建缓存并分发 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingHurtHighest(@Nonnull LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        int key = System.identityHashCode(event);
        LivingEntity victim = event.getEntity();
        LivingEntity attacker = getAttacker(event.getSource());
        EVENT_CACHE.put(key, buildDamageEventCache(victim, attacker));
        dispatchFromCache(key, event, EventPriority.HIGHEST, event.getSource(), victim);
    }

    /** LivingHurtEvent HIGH：从缓存分发 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void handleLivingHurtHigh(@Nonnull LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.HIGH, event.getSource(), event.getEntity());
    }

    /** LivingHurtEvent NORMAL：从缓存分发 */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingHurtNormal(@Nonnull LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.NORMAL, event.getSource(), event.getEntity());
    }

    /** LivingHurtEvent LOW：从缓存分发 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void handleLivingHurtLow(@Nonnull LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.LOW, event.getSource(), event.getEntity());
    }

    /**
     * LivingHurtEvent LOWEST：从缓存分发并清除缓存
     * <p>
     * v2.2修复：receiveCanceled = true，防止缓存泄漏
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void handleLivingHurtLowest(@Nonnull LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        int key = System.identityHashCode(event);
        if (!event.isCanceled()) {
            dispatchFromCache(key, event, EventPriority.LOWEST, event.getSource(), event.getEntity());
        }
        EVENT_CACHE.remove(key);
    }

    // ==================== LivingDamageEvent ====================

    /** LivingDamageEvent HIGHEST：构建缓存并分发 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingDamageHighest(@Nonnull LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        int key = System.identityHashCode(event);
        LivingEntity victim = event.getEntity();
        LivingEntity attacker = getAttacker(event.getSource());
        EVENT_CACHE.put(key, buildDamageEventCache(victim, attacker));
        dispatchFromCache(key, event, EventPriority.HIGHEST, event.getSource(), victim);
    }

    /** LivingDamageEvent HIGH：从缓存分发 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void handleLivingDamageHigh(@Nonnull LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.HIGH, event.getSource(), event.getEntity());
    }

    /** LivingDamageEvent NORMAL：从缓存分发 */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingDamageNormal(@Nonnull LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.NORMAL, event.getSource(), event.getEntity());
    }

    /** LivingDamageEvent LOW：从缓存分发 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void handleLivingDamageLow(@Nonnull LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.LOW, event.getSource(), event.getEntity());
    }

    /**
     * LivingDamageEvent LOWEST：从缓存分发并清除缓存
     * <p>
     * v2.2修复：receiveCanceled = true，防止缓存泄漏
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void handleLivingDamageLowest(@Nonnull LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        int key = System.identityHashCode(event);
        if (!event.isCanceled()) {
            dispatchFromCache(key, event, EventPriority.LOWEST, event.getSource(), event.getEntity());
        }
        EVENT_CACHE.remove(key);
    }

    // ==================== 其他事件（频率低，无需缓存优化） ====================

    /**
     * 死亡事件处理
     * <p>仅触发一次，无需缓存</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingDeath(@Nonnull LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity victim = event.getEntity();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = victim.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
                if (!(entry.getKey() instanceof EnchantmentBase base)) continue;
                int level = base.applyLevelLimit(entry.getValue());
                if (level > 0) {
                    EnchantmentContext ctx = new EnchantmentContext(event, victim, stack, level, null, victim, event.getSource());
                    base.onDeath(ctx, level);
                }
            }
        }
    }

    /**
     * 治疗事件处理
     * <p>频率低，无需缓存</p>
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingHeal(@Nonnull LivingHealEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity healer = event.getEntity();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = healer.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
                if (!(entry.getKey() instanceof EnchantmentBase base)) continue;
                int level = base.applyLevelLimit(entry.getValue());
                if (level > 0) {
                    EnchantmentContext ctx = new EnchantmentContext(event, healer, stack, level);
                    base.onHeal(ctx, level);
                }
            }
        }
    }

    /**
     * 玩家Tick事件处理
     * <p>
     * 注意：此方法每tick遍历每个玩家所有槽位所有附魔的NBT
     * 这是架构级问题，需要更深层重构（如按槽位缓存+装备变更监听），暂保持原逻辑
     * </p>
     */
    @SubscribeEvent
    public static void handlePlayerTick(@Nonnull TickEvent.PlayerTickEvent event) {
        if (event.player.level().isClientSide || event.phase != TickEvent.Phase.START) return;
        Player player = event.player;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
                if (!(entry.getKey() instanceof EnchantmentBase base)) continue;
                int level = base.applyLevelLimit(entry.getValue());
                if (level > 0) {
                    EnchantmentContext ctx = new EnchantmentContext(event, player, stack, level);
                    base.onPlayerTick(ctx, level);
                }
            }
        }
    }

    /**
     * 暴击事件处理
     * <p>仅检查主手武器，频率低</p>
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleCriticalHit(@Nonnull CriticalHitEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Player player = event.getEntity();
        ItemStack weapon = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (weapon.isEmpty()) return;
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(weapon).entrySet()) {
            if (!(entry.getKey() instanceof EnchantmentBase base)) continue;
            int level = base.applyLevelLimit(entry.getValue());
            if (level > 0) {
                EnchantmentContext ctx = new EnchantmentContext(event, player, weapon, level);
                base.onCriticalHit(ctx, level);
            }
        }
    }
}
