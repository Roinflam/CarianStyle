package pers.roinflam.carianstyle.utils.helper;

import com.google.common.collect.MapMaker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 攻击冷却（蓄力）的延后清零
 *
 * <h3>问题：在伤害事件里直接清零会废掉同一下挥击里的其它附魔</h3>
 * <p>
 * 水鸟乱舞、二连斩、居合过了蓄力判断以后都会调用 {@code player.resetCooldown()}，
 * 用来「消耗」这一下的蓄力。但它们是在 LivingHurtEvent 里调的——同一下挥击的事件链还没走完，
 * 排在它们后面的所有蓄力判断（例如命定之死在 LOWEST 读蓄力）读到的已经是清零后的约 0.04，
 * 直接 return。表现就是「带水鸟乱舞 / 二连斩 / 居合的武器打不出命定之死」，普通武器和拔刀剑都一样。
 * </p>
 * <p>
 * 1.20.1 的 Forge 把 {@code Player#attack} 里的清零挪到了方法最末尾，整条事件链读到的都是出手前的蓄力值。
 * 1.12.2 版：{@code attackTargetEntityWithCurrentItem} 在 {@code attackEntityFrom} <b>之前</b>就 {@code resetCooldown()}，
 * 受伤事件里直接读 {@code getCooledAttackStrength} 永远是 0。所以本类另外记一份「出手前的蓄力」快照
 * （{@link #getChargeSnapshot}），{@code EnchantmentBase.isFullyCharged} 读它：
 * </p>
 * <ul>
 *   <li>{@code PlayerTickEvent} END 记一次：拔刀剑这类不经过 {@code attackTargetEntityWithCurrentItem}、
 *       直接以玩家为攻击者调 {@code attackEntityFrom} 的武器，读到的是上一次玩家 tick 结束时的蓄力；</li>
 *   <li>{@code AttackEntityEvent} 再刷新一次：这个事件在原版清零之前发出，记下的就是这一下挥击原版自己用的蓄力值，
 *       同一 tick 里连打两下时第二下读到的也是清零后的值，不会沿用上一次 tick 的满蓄力；</li>
 *   <li>延后清零（下面）执行之后跟着刷新，补刀段读到的是清零后的值。</li>
 * </ul>
 * <p>
 * 附魔自己在链条中途清零同样会把后面的判断搞坏（1.12.2 的快照不受清零影响，但补刀段要读到低蓄力），所以延后清零照 1.20.1 保留。
 * </p>
 *
 * <h3>为什么这么改</h3>
 * <p>
 * 清零本身有用：水鸟乱舞的补刀段会在之后的 tick 里用同一个伤害源再调 {@code victim.attackEntityFrom}，
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
 * 下线的玩家会立刻移出队列；已被移出世界的玩家对象（{@code isDead}，例如重生或从末地回主世界
 * 之后被换掉的旧对象）处理时跳过；死亡还没重生的玩家清零也没有副作用，重生后是新的玩家对象，冷却从零开始；
 * 普通换维度沿用同一个玩家对象，清零照常生效。
 * </p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class AttackCooldownHelper {

    /** 等待清零的玩家，按对象身份去重；只在服务端主线程读写 */
    private static final Set<EntityPlayer> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());

    /** 本 tick 内已经「消耗」过这一下挥击的玩家（见 {@link #spendSwing}）；只在服务端主线程读写 */
    private static final Set<EntityPlayer> SPENT = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * 1.12.2 版：玩家 → 出手前的蓄力（0~1）。弱键、按对象身份比较，下线 / 重生换掉的旧玩家对象自动释放；
     * 只在服务端主线程读写。
     */
    private static final Map<EntityPlayer, Float> CHARGE = new MapMaker().weakKeys().makeMap();

    private AttackCooldownHelper() {
    }

    /**
     * 1.12.2 版：这一下挥击出手前的蓄力（0~1）
     * <p>
     * 服务端读快照（见类注释），没有记录时按满蓄力算（1.0），和第一期一致；客户端直接读当前值。
     * </p>
     *
     * @param player 玩家
     * @return 蓄力
     */
    public static float getChargeSnapshot(@Nonnull EntityPlayer player) {
        if (player.world.isRemote) {
            return player.getCooledAttackStrength(0.5F);
        }
        Float charge = CHARGE.get(player);
        return charge != null ? charge : 1.0F;
    }

    private static void snapshot(@Nonnull EntityPlayer player) {
        CHARGE.put(player, player.getCooledAttackStrength(0.5F));
    }

    /**
     * 每次玩家 tick 结束记一次蓄力
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        snapshot(event.player);
    }

    /**
     * 原版挥击清零之前再记一次，取最高优先级，赶在其它模组取消事件之前
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(@Nonnull AttackEntityEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) {
            return;
        }
        snapshot(player);
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
    public static boolean spendSwing(@Nonnull EntityPlayer player) {
        if (player.world.isRemote) {
            return true;
        }
        MinecraftServer server = player.getServer();
        if (server != null && !server.isCallingFromMinecraftThread()) {
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
     * 用来替代在伤害事件里直接调用 {@code player.resetCooldown()}：
     * 本次挥击后续的蓄力判断仍读到出手前的值，下一 tick 起（包括水鸟乱舞的补刀段）读到的是清零后的值。
     * </p>
     *
     * @param player 玩家
     */
    public static void resetNextTick(@Nonnull EntityPlayer player) {
        if (player.world.isRemote) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server != null && !server.isCallingFromMinecraftThread()) {
            server.addScheduledTask(() -> resetNextTick(player));
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
        EntityPlayer[] players = PENDING.toArray(new EntityPlayer[0]);
        PENDING.clear();
        for (EntityPlayer player : players) {
            if (!player.isDead) {
                player.resetCooldown();
                snapshot(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(@Nonnull PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.player);
        SPENT.remove(event.player);
        CHARGE.remove(event.player);
    }

    /**
     * 停服清理
     * <p>
     * 1.12.2 版：{@code FMLServerStoppedEvent} 只发给模组主类，这里不订阅，由 {@code ServerLifecycle} 转调。
     * </p>
     *
     * @param event FML 停服事件
     */
    public static void onServerStopped(@Nonnull FMLServerStoppedEvent event) {
        PENDING.clear();
        SPENT.clear();
        CHARGE.clear();
    }
}
