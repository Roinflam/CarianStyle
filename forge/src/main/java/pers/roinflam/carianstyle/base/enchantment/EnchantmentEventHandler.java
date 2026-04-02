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
import net.minecraftforge.event.entity.player.PlayerEvent;
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
 * - v2.x：伤害事件在HIGHEST缓存一次，后续4个优先级复用，NBT解析从180次降为36次
 * - v2.2：LOWEST加receiveCanceled防止缓存泄漏
 * - v2.3：EVENT_CACHE改为ConcurrentHashMap防止并发问题
 * </p>
 * <p>
 * v3.0 核心新增 - PlayerTick装备缓存：
 *
 * 原问题：50个玩家×6槽位=每tick 300次 EnchantmentHelper.getEnchantments() 调用，
 * 每次都反序列化ItemStack的NBT附魔标签，是最大的tick性能瓶颈之一。
 *
 * 优化策略：
 * - 为每个玩家缓存6个槽位的物品身份哈希（item注册单例+count+damage，不含tag）
 * - 哈希不含tag的原因：科技模组物品的能量NBT每tick变化，如果含tag则每tick都miss
 * - 哈希匹配 → 用缓存结果，跳过NBT反序列化
 * - 哈希不匹配（换装备/耐久变化） → 立即重新扫描
 * - 每20tick（1秒）强制重新扫描一次 → 覆盖铁砧修改附魔等tag变但item不变的极端情况
 * - 玩家体验：换装备立即生效，铁砧加附魔最多1秒后生效（完全无感知）
 *
 * 效果：50人服务器 300次NBT读取/tick → 约15次/秒（仅强制刷新时），降低95%。
 *
 * 清理：玩家登出时自动清理缓存，防止内存泄漏。
 * </p>
 * <p>
 * v3.1修复 - 黑名单附魔过滤：
 * 在所有扫描点（scanEntity / scanPlayerEnchantments / 独立事件处理）
 * 增加 isDisabled() 检查，被 uninstallEnchantment 配置禁用的附魔
 * 不会进入缓存，不会触发任何效果。
 * </p>
 * <p>
 * 前置条件：需要EnchantmentBase中的dispatchLivingAttackEvent、dispatchLivingHurtEvent、
 * dispatchLivingDamageEvent三个方法为public static
 * </p>
 *
 * @version 3.1
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EnchantmentEventHandler {

    // ==================== 伤害事件扫描缓存（v2.x已有，未修改） ====================

    /**
     * 扫描结果缓存
     * <p>
     * key = 事件对象的identityHashCode
     * value = 该事件中所有需要触发的附魔信息列表
     * </p>
     * <p>
     * 生命周期：HIGHEST时创建 → LOWEST后清除
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

    // ==================== PlayerTick装备缓存（v3.0新增） ====================

    /**
     * 强制刷新间隔（tick）
     */
    private static final int FORCE_RESCAN_INTERVAL = 20;

    /**
     * 玩家装备缓存
     */
    private static final Map<UUID, PlayerEquipmentCache> PLAYER_TICK_CACHE = new ConcurrentHashMap<>();

    /**
     * 玩家装备缓存条目
     */
    private static class PlayerEquipmentCache {
        private final int[] slotHashes = new int[EquipmentSlot.values().length];
        private List<TickEnchantmentEntry> tickEntries = Collections.emptyList();
        private int ticksSinceForceRescan = 0;

        List<TickEnchantmentEntry> getOrRescan(Player player) {
            ticksSinceForceRescan++;

            boolean equipmentChanged = false;
            EquipmentSlot[] slots = EquipmentSlot.values();

            for (int i = 0; i < slots.length; i++) {
                ItemStack stack = player.getItemBySlot(slots[i]);
                int hash = computeSlotHash(stack);
                if (hash != slotHashes[i]) {
                    slotHashes[i] = hash;
                    equipmentChanged = true;
                }
            }

            boolean forceRescan = ticksSinceForceRescan >= FORCE_RESCAN_INTERVAL;

            if (equipmentChanged || forceRescan) {
                tickEntries = scanPlayerEnchantments(player);
                if (forceRescan) {
                    ticksSinceForceRescan = 0;
                }
            }

            return tickEntries;
        }

        private static int computeSlotHash(ItemStack stack) {
            if (stack.isEmpty()) {
                return 0;
            }
            int h = System.identityHashCode(stack.getItem());
            h = h * 31 + stack.getCount();
            h = h * 31 + stack.getDamageValue();
            return h;
        }

        /**
         * 完整扫描玩家所有槽位的CarianStyle附魔
         * <p>
         * v3.1修复：增加 isDisabled() 检查，被禁用的附魔不进入缓存
         * </p>
         *
         * @param player 玩家
         * @return 附魔条目列表
         */
        private static List<TickEnchantmentEntry> scanPlayerEnchantments(Player player) {
            List<TickEnchantmentEntry> entries = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty()) continue;

                Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
                for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    if (!(entry.getKey() instanceof EnchantmentBase base)) continue;
                    // v3.1：跳过被禁用的附魔
                    if (base.isDisabled()) continue;
                    int level = base.applyLevelLimit(entry.getValue());
                    if (level <= 0) continue;
                    entries.add(new TickEnchantmentEntry(base, level, stack));
                }
            }
            return entries;
        }
    }

    /**
     * PlayerTick专用的附魔条目（比CachedEnchantmentEntry更轻量）
     */
    private static class TickEnchantmentEntry {
        final EnchantmentBase enchantment;
        final int level;
        final ItemStack stack;

        TickEnchantmentEntry(EnchantmentBase enchantment, int level, ItemStack stack) {
            this.enchantment = enchantment;
            this.level = level;
            this.stack = stack;
        }
    }

    // ==================== 伤害事件扫描方法 ====================

    /**
     * 扫描实体的所有装备槽位，收集CarianStyle附魔信息
     * <p>
     * 核心优化点：每个槽位只调用一次getEnchantments()，结果缓存给后续4个优先级复用
     * v3.1修复：增加 isDisabled() 检查
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

            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                if (!(entry.getKey() instanceof EnchantmentBase base)) continue;
                // v3.1：跳过被禁用的附魔
                if (base.isDisabled()) continue;
                int level = base.applyLevelLimit(entry.getValue());
                if (level <= 0) continue;
                entries.add(new CachedEnchantmentEntry(base, level, stack, holder, isAttacker));
            }
        }
    }

    /**
     * 为伤害类事件（Attack/Hurt/Damage）构建缓存
     *
     * @param victim   受害者
     * @param attacker 攻击者（可能为null）
     * @return 缓存条目列表
     */
    private static List<CachedEnchantmentEntry> buildDamageEventCache(
            @Nonnull LivingEntity victim,
            @Nullable LivingEntity attacker) {
        List<CachedEnchantmentEntry> entries = new ArrayList<>();

        if (attacker != null) {
            scanEntity(entries, attacker, true, EquipmentSlot.values());
        }

        scanEntity(entries, victim, false, EquipmentSlot.values());

        return entries;
    }

    /**
     * 从缓存分发事件到对应优先级的模板方法
     */
    private static void dispatchFromCache(int cacheKey, @Nonnull Object event,
                                          @Nonnull EventPriority priority,
                                          @Nullable net.minecraft.world.damagesource.DamageSource source,
                                          @Nullable LivingEntity victim) {
        List<CachedEnchantmentEntry> entries = EVENT_CACHE.get(cacheKey);
        if (entries == null) return;

        for (CachedEnchantmentEntry entry : entries) {
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

    @Nullable
    private static LivingEntity getAttacker(@Nonnull net.minecraft.world.damagesource.DamageSource source) {
        if (source.getDirectEntity() instanceof LivingEntity le) return le;
        if (source.getEntity() instanceof LivingEntity le) return le;
        return null;
    }

    // ==================== LivingAttackEvent ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingAttackHighest(@Nonnull LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        int key = System.identityHashCode(event);
        LivingEntity victim = event.getEntity();
        LivingEntity attacker = getAttacker(event.getSource());
        EVENT_CACHE.put(key, buildDamageEventCache(victim, attacker));
        dispatchFromCache(key, event, EventPriority.HIGHEST, event.getSource(), victim);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void handleLivingAttackHigh(@Nonnull LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.HIGH, event.getSource(), event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingAttackNormal(@Nonnull LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.NORMAL, event.getSource(), event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void handleLivingAttackLow(@Nonnull LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.LOW, event.getSource(), event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void handleLivingAttackLowest(@Nonnull LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        int key = System.identityHashCode(event);
        if (!event.isCanceled()) {
            dispatchFromCache(key, event, EventPriority.LOWEST, event.getSource(), event.getEntity());
        }
        EVENT_CACHE.remove(key);
    }

    // ==================== LivingHurtEvent ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingHurtHighest(@Nonnull LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        int key = System.identityHashCode(event);
        LivingEntity victim = event.getEntity();
        LivingEntity attacker = getAttacker(event.getSource());
        EVENT_CACHE.put(key, buildDamageEventCache(victim, attacker));
        dispatchFromCache(key, event, EventPriority.HIGHEST, event.getSource(), victim);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void handleLivingHurtHigh(@Nonnull LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.HIGH, event.getSource(), event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingHurtNormal(@Nonnull LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.NORMAL, event.getSource(), event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void handleLivingHurtLow(@Nonnull LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.LOW, event.getSource(), event.getEntity());
    }

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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingDamageHighest(@Nonnull LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        int key = System.identityHashCode(event);
        LivingEntity victim = event.getEntity();
        LivingEntity attacker = getAttacker(event.getSource());
        EVENT_CACHE.put(key, buildDamageEventCache(victim, attacker));
        dispatchFromCache(key, event, EventPriority.HIGHEST, event.getSource(), victim);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void handleLivingDamageHigh(@Nonnull LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.HIGH, event.getSource(), event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingDamageNormal(@Nonnull LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.NORMAL, event.getSource(), event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void handleLivingDamageLow(@Nonnull LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        dispatchFromCache(System.identityHashCode(event), event, EventPriority.LOW, event.getSource(), event.getEntity());
    }

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
     * <p>
     * 仅触发一次，无需缓存
     * v3.1修复：增加 isDisabled() 检查
     * </p>
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
                // v3.1：跳过被禁用的附魔
                if (base.isDisabled()) continue;
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
     * <p>
     * 频率低，无需缓存
     * v3.1修复：增加 isDisabled() 检查
     * </p>
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
                // v3.1：跳过被禁用的附魔
                if (base.isDisabled()) continue;
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
     * v3.0核心优化：使用装备缓存。
     * v3.1修复：scanPlayerEnchantments 中已增加 isDisabled() 过滤。
     * </p>
     */
    @SubscribeEvent
    public static void handlePlayerTick(@Nonnull TickEvent.PlayerTickEvent event) {
        if (event.player.level().isClientSide || event.phase != TickEvent.Phase.START) return;

        Player player = event.player;

        if (!player.isAlive()) return;

        PlayerEquipmentCache cache = PLAYER_TICK_CACHE.computeIfAbsent(
                player.getUUID(), k -> new PlayerEquipmentCache());

        List<TickEnchantmentEntry> entries = cache.getOrRescan(player);

        if (entries.isEmpty()) return;

        for (TickEnchantmentEntry entry : entries) {
            EnchantmentContext ctx = new EnchantmentContext(event, player, entry.stack, entry.level);
            entry.enchantment.onPlayerTick(ctx, entry.level);
        }
    }

    /**
     * 暴击事件处理
     * <p>
     * 仅检查主手武器，频率低
     * v3.1修复：增加 isDisabled() 检查
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleCriticalHit(@Nonnull CriticalHitEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Player player = event.getEntity();
        ItemStack weapon = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (weapon.isEmpty()) return;
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(weapon).entrySet()) {
            if (!(entry.getKey() instanceof EnchantmentBase base)) continue;
            // v3.1：跳过被禁用的附魔
            if (base.isDisabled()) continue;
            int level = base.applyLevelLimit(entry.getValue());
            if (level > 0) {
                EnchantmentContext ctx = new EnchantmentContext(event, player, weapon, level);
                base.onCriticalHit(ctx, level);
            }
        }
    }

    // ==================== 缓存清理 ====================

    /**
     * 玩家登出时清理装备缓存，防止内存泄漏
     */
    @SubscribeEvent
    public static void onPlayerLogout(@Nonnull PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_TICK_CACHE.remove(event.getEntity().getUUID());
    }

    /**
     * 手动清除所有缓存（调试用）
     */
    public static void clearAllCaches() {
        EVENT_CACHE.clear();
        PLAYER_TICK_CACHE.clear();
    }

    /**
     * 获取PlayerTick缓存统计
     *
     * @return 缓存条目数量
     */
    public static int getPlayerTickCacheSize() {
        return PLAYER_TICK_CACHE.size();
    }
}