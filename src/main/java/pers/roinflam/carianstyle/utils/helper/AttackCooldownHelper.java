package pers.roinflam.carianstyle.utils.helper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 攻击冷却（蓄力）的延后清零
 *
 * <h3>问题：在伤害事件里直接清零会废掉同一下挥击里的其它附魔</h3>
 * <p>
 * 水鸟乱舞、二连斩、居合过了蓄力判断以后都会调用 {@code player.resetAttackStrengthTicker()}，
 * 用来「消耗」这一下的蓄力。但它们是在 LivingHurtEvent 里调的——同一下挥击的事件链还没走完，
 * 排在它们后面的所有蓄力判断（例如命定之死在 LOWEST 读蓄力）读到的已经是清零后的约 0.04，
 * 直接 return。表现就是「带水鸟乱舞 / 二连斩 / 居合的武器打不出命定之死」，普通武器和拔刀剑都一样。
 * </p>
 * <p>
 * Forge 47.4.13 已经把 {@code Player#attack} 里的 {@code resetAttackStrengthTicker()} 挪到了方法最末尾，
 * 整条事件链（AttackEntityEvent、CriticalHitEvent、LivingAttack/Hurt/Damage/Death/Drops 各优先级）
 * 读到的本来都是出手前的蓄力值；是附魔自己在链条中途清零才把后面的判断搞坏的。
 * 拔刀剑这类不经过 {@code Player#attack}、以玩家为攻击者直接调 {@code hurt} 的武器，原版根本不会清零，
 * 中途清零造成的影响完全一样。
 * </p>
 *
 * <h3>为什么这么改</h3>
 * <p>
 * 清零本身有用：水鸟乱舞的补刀段会在之后的 tick 里用同一个伤害源再调 {@code victim.hurt}，
 * 靠清零让补刀段读到低蓄力，才不会把被蓄力拦着的附魔（命定之死等）每段都再触发一遍。
 * 所以不是去掉清零，而是把它挪到「这一下挥击的事件链全部结束之后、补刀段开始之前」：
 * </p>
 * <ul>
 *   <li>攻击包是在两个 tick 之间的任务队列里处理的，拔刀剑的攻击发生在实体 / 玩家 tick 里，
 *       无论哪种，到下一个 tick 的 {@code ServerTickEvent} START 时这一下的事件链都已经走完；</li>
 *   <li>水鸟乱舞的补刀段由 {@code SynchronizationTask} 驱动，它和 {@code DamageOverTimeManager}
 *       都挂在 START 的默认优先级（NORMAL）上。这里用 HIGHEST，保证清零先于第一段补刀生效。</li>
 * </ul>
 *
 * <h3>行为影响</h3>
 * <ul>
 *   <li>同一下挥击里，所有附魔读到的都是同一个出手前的蓄力值，不再受附魔排列顺序影响；</li>
 *   <li>原版横扫的副目标、拔刀剑一次打中的多个目标，和主目标在同一条链里，
 *       现在也能过蓄力判断——与武器上没有这三个附魔时的表现一致；
 *       水鸟乱舞自己靠攻击者身上的标记防重入，不会在副目标上再拆一次；
 *       二连斩、居合靠 {@link #spendSwing} 保持「一下挥击只结算一次」（只算第一个目标），与立即清零时的表现一致；</li>
 *   <li>补刀段读到的蓄力与之前相同（清零发生在第一段之前）；</li>
 *   <li>玩家的蓄力最多晚 1 tick 开始重新累积（攻击发生在本 tick 的玩家 tick 之前时）。</li>
 * </ul>
 *
 * <h3>线程与清理</h3>
 * <p>
 * 只在服务端生效，客户端调用直接忽略。队列只在服务端主线程读写，不在主线程上的调用会转交给服务端线程。
 * 同一玩家在一个 tick 内登记多次只清零一次。队列每个 tick 清空，不会长期持有玩家对象：
 * 下线的玩家会立刻移出队列；已被移出世界的玩家对象（{@code isRemoved()}，例如重生或从末地回主世界
 * 之后被换掉的旧对象）处理时跳过；死亡还没重生的玩家清零也没有副作用，重生后是新的玩家对象，冷却从零开始；
 * 普通换维度沿用同一个玩家对象，清零照常生效。
 * </p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AttackCooldownHelper {

    /** 等待清零的玩家，按对象身份去重；只在服务端主线程读写 */
    private static final Set<Player> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());

    /** 本 tick 内已经「消耗」过这一下挥击的玩家（见 {@link #spendSwing}）；只在服务端主线程读写 */
    private static final Set<Player> SPENT = Collections.newSetFromMap(new IdentityHashMap<>());

    private AttackCooldownHelper() {
    }

    /**
     * 消耗这一下挥击：每个 tick 内每名玩家只有第一次调用返回 true，同时登记下一 tick 开头清零
     * <p>
     * 二连斩、居合这类「过了蓄力判断就把这一下用掉」的战技用它。以前它们是在伤害事件里直接清零冷却，
     * 靠清零顺带保证了「一下挥击只结算一次」——横扫的副目标、箭步回旋斩的范围伤害、
     * 拔刀剑一次打中的多个目标、主副手都带同一附魔的重复分发，到第二次都过不了蓄力判断。
     * 清零延后以后这个副作用没了，所以改成显式记一笔：本 tick 已经消耗过就返回 false，调用方直接放弃。
     * 标记和清零队列在同一个时点（下一 tick 的 START HIGHEST）一起清掉。
     * </p>
     * <p>
     * 只管「同一下挥击只结算一次」，不影响别的附魔读到的蓄力值。客户端调用恒返回 true、什么也不做。
     * </p>
     *
     * @param player 玩家
     * @return 这是本 tick 第一次消耗；false 表示这一下已经被别的结算用掉了
     */
    public static boolean spendSwing(@Nonnull Player player) {
        if (player.level().isClientSide) {
            return true;
        }
        MinecraftServer server = player.getServer();
        if (server != null && !server.isSameThread()) {
            resetNextTick(player);
            return true;
        }
        if (!SPENT.add(player)) {
            return false;
        }
        PENDING.add(player);
        return true;
    }

    /**
     * 在下一个服务端 tick 开头清零玩家的攻击冷却
     * <p>
     * 用来替代在伤害事件里直接调用 {@code player.resetAttackStrengthTicker()}：
     * 本次挥击后续的蓄力判断仍读到出手前的值，下一 tick 起（包括水鸟乱舞的补刀段）读到的是清零后的值。
     * </p>
     *
     * @param player 玩家
     */
    public static void resetNextTick(@Nonnull Player player) {
        if (player.level().isClientSide) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server != null && !server.isSameThread()) {
            server.execute(() -> resetNextTick(player));
            return;
        }
        PENDING.add(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTick(@Nonnull TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        SPENT.clear();
        if (PENDING.isEmpty()) {
            return;
        }
        Player[] players = PENDING.toArray(new Player[0]);
        PENDING.clear();
        for (Player player : players) {
            if (!player.isRemoved()) {
                player.resetAttackStrengthTicker();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(@Nonnull PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity());
        SPENT.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopped(@Nonnull ServerStoppedEvent event) {
        PENDING.clear();
        SPENT.clear();
    }
}
