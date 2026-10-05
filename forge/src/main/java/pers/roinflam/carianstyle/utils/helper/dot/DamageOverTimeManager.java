package pers.roinflam.carianstyle.utils.helper.dot;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 持续伤害（DoT）集中管理器
 * <p>
 * 核心性能优化：替代每次攻击创建独立 SynchronizationTask 的模式。
 * </p>
 * <p>
 * 原问题：
 * 注定死亡、死亡之刃、黑焰刃、癫火、战士、沙布里里嚎叫、空癫火等附魔，
 * 每次攻击命中都创建1-2个 SynchronizationTask(delay, 1)，持续60-100tick。
 * 50人服务器战斗高峰时，每秒产生数百个周期任务对象，同时存活数千个。
 * 每个匿名内部类实例约200+字节（捕获外部变量引用+类元数据），
 * 加上 ConcurrentHashMap/ConcurrentLinkedQueue 操作的 Node 对象。
 * </p>
 * <p>
 * 优化方案：
 * 所有持续伤害效果统一注册到一个 ArrayList 中，
 * 每个 ServerTick 遍历一次列表处理所有活跃效果。
 * DoTEntry 是轻量级普通类，无闭包捕获开销。
 * </p>
 * <p>
 * 效果估算（50人战斗场景）：
 * - 原方案：~2000个 ConcurrentHashMap/Queue 条目 + 2000个匿名类实例
 * - 新方案：~2000个 DoTEntry 在一个 ArrayList 中，单次顺序遍历
 * - GC分配速率降低约15-25GB/min（消除匿名类+HashMap节点分配）
 * </p>
 *
 * <h3>v1.1：新增来源标签（tag）与剩余伤害查询</h3>
 * <p>
 * <b>为什么需要：</b>本管理器是<b>七个附魔共用的池子</b>（注定死亡、死亡之刃、黑焰刃、
 * 癫火、战士、沙布里里嚎叫、空癫火）。此前的公开 API 只有
 * {@link #getActiveCount()} 与 {@link #getStats()}，都是全局计数，
 * 无法回答「<b>某个实体身上、由某个特定附魔造成的</b>持续伤害还剩多少」。
 * </p>
 * <p>
 * 战士的 HUD 需要显示「流血剩余」。如果直接把该实体身上所有 DoT 加起来，
 * 一个同时中了癫火和战士的玩家，HUD 会把癫火的伤害算进战士那一行——
 * 数字看着有，但意义是错的。因此给条目加一个可选的来源标签。
 * </p>
 * <p>
 * <b>完全向后兼容：</b>原有的 {@link #applyLinear(LivingEntity, float, int, int, DamageSource, boolean)}
 * 与 {@link #applyScaling(LivingEntity, float, int, int, DamageSource, boolean, BiFunction)}
 * 两个重载<b>签名与行为一律不变</b>，内部以 {@code tag = null} 转调新重载。
 * 六个尚未打标签的附魔一行都不用改，只是查不到而已。
 * </p>
 *
 * <h3>v1.2：自损类持续伤害的致死一击改走原版伤害管线</h3>
 * <p>
 * <b>问题：</b>致死分支一律调用 {@link EntityLivingUtil#kill}——先把血量直接扣到 0，再直接调用
 * {@code die()}，完全不经过 {@code LivingEntity.hurt()}。对照原版伤害致死，第三方模组能看到的差别是：
 * </p>
 * <ul>
 *     <li>收不到这一击的 {@code LivingAttackEvent} / {@code LivingHurtEvent} / {@code LivingDamageEvent}，
 *         死亡凭空出现（{@code LivingDeathEvent} 与 {@code LivingDropsEvent} 本身照常触发，掉落列表也一致）；</li>
 *     <li>战斗记录（{@code CombatTracker}）里没有这一击，死亡消息退化成「xx 死了」；
 *         死前不久挨过别人一下的话，还会被记成「被那个人杀死」；</li>
 *     <li>死后 {@code getLastDamageSource()} 仍是之前那一下（或 null），受伤统计、受伤类进度触发器都没有。</li>
 * </ul>
 * <p>
 * 只看 {@code LivingDeathEvent} / {@code LivingDropsEvent} 的坟墓模组在两条路径下表现相同；
 * 读死亡消息、死因、受伤阶段状态的模组，以及混合端（Mohist 等）上依赖 {@code EntityDamageEvent}
 * （同样在伤害管线里触发，{@code getLastDamageCause()} 由它填写）的插件，看到的直接处决是一次来路不明的死亡。
 * </p>
 * <p>
 * <b>为什么只改自损：</b>夏玻利利的嘶吼、癫火、空癫火给攻击者自己挂的反噬 DoT，伤害源不带任何实体，
 * 致命一击进 {@code hurt()} 不会让任何人的攻击类附魔（吸血、叠层、再挂 DoT……）对它起反应。
 * 对敌 DoT（注定死亡、死亡之刃、黑焰刃、战士）用的是命中时的原始伤害源，攻击者就是出手的人，
 * 致命一击一旦进伤害管线，攻击者当前武器上的攻击类附魔会按「10 倍最大生命」的伤害量再结算一遍，
 * 所以它们仍保持直接处决；癫火 / 空癫火挂给目标的那一份同样不动。
 * </p>
 * <p>
 * <b>怎么保证「谁会死」不变：</b>
 * </p>
 * <ul>
 *     <li>致命一击用原伤害源的副本，额外挂上 {@code BYPASSES_INVULNERABILITY / COOLDOWN / ARMOR / EFFECTS / SHIELD}，
 *         和原版 {@code /kill} 一个思路：不死图腾、创造模式、出生保护、抗性、护甲、保护附魔、盾牌都拦不住；
 *         本模组里遵守「无视无敌就不介入」约定的免死 / 免伤附魔（无敌、发狂扩散等）也不会出手，
 *         结果与原来的直接处决一致——不挂这些标签的话，穿无敌的玩家濒死时有很大概率免疫这一击
 *         （概率随缺失生命升高），穿发狂扩散的不在冷却时会被它救下，等于凭空多出一条命；</li>
 *     <li>伤害量取最大生命的 10 倍，与出血 / 切腹的致死路径相同，不用 {@code Float.MAX_VALUE}，避免后续乘算溢出；</li>
 *     <li>这一击如果压根没走到死亡判定（被某个监听取消、被减到不致命、实体本身不吃这类伤害），
 *         退回原来的 {@link EntityLivingUtil#kill}，结果与改动前完全相同；</li>
 *     <li>走到了死亡判定、但 {@code LivingDeathEvent} 被取消（满月、死诞者、普拉顿桑克斯的回溯这类复活）时不补刀，
 *         与原来直接处决时这些复活照样生效一致；</li>
 *     <li>受击侧附魔把这一击转成的持续伤害（战士）在这一击结算完就丢掉，见 {@link #discardEntriesFrom}；
 *         死亡事件里同样能看到「无视无敌」，入口检查它的死亡触发附魔（洛尼亚）要另用
 *         {@link #isSelfLethalBlow} 认出这一击，照旧触发。</li>
 * </ul>
 * <p>
 * <b>行为影响：</b>自损致死时第三方能看到完整的 攻击 → 受伤 → 伤害 → 死亡 → 掉落 事件链，
 * 死亡消息变成该伤害类型自己的（如「心中的癫火发作后死亡了」），{@code getLastDamageSource()} 正确；
 * 死者的受伤统计会多记一笔 10 倍最大生命的伤害。逐 tick 的持续扣血仍然直扣血量，不变。
 * </p>
 *
 * @author RoinFlam
 * @version 1.2
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DamageOverTimeManager {

    /**
     * 所有活跃的持续伤害效果
     * <p>
     * 只在主线程（ServerTick START阶段）中遍历修改，不需要并发容器。
     * ArrayList 保证顺序遍历的缓存友好性。
     * </p>
     */
    private static final List<DoTEntry> ACTIVE_DOTS = new ArrayList<>(256);

    /**
     * 待添加队列
     * <p>
     * apply() 可能在 ServerTick 遍历期间被间接触发（通过kill触发DeathEvent，
     * DeathEvent中某些附魔又调用apply），使用 pending 队列延迟到遍历结束后批量添加，
     * 避免 ConcurrentModificationException。
     * </p>
     */
    private static final List<DoTEntry> PENDING = new ArrayList<>(64);

    /**
     * 是否正在遍历中
     */
    private static boolean iterating = false;

    /**
     * 自损致死一击的伤害倍率（× 最大生命，v1.2）
     * <p>与出血 / 切腹的致死路径取同一个值：足以压过吸收与各类百分比减伤，
     * 又不会像 {@code Float.MAX_VALUE} 那样在后续乘算里变成无穷大。</p>
     */
    private static final float SELF_LETHAL_DAMAGE_MULTIPLIER = 10.0f;

    /**
     * 正在挨致命一击的实体（v1.2）
     * <p>只在 {@link #killThroughDamagePipeline} 调用 {@code hurt()} 期间非 null，
     * 供 {@link #onLivingDeath} 判断这一击有没有走到死亡判定。</p>
     */
    @Nullable
    private static LivingEntity lethalTarget = null;

    /**
     * {@link #lethalTarget} 在这一击里是否触发过 {@code LivingDeathEvent}（被取消也算）
     */
    private static boolean lethalDeathPosted = false;

    /**
     * 正在结算的致命一击所用的伤害源副本，与 {@link #lethalTarget} 同时设置、同时清空
     */
    @Nullable
    private static DamageSource lethalSource = null;

    // ==================== 公开API ====================

    /**
     * 注册一个固定伤害的持续效果（每tick扣固定值）
     *
     * @param target        目标实体
     * @param damagePerTick 每tick伤害值（在注册时计算好，不再每tick重算）
     * @param durationTicks 持续时间（tick）
     * @param initialDelay  初始延迟（tick），0=下一tick开始
     * @param source        伤害来源（用于击杀时的死亡消息）
     * @param canKill       是否可以致死
     */
    public static void applyLinear(@Nonnull LivingEntity target, float damagePerTick,
                                   int durationTicks, int initialDelay,
                                   @Nonnull DamageSource source, boolean canKill) {
        applyLinear(target, damagePerTick, durationTicks, initialDelay, source, canKill, null);
    }

    /**
     * 注册一个固定伤害的持续效果，并打上来源标签（v1.1 新增）
     * <p>
     * 打了标签的条目才能被 {@link #getRemainingDamage} 按来源查到。
     * 标签建议用附魔自身的常量（如 {@code EnchantmentWarrior.DOT_TAG}），
     * 不要在多处硬编码字符串。
     * </p>
     *
     * @param target        目标实体
     * @param damagePerTick 每tick伤害值（在注册时计算好，不再每tick重算）
     * @param durationTicks 持续时间（tick）
     * @param initialDelay  初始延迟（tick），0=下一tick开始
     * @param source        伤害来源（用于击杀时的死亡消息）
     * @param canKill       是否可以致死
     * @param tag           来源标签，可为 null（表示不参与按来源查询）
     */
    public static void applyLinear(@Nonnull LivingEntity target, float damagePerTick,
                                   int durationTicks, int initialDelay,
                                   @Nonnull DamageSource source, boolean canKill,
                                   @Nullable String tag) {
        DoTEntry entry = new DoTEntry(target, damagePerTick, durationTicks, initialDelay, source, canKill, null, tag, false);
        addEntry(entry);
    }

    /**
     * 注册一个「自损」的固定伤害持续效果（v1.2 新增）
     * <p>
     * 用于攻击者给自己挂的反噬伤害（夏玻利利的嘶吼、癫火、空癫火）。可以致死；
     * 逐 tick 扣血与 {@link #applyLinear} 相同，区别只在致死那一击走原版伤害管线，
     * 让第三方看到的死亡与原版无异，详见类注释 v1.2。
     * </p>
     * <p>
     * 伤害源请传不带实体的那种（如 {@code NewDamageSource.epilepsyFire(level)}）。
     * 带攻击者的伤害源进伤害管线会触发攻击者的攻击类附魔，不适合走这里。
     * </p>
     *
     * @param holder        承受反噬的实体（通常就是攻击者自己）
     * @param damagePerTick 每tick伤害值
     * @param durationTicks 持续时间（tick）
     * @param initialDelay  初始延迟（tick）
     * @param source        伤害来源（决定死亡消息）
     */
    public static void applySelfLinear(@Nonnull LivingEntity holder, float damagePerTick,
                                       int durationTicks, int initialDelay,
                                       @Nonnull DamageSource source) {
        DoTEntry entry = new DoTEntry(holder, damagePerTick, durationTicks, initialDelay, source, true, null, null, true);
        addEntry(entry);
    }

    /**
     * 注册一个递增/自定义伤害的持续效果
     * <p>
     * 实际伤害 = scalingFunction.apply(baseDamagePerTick, elapsedDamageTicks)
     * </p>
     *
     * @param target             目标实体
     * @param baseDamagePerTick  基础每tick伤害值（传给scaling函数的第一个参数）
     * @param durationTicks      持续时间（tick）
     * @param initialDelay       初始延迟（tick）
     * @param source             伤害来源
     * @param canKill            是否可以致死
     * @param scalingFunction    伤害缩放函数：(baseDamage, elapsedTicks) -> actualDamage
     */
    public static void applyScaling(@Nonnull LivingEntity target, float baseDamagePerTick,
                                    int durationTicks, int initialDelay,
                                    @Nonnull DamageSource source, boolean canKill,
                                    @Nonnull BiFunction<Float, Integer, Float> scalingFunction) {
        applyScaling(target, baseDamagePerTick, durationTicks, initialDelay, source, canKill, scalingFunction, null);
    }

    /**
     * 注册一个递增/自定义伤害的持续效果，并打上来源标签（v1.1 新增）
     * <p>
     * <b>⚠ 注意：</b>{@link #getRemainingDamage} 对缩放型条目只能按<b>基础速率</b>估算
     * （{@code baseDamagePerTick × 剩余 tick}），无法预测缩放函数未来的返回值。
     * 若需要精确的剩余量，请改用 {@link #applyLinear} 的标签重载。
     * </p>
     *
     * @param target            目标实体
     * @param baseDamagePerTick 基础每tick伤害值（传给scaling函数的第一个参数）
     * @param durationTicks     持续时间（tick）
     * @param initialDelay      初始延迟（tick）
     * @param source            伤害来源
     * @param canKill           是否可以致死
     * @param scalingFunction   伤害缩放函数：(baseDamage, elapsedTicks) -> actualDamage
     * @param tag               来源标签，可为 null（表示不参与按来源查询）
     */
    public static void applyScaling(@Nonnull LivingEntity target, float baseDamagePerTick,
                                    int durationTicks, int initialDelay,
                                    @Nonnull DamageSource source, boolean canKill,
                                    @Nonnull BiFunction<Float, Integer, Float> scalingFunction,
                                    @Nullable String tag) {
        DoTEntry entry = new DoTEntry(target, baseDamagePerTick, durationTicks, initialDelay, source, canKill, scalingFunction, tag, false);
        addEntry(entry);
    }

    /**
     * 查询某实体身上、由指定来源造成的持续伤害<b>剩余总量</b>（v1.1 新增）
     * <p>
     * 同一来源可能有多个条目同时存在（例如战士在 60 tick 内被连续命中多次，
     * 每次都会注册一条），本方法会把它们全部累加。
     * </p>
     * <p>
     * <b>计算方式：</b>{@code Σ(每tick伤害 × 剩余tick)}。对
     * {@link #applyLinear} 注册的条目是精确值；对 {@link #applyScaling} 注册的条目
     * 只是按基础速率的估算（详见该方法注释）。
     * </p>
     * <p>
     * <b>初始延迟期内的条目也计入</b>——伤害尚未开始扣，但它确实是「欠着的」，
     * HUD 应该在延迟期就把它显示出来，否则玩家会看到数字凭空跳出来。
     * </p>
     * <p>
     * <b>线程：</b>仅供服务端主线程调用（与 tick 处理同线程），故直接遍历 ArrayList。
     * 用下标遍历而非迭代器，避免在 {@link #onServerTick} 遍历期间被调用时抛
     * ConcurrentModificationException——虽然当前不存在这种调用路径，但成本为零，值得防。
     * </p>
     *
     * @param target 目标实体
     * @param tag    来源标签（与注册时传入的一致）
     * @return 剩余伤害总量；无匹配条目时为 0
     */
    public static float getRemainingDamage(@Nonnull LivingEntity target, @Nonnull String tag) {
        int targetId = target.getId();
        float total = 0f;
        for (int i = 0; i < ACTIVE_DOTS.size(); i++) {
            DoTEntry dot = ACTIVE_DOTS.get(i);
            if (dot.targetId == targetId && tag.equals(dot.tag)) {
                total += dot.remainingDamage();
            }
        }
        // 待添加队列里的条目同样算数：它们是本 tick 刚注册的，
        // 漏掉会让 HUD 在受击后的第一次轮询少显示一截
        for (int i = 0; i < PENDING.size(); i++) {
            DoTEntry dot = PENDING.get(i);
            if (dot.targetId == targetId && tag.equals(dot.tag)) {
                total += dot.remainingDamage();
            }
        }
        return total;
    }

    /**
     * 清除指定实体的所有持续伤害效果
     *
     * @param target 目标实体
     */
    public static void clearEntity(@Nonnull LivingEntity target) {
        int targetId = target.getId();
        ACTIVE_DOTS.removeIf(dot -> dot.targetId == targetId);
        PENDING.removeIf(dot -> dot.targetId == targetId);
    }

    /**
     * 清除所有持续伤害效果（服务器关闭时调用）
     */
    public static void clearAll() {
        ACTIVE_DOTS.clear();
        PENDING.clear();
    }

    /**
     * 获取活跃效果数量（调试用）
     *
     * @return 活跃效果数量
     */
    public static int getActiveCount() {
        return ACTIVE_DOTS.size();
    }

    /**
     * 获取统计信息（调试用）
     *
     * @return 统计字符串
     */
    public static String getStats() {
        return String.format("[DoT管理器] 活跃: %d | 待添加: %d", ACTIVE_DOTS.size(), PENDING.size());
    }

    // ==================== 内部方法 ====================

    /**
     * 添加效果条目
     */
    private static void addEntry(@Nonnull DoTEntry entry) {
        if (iterating) {
            PENDING.add(entry);
        } else {
            ACTIVE_DOTS.add(entry);
        }
    }

    // ==================== ServerTick 处理 ====================

    /**
     * 每tick处理所有活跃的持续伤害效果
     */
    @SubscribeEvent
    public static void onServerTick(@Nonnull TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        if (ACTIVE_DOTS.isEmpty() && PENDING.isEmpty()) {
            return;
        }

        // 先合并 pending
        if (!PENDING.isEmpty()) {
            ACTIVE_DOTS.addAll(PENDING);
            PENDING.clear();
        }

        iterating = true;
        try {
            Iterator<DoTEntry> iterator = ACTIVE_DOTS.iterator();
            while (iterator.hasNext()) {
                DoTEntry dot = iterator.next();
                if (dot.tick()) {
                    iterator.remove();
                }
            }
        } finally {
            iterating = false;
        }

        // 遍历期间可能有新的效果通过 kill -> DeathEvent -> 附魔逻辑 加入 pending
        if (!PENDING.isEmpty()) {
            ACTIVE_DOTS.addAll(PENDING);
            PENDING.clear();
        }
    }

    // ==================== 自损致死（v1.2） ====================

    /**
     * 记录致命一击有没有走到死亡判定
     * <p>
     * 最低优先级 + 接收已取消事件：前面的复活类监听取消了死亡也照样记上，
     * 这样被复活的实体不会再被补刀。
     * </p>
     *
     * @param event 死亡事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDeath(@Nonnull LivingDeathEvent event) {
        if (lethalTarget != null && event.getEntity() == lethalTarget) {
            lethalDeathPosted = true;
        }
    }

    /**
     * 自损 DoT 的致死一击：走原版 {@code hurt()}，拦不住时退回直接处决
     * <p>
     * 用伤害源副本而不是原对象挂临时标签，退回 {@link EntityLivingUtil#kill} 时
     * 死亡事件里的伤害源与改动前完全一样。做法与边界情况见类注释 v1.2。
     * </p>
     *
     * @param target 目标
     * @param source 条目登记时的伤害源
     */
    private static void killThroughDamagePipeline(@Nonnull LivingEntity target, @Nonnull DamageSource source) {
        DamageSource lethal = new DamageSource(source.typeHolder(), source.getDirectEntity(),
                source.getEntity(), source.sourcePositionRaw());
        DamageSourceUtil.setBypassesInvulnerability(lethal);
        DamageSourceUtil.setBypassesCooldown(lethal);
        DamageSourceUtil.setBypassesArmor(lethal);
        DamageSourceUtil.setBypassesEffects(lethal);
        DamageSourceUtil.setBypassesShield(lethal);

        lethalTarget = target;
        lethalSource = lethal;
        lethalDeathPosted = false;
        try {
            target.hurt(lethal, target.getMaxHealth() * SELF_LETHAL_DAMAGE_MULTIPLIER);
        } finally {
            lethalTarget = null;
            lethalSource = null;
            discardEntriesFrom(lethal);
        }

        // 没走到死亡判定（被取消 / 被减到不致命 / 免疫这类伤害）：按原来的方式处决，结果不变
        if (!lethalDeathPosted && target.isAlive()) {
            EntityLivingUtil.kill(target, source);
        }
    }

    /**
     * 当前是不是自损 DoT 的致命一击（v1.2）
     * <p>
     * 这一击的伤害源挂着「无视无敌」，好让免伤附魔不介入；但死亡触发类附魔（猩红腐败的洛尼亚）
     * 在直接处决时一直是会触发的，要用这个方法把这一击认出来，别把它当成 /kill 跳过。
     * </p>
     *
     * @param source 死亡事件里的伤害源
     * @return 是这一击的伤害源副本时为 true
     */
    public static boolean isSelfLethalBlow(@Nullable DamageSource source) {
        return source != null && source == lethalSource;
    }

    /**
     * 丢掉以致命一击的伤害源副本登记的持续伤害
     * <p>
     * 受击侧附魔会把这一击转成持续伤害（战士：一半伤害 60 tick 内扣完），伤害源就是这个副本。
     * 直接处决时根本不会有这些条目；留着的话，被满月、死诞者、时间逆转复活的持有者，
     * 会在接下来几 tick 里被按「10 倍最大生命」折算出来的残留伤害再杀一次。
     * 这一击发生在 {@link #onServerTick} 遍历期间，新条目都还在 {@link #PENDING} 里。
     * </p>
     *
     * @param lethal 致命一击的伤害源副本
     */
    private static void discardEntriesFrom(@Nonnull DamageSource lethal) {
        PENDING.removeIf(dot -> dot.source == lethal);
        if (!iterating) {
            ACTIVE_DOTS.removeIf(dot -> dot.source == lethal);
        }
    }

    // ==================== DoT 效果条目 ====================

    /**
     * 持续伤害效果条目
     * <p>
     * 轻量级普通类，无闭包捕获。每个实例约56字节。
     * 对比匿名SynchronizationTask子类约200+字节。
     * </p>
     */
    private static class DoTEntry {
        final int targetId;
        final LivingEntity target;
        final float baseDamagePerTick;
        final DamageSource source;
        final boolean canKill;
        @Nullable
        final BiFunction<Float, Integer, Float> scalingFunction;
        /**
         * 来源标签（v1.1 新增）
         * <p>用于把共用同一个池子的七个附魔区分开，供
         * {@link #getRemainingDamage} 按来源查询。null 表示不参与查询。</p>
         */
        @Nullable
        final String tag;
        /**
         * 是否为自损（v1.2 新增）
         * <p>true 时致死一击走 {@link #killThroughDamagePipeline}，否则仍直接处决。</p>
         */
        final boolean selfInflicted;

        int remainingDelay;
        int remainingTicks;
        int elapsedDamageTicks;

        DoTEntry(@Nonnull LivingEntity target, float baseDamagePerTick, int durationTicks,
                 int initialDelay, @Nonnull DamageSource source, boolean canKill,
                 @Nullable BiFunction<Float, Integer, Float> scalingFunction,
                 @Nullable String tag, boolean selfInflicted) {
            this.targetId = target.getId();
            this.target = target;
            this.baseDamagePerTick = baseDamagePerTick;
            this.remainingDelay = initialDelay;
            this.remainingTicks = durationTicks;
            this.source = source;
            this.canKill = canKill;
            this.scalingFunction = scalingFunction;
            this.tag = tag;
            this.selfInflicted = selfInflicted;
            this.elapsedDamageTicks = 0;
        }

        /**
         * 本条目尚未结算的伤害总量（v1.1 新增）
         * <p>对线性条目为精确值；对缩放条目为按基础速率的估算。</p>
         *
         * @return 剩余伤害；已结束时为 0
         */
        float remainingDamage() {
            if (remainingTicks <= 0 || baseDamagePerTick <= 0f) {
                return 0f;
            }
            return baseDamagePerTick * remainingTicks;
        }

        /**
         * 每tick处理
         *
         * @return true=效果结束需移除
         */
        boolean tick() {
            // 目标已死亡或被移除
            if (!target.isAlive() || target.isRemoved()) {
                return true;
            }

            // 初始延迟
            if (remainingDelay > 0) {
                remainingDelay--;
                return false;
            }

            // 持续时间结束
            if (remainingTicks <= 0) {
                return true;
            }

            remainingTicks--;
            elapsedDamageTicks++;

            // 计算伤害
            float damage;
            if (scalingFunction != null) {
                damage = scalingFunction.apply(baseDamagePerTick, elapsedDamageTicks);
            } else {
                damage = baseDamagePerTick;
            }

            if (damage <= 0) {
                return false;
            }

            // 应用伤害
            if (canKill && target.getHealth() - damage * 2 <= 0) {
                if (selfInflicted) {
                    killThroughDamagePipeline(target, source);
                } else {
                    EntityLivingUtil.kill(target, source);
                }
                return true;
            } else {
                EntityLivingUtil.damageHealthDirectly(target, damage);
            }

            return false;
        }
    }
}
