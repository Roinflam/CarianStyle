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
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.config.ConfigLoader;
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
 * v3.2新增 - 怪物附魔触发开关：
 * <ul>
 *   <li>{@link ConfigLoader#allowMobTriggerEnchantments}：通用开关（默认 true）。
 *       关闭后，{@link #scanEntity} 在入口直接跳过非玩家持有者，
 *       所有走中央事件处理器的伤害/死亡事件都不再扫描怪物的附魔，
 *       是性能收益最大的拦截点。</li>
 *   <li>{@link ConfigLoader#allowMobTriggerDeathEnchantments}：死亡/濒死类附魔开关
 *       （默认 false）。覆盖范围：
 *       <ul>
 *         <li>DEAD 分类的全部附魔（在 {@link #scanEntity} 内按分类过滤，
 *             覆盖 {@code EnchantmentEpilepsySpread} 的 onDamageAsVictimLowest 路径）</li>
 *         <li>走 {@code onDeath()} 模板方法的附魔（在 {@link #handleLivingDeath} 入口拦截，
 *             覆盖 {@code EnchantmentScarletLonia}/{@code EnchantmentGreatbladePhalanx}/
 *             {@code EnchantmentAncientDragonLightning}）</li>
 *         <li>独立 @SubscribeEvent 监听 LivingDeathEvent 的濒死类附魔
 *             （满月/死诞者/时间逆转）通过调用 {@link #shouldBlockMobTrigger}
 *             在各自类内拦截</li>
 *       </ul>
 *   </li>
 * </ul>
 * </p>
 * <p>
 * 前置条件：需要EnchantmentBase中的dispatchLivingAttackEvent、dispatchLivingHurtEvent、
 * dispatchLivingDamageEvent三个方法为public static
 * </p>
 *
 * @version 3.2
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

        /**
         * 上次重扫时玩家的 {@code tickCount}；-1 表示尚未扫过。
         * <p>用于让 {@link #getOrRescan} 在同一 tick 内幂等，详见该方法注释。</p>
         */
        private int lastTickStamp = -1;

        /**
         * 取本 tick 的附魔快照，必要时重扫。
         *
         * <h3>v3.3：同一 tick 内幂等</h3>
         * <p>
         * 原实现每次调用都 {@code ticksSinceForceRescan++}。此前它只被 tick 分发调用一次，
         * 所以「20 tick 强制重扫一次」是准确的。但 v3.3 把本缓存对外开放给独立监听器之后，
         * 同一 tick 内会被调用 N 次，那个计数器就会以 N 倍速前进——
         * 「20 tick 一次」实际变成「20/N tick 一次」，强制重扫的兜底频率被悄悄放大 N 倍。
         * </p>
         * <p>
         * 因此加一个 tick 戳记：本 tick 已经扫过就直接返回快照，
         * 装备哈希比对和计数器推进都只在每 tick 的<b>第一次</b>调用时发生。
         * 谁先调用无所谓——玩家的装备在一个 tick 内不会变，快照对所有调用者都一样。
         * </p>
         *
         * @param player 玩家
         * @return 该玩家当前的附魔快照
         */
        List<TickEnchantmentEntry> getOrRescan(Player player) {
            int now = player.tickCount;
            if (now == lastTickStamp) {
                return tickEntries;
            }
            lastTickStamp = now;

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
                    entries.add(new TickEnchantmentEntry(base, level, stack, slot));
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
        /**
         * 该附魔所在的槽位（v3.3 新增）。
         * <p>对外的等级查询需要按槽位筛选（「只看护甲」「只看主手」），
         * 而快照里混着全部六个槽位，光有 {@link #stack} 反推不出它来自哪个槽。</p>
         */
        final EquipmentSlot slot;

        TickEnchantmentEntry(EnchantmentBase enchantment, int level, ItemStack stack,
                             EquipmentSlot slot) {
            this.enchantment = enchantment;
            this.level = level;
            this.stack = stack;
            this.slot = slot;
        }
    }

    // ==================== 对外的附魔等级查询（v3.3 新增） ====================

    /**
     * 全部护甲槽位，供 {@link #armorTotal} 使用。
     * <p>抽成常量避免每次调用都新建数组——这些方法会被 tick 监听器每 tick 调用。</p>
     */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** 护甲 + 主手，供 {@link #armorAndMainHand} 使用 */
    private static final EquipmentSlot[] ARMOR_AND_MAINHAND = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND
    };

    /**
     * 查询某个实体身上指定槽位的附魔等级（走缓存）。
     *
     * <h3>为什么需要这组方法</h3>
     * <p>
     * 本类的 v3.0 装备缓存只服务于「走模板方法分发」的附魔。113 个附魔里有 70 个
     * 挂了自己的 {@code @SubscribeEvent}，它们各自调
     * {@link EnchantmentHelper#getItemEnchantmentLevel}——<b>每次都要反序列化物品的附魔 ListTag</b>。
     * 其中十几个还是挂在 {@code PlayerTickEvent} 上的，也就是每玩家每 tick 都要付这个代价。
     * </p>
     * <p>
     * 这组方法让那些独立监听器共用同一份快照：一个 tick 内无论多少个监听器来问，
     * 装备只扫一遍。
     * </p>
     *
     * <h3>⚠ 与直接调 EnchantmentHelper 的两点行为差异</h3>
     * <p>
     * 快照是由 {@code scanPlayerEnchantments} 构建的，它做了两件直接调用不会做的事：
     * </p>
     * <ol>
     *   <li><b>跳过被禁用的附魔</b>（{@code isDisabled()}）——直接调用会无视黑名单配置，
     *       也就是说服主把某个附魔加进黑名单后，独立监听器仍然会触发它。
     *       改用本方法后这个洞被堵上了。</li>
     *   <li><b>应用等级上限</b>（{@code applyLevelLimit}）——直接调用返回的是 NBT 里的原始等级，
     *       指令给的 32767 级附魔会原样进入伤害公式。</li>
     * </ol>
     * <p>
     * 这两点都是<b>修复</b>而非退化，但确实是行为变化，迁移时需要知道。
     * </p>
     *
     * <h3>非玩家实体</h3>
     * <p>
     * 缓存按玩家 UUID 维护，生命周期挂在玩家登出事件上。怪物没有对应的清理时机，
     * 为它们建缓存会引入一个需要额外管理的无界 Map，收益却很小——
     * 绝大多数独立监听器一开头就用 {@link #shouldBlockMobTrigger} 把怪物挡掉了。
     * 因此非玩家实体<b>回退到直接查询</b>，行为与原来完全一致。
     * </p>
     *
     * @param holder      持有者
     * @param enchantment 要查的附魔
     * @param slot        槽位
     * @return 该槽位上的附魔等级；没有则为 0
     */
    public static int levelIn(@Nonnull LivingEntity holder, @Nonnull Enchantment enchantment,
                              @Nonnull EquipmentSlot slot) {
        if (!(holder instanceof Player player)) {
            return EnchantmentHelper.getItemEnchantmentLevel(enchantment, holder.getItemBySlot(slot));
        }
        for (TickEnchantmentEntry entry : snapshotOf(player)) {
            if (entry.slot == slot && entry.enchantment == enchantment) {
                return entry.level;
            }
        }
        return 0;
    }

    /**
     * 查询某个实体手上的附魔等级（走缓存）。
     *
     * @param holder      持有者
     * @param enchantment 要查的附魔
     * @param hand        主手或副手
     * @return 该手上的附魔等级；没有则为 0
     */
    public static int levelInHand(@Nonnull LivingEntity holder, @Nonnull Enchantment enchantment,
                                  @Nonnull InteractionHand hand) {
        return levelIn(holder, enchantment,
                hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }

    /**
     * 查询主手上的附魔等级（走缓存）。
     *
     * @param holder      持有者
     * @param enchantment 要查的附魔
     * @return 主手上的附魔等级；没有则为 0
     */
    public static int mainHand(@Nonnull LivingEntity holder, @Nonnull Enchantment enchantment) {
        return levelIn(holder, enchantment, EquipmentSlot.MAINHAND);
    }

    /**
     * 汇总若干槽位上的附魔等级（走缓存）。
     *
     * @param holder      持有者
     * @param enchantment 要查的附魔
     * @param slots       要汇总的槽位
     * @return 各槽位等级之和
     */
    public static int totalIn(@Nonnull LivingEntity holder, @Nonnull Enchantment enchantment,
                              @Nonnull EquipmentSlot... slots) {
        if (!(holder instanceof Player player)) {
            int sum = 0;
            for (EquipmentSlot slot : slots) {
                sum += EnchantmentHelper.getItemEnchantmentLevel(enchantment, holder.getItemBySlot(slot));
            }
            return sum;
        }
        int sum = 0;
        for (TickEnchantmentEntry entry : snapshotOf(player)) {
            if (entry.enchantment != enchantment) {
                continue;
            }
            for (EquipmentSlot slot : slots) {
                if (entry.slot == slot) {
                    sum += entry.level;
                    break;
                }
            }
        }
        return sum;
    }

    /**
     * 汇总四个护甲槽位上的附魔等级（走缓存）。
     * <p>等价于原来的 {@code for (ItemStack armor : entity.getArmorSlots())} 循环。</p>
     *
     * @param holder      持有者
     * @param enchantment 要查的附魔
     * @return 四个护甲槽的等级之和
     */
    public static int armorTotal(@Nonnull LivingEntity holder, @Nonnull Enchantment enchantment) {
        return totalIn(holder, enchantment, ARMOR_SLOTS);
    }

    /**
     * 汇总四个护甲槽位加主手上的附魔等级（走缓存）。
     *
     * @param holder      持有者
     * @param enchantment 要查的附魔
     * @return 五个槽位的等级之和
     */
    public static int armorAndMainHand(@Nonnull LivingEntity holder, @Nonnull Enchantment enchantment) {
        return totalIn(holder, enchantment, ARMOR_AND_MAINHAND);
    }

    /**
     * 取某个玩家当前的附魔快照，必要时重扫。
     * <p>同一 tick 内多次调用只扫一次，见 {@code PlayerEquipmentCache#getOrRescan}。</p>
     *
     * @param player 玩家
     * @return 附魔快照（只读语义，调用方不得修改）
     */
    @Nonnull
    private static List<TickEnchantmentEntry> snapshotOf(@Nonnull Player player) {
        return PLAYER_TICK_CACHE
                .computeIfAbsent(player.getUUID(), uuid -> new PlayerEquipmentCache())
                .getOrRescan(player);
    }

    // ==================== 怪物附魔触发开关工具方法（v3.2新增） ====================

    /**
     * 判断是否应该拦截怪物（非玩家）的附魔触发
     * <p>
     * 对外公开的工具方法，供独立 @SubscribeEvent 监听器使用，
     * 主要用于 RECOLLECT 分类中独立监听 LivingDeathEvent 的濒死类附魔：
     * 满月（FullMoon）、死诞者（LivingCorpse）、时间逆转（TimeReversal）。
     * </p>
     * <p>
     * 判断逻辑：
     * <ol>
     *   <li>玩家持有 → 永不拦截</li>
     *   <li>非玩家 + {@code allowMobTriggerEnchantments} 关 → 拦截</li>
     *   <li>非玩家 + {@code isDeathTrigger=true} + {@code allowMobTriggerDeathEnchantments} 关 → 拦截</li>
     *   <li>其余情况 → 不拦截</li>
     * </ol>
     * </p>
     *
     * @param holder         附魔持有者实体
     * @param isDeathTrigger 是否为死亡/濒死触发的附魔（如满月/死诞者/时间逆转传 true）
     * @return true 表示应该拦截（不触发附魔），false 表示正常触发
     */
    public static boolean shouldBlockMobTrigger(@Nonnull LivingEntity holder, boolean isDeathTrigger) {
        // 玩家持有 → 永不拦截
        if (holder instanceof Player) {
            return false;
        }

        // 非玩家 + 通用开关关闭 → 拦截全部
        if (!ConfigLoader.allowMobTriggerEnchantments) {
            return true;
        }

        // 非玩家 + 死亡类开关关闭 + 当前是死亡/濒死触发 → 拦截
        if (isDeathTrigger && !ConfigLoader.allowMobTriggerDeathEnchantments) {
            return true;
        }

        return false;
    }

    /**
     * 判断指定附魔是否属于「死亡/濒死类」
     * <p>
     * 仅判断 DEAD 分类的附魔（覆盖 EnchantmentEpilepsySpread 在
     * onDamageAsVictimLowest 路径的触发）。
     * </p>
     * <p>
     * 注意：此方法不识别 RECOLLECT 中的濒死类（满月/死诞者/时间逆转），
     * 因为它们走独立 @SubscribeEvent 监听 LivingDeathEvent，
     * 不经过 scanEntity 路径，需要在它们各自的监听器内调用
     * {@link #shouldBlockMobTrigger} 拦截。
     * </p>
     *
     * @param enchantment 附魔实例
     * @return 是否为 DEAD 分类附魔
     */
    private static boolean isDeadCategoryEnchantment(@Nonnull EnchantmentBase enchantment) {
        AutoRegisterEnchantment ann = enchantment.getClass().getAnnotation(AutoRegisterEnchantment.class);
        if (ann == null) {
            return false;
        }
        return ann.category() == pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD;
    }

    // ==================== 伤害事件扫描方法 ====================

    /**
     * 扫描实体的所有装备槽位，收集CarianStyle附魔信息
     * <p>
     * 核心优化点：每个槽位只调用一次getEnchantments()，结果缓存给后续4个优先级复用
     * v3.1修复：增加 isDisabled() 检查
     * v3.2新增：
     * <ul>
     *   <li>方法入口：怪物 + {@code allowMobTriggerEnchantments=false} → 直接 return，
     *       不扫描怪物身上的任何附魔，是最大性能收益点</li>
     *   <li>循环内：怪物 + {@code allowMobTriggerDeathEnchantments=false} + DEAD 分类 → 跳过该条目，
     *       覆盖 EnchantmentEpilepsySpread 等通过 onDamageAsVictimLowest 触发的濒死类附魔</li>
     * </ul>
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

        // ⭐ v3.2：怪物 + 通用关 → 整个 holder 跳过
        // 这是性能收益最大的拦截点，跳过整个装备槽位的 NBT 扫描
        boolean holderIsPlayer = holder instanceof Player;
        if (!holderIsPlayer && !ConfigLoader.allowMobTriggerEnchantments) {
            return;
        }

        // 是否需要在循环内过滤 DEAD 分类附魔
        // 仅当：非玩家 + 通用开 + 死亡类关 时为 true
        boolean filterDeathEnchantmentsInLoop = !holderIsPlayer
                && !ConfigLoader.allowMobTriggerDeathEnchantments;

        for (EquipmentSlot slot : slots) {
            ItemStack stack = holder.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                if (!(entry.getKey() instanceof EnchantmentBase base)) continue;

                // v3.1：跳过被禁用的附魔
                if (base.isDisabled()) continue;

                // ⭐ v3.2：怪物 + 死亡类关 + 是 DEAD 分类 → 跳过
                // 覆盖 EnchantmentEpilepsySpread（通过 onDamageAsVictimLowest 触发的濒死类）
                if (filterDeathEnchantmentsInLoop && isDeadCategoryEnchantment(base)) {
                    continue;
                }

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
     * v3.2新增：在入口拦截怪物的死亡触发——
     * 怪物 + (通用关 或 死亡类关) → 整体 return，
     * 覆盖所有走 onDeath() 模板方法的附魔
     * （如 EnchantmentScarletLonia / EnchantmentGreatbladePhalanx /
     * EnchantmentAncientDragonLightning）
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingDeath(@Nonnull LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity victim = event.getEntity();

        // ⭐ v3.2：怪物死亡触发拦截
        // 死亡是濒死类的典型场景，所以传 isDeathTrigger=true
        if (shouldBlockMobTrigger(victim, true)) {
            return;
        }

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
     * v3.2新增：怪物 + 通用关 → 跳过整体扫描
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingHeal(@Nonnull LivingHealEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity healer = event.getEntity();

        // ⭐ v3.2：治疗事件不属于死亡/濒死类，传 false
        if (shouldBlockMobTrigger(healer, false)) {
            return;
        }

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
     * v3.2备注：本方法本身就是 PlayerTickEvent，仅玩家触发，
     * 不受怪物附魔触发开关影响（怪物本来就不会进入此方法）。
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
     * v3.2备注：CriticalHitEvent 在 1.20.1 中只对玩家触发（参数类型为 Player），
     * 怪物攻击不会触发此事件，无需额外拦截。
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
