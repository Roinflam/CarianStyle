package pers.roinflam.carianstyle.dynamicattr.clientsync;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.mc112.util.EntityLeaveTracker;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 客户端同步效果事件监听器
 * 处理多人游戏中的同步和清理逻辑
 *
 * <h3>v1.1 修复：实体死亡后特效残留（玩家复活后仍显示）</h3>
 * <p>
 * 原实现里，{@link #onLivingDeath} 只调用了
 * {@link DynamicAttributeManager#clearAll}——它能通过移除回调清掉
 * <b>基于动态属性</b>的同步（自定义火焰 1~3、隐身 4），
 * 但<b>基于 MobEffect</b> 的同步（猩红腐败 5、冻伤 7、出血 8、切腹 9、睡眠 10、噩兆 11）
 * 完全没有死亡清理入口——因为实体死亡时 Forge 不会补发
 * {@code PotionRemoveEvent / PotionExpiryEvent}，各 SyncHandler 收不到任何通知。
 * </p>
 * <p>
 * 又因为<b>玩家复活后实体网络 ID 不变且重新存活</b>，
 * {@code ClientSyncEffectManager} 里 5 秒一次重同步的
 * {@code e == null || !e.isAlive()} 剪枝也拦不住，残留条目于是永久生效。
 * 最典型的表现就是「切腹（大灭）触发期间被自己耗死，复活后刀痕 / 血刃碎片一直挂着」。
 * </p>
 * <p>
 * 本次在<b>死亡 / 复活 / 登出</b>三处调用
 * {@link ClientSyncEffectManager#removeAllForEntity} 主动清除：
 * </p>
 * <ul>
 *     <li><b>死亡</b>——主修复点，最早、最准确的清理时机；</li>
 *     <li><b>复活</b>——兜底。死亡事件可能被 满月 / 死诞者 / 时间逆转
 *         在 HIGHEST 优先级取消（此时本监听器收不到事件），
 *         但那些情况玩家并没有真的死、也不会走复活流程；
 *         真正需要兜底的是其它 mod 抢先取消 / 改写死亡流程导致漏清的路径。
 *         复活后的玩家必定是全新实体、身上不带任何效果，无条件清空是安全的；</li>
 *     <li><b>登出</b>——防止离线玩家的实体 ID 长期滞留在服务端集合中被反复全量广播。</li>
 * </ul>
 * <p>
 * {@code removeAllForEntity} 对不在集合中的实体是零开销空操作，
 * 与 {@code DynamicAttributeManager.clearAll} 的移除回调叠加调用也不会重复广播。
 * </p>
 *
 * <h3>1.12.2 版</h3>
 * <ul>
 *     <li>1.12.2 没有 {@code EntityLeaveLevelEvent}：「实体离开世界」的清理改成类加载时往
 *         {@link EntityLeaveTracker} 登记一个监听器，处理体照搬 1.20.1 的 {@code onEntityLeaveLevel}。
 *         区块卸载、换维度、下线、死亡移除、整个世界卸载都会走到。</li>
 *     <li>为了保证第一个实体离开之前监听器已经挂上，订阅了 {@code WorldEvent.Load}：
 *         静态订阅类要等第一次收到事件才初始化，世界加载是最早的一个。</li>
 *     <li>登录 / 登出 / 换维度 / 重生是 FML 的 {@code PlayerEvent}（{@code event.player}）。</li>
 * </ul>
 *
 * @version 1.1
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class ClientSyncEffectEventHandler {

    static {
        EntityLeaveTracker.addListener(ClientSyncEffectEventHandler::onEntityLeaveLevel);
    }

    /**
     * 世界加载：什么都不做，只为让本类在第一个实体离开世界之前初始化、挂上离开监听器（见类注释）
     */
    @SubscribeEvent
    public static void onWorldLoad(@Nonnull WorldEvent.Load event) {
        // 静态块已经在本方法第一次被调用之前跑过
    }

    /**
     * 玩家登录时同步当前维度的所有客户端效果
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(@Nonnull PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            ClientSyncEffectManager.syncDimensionToPlayer((EntityPlayerMP) event.player);
        }
    }

    /**
     * 玩家登出时清理其残留的同步条目
     * <p>
     * v1.1新增：动态属性类（火焰 / 隐身）由 {@link #onEntityLeaveLevel} 的
     * {@code clearAll} 顺带清掉，但 MobEffect 类的同步条目没有任何出口，
     * 只能靠 5 秒一次的重同步剪枝。这里主动清一次，避免离线玩家 ID 滞留。
     * </p>
     * <p>
     * 1.12.2 版：登出事件在玩家从世界移除之前发；动态属性随后由离开监听器清掉
     * （修饰符不存档，先写存档也不会把它们存下来）。
     * </p>
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(@Nonnull PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            ClientSyncEffectManager.removeAllForEntity(event.player);
        }
    }

    /**
     * 玩家切换维度时同步新维度的客户端效果
     */
    @SubscribeEvent
    public static void onPlayerChangeDimension(@Nonnull PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            // 清除客户端旧维度的缓存（通过发送空列表）
            // 然后同步新维度的状态
            ClientSyncEffectManager.syncDimensionToPlayer((EntityPlayerMP) event.player);
        }
    }

    /**
     * 玩家重生时重新同步
     * <p>
     * v1.1新增：先清除该玩家 ID 上的全部残留条目，再做全量同步。
     * 复活后玩家是全新实体、身上不带任何效果，
     * 但实体网络 ID 与死亡前完全相同，不主动清就会把旧条目原样同步回去。
     * </p>
     */
    @SubscribeEvent
    public static void onPlayerRespawn(@Nonnull PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            ClientSyncEffectManager.removeAllForEntity(event.player);
            ClientSyncEffectManager.syncDimensionToPlayer((EntityPlayerMP) event.player);
        }
    }

    /**
     * 实体死亡时清理客户端效果
     * <p>
     * v1.1新增：{@code removeAllForEntity} 覆盖全部序列号（含 MobEffect 类），
     * 是「死亡后特效残留」的主修复点，详见类注释。
     * </p>
     */
    @SubscribeEvent
    public static void onLivingDeath(@Nonnull LivingDeathEvent event) {
        if (event.getEntityLiving().world.isRemote) {
            return;
        }

        // v1.1：先清掉该实体在全部序列号下的同步条目（MobEffect 类唯一的清理入口）
        ClientSyncEffectManager.removeAllForEntity(event.getEntityLiving());

        // 清除该实体的所有动态属性（会触发移除回调，顺带清掉火焰 / 隐身的同步条目）
        DynamicAttributeManager.clearAll(event.getEntityLiving());
    }

    /**
     * 实体从世界中移除时清理
     * 包括实体卸载、维度切换等情况
     * <p>1.12.2 版：由 {@link EntityLeaveTracker} 调用（见类注释）。</p>
     *
     * @param entity 离开世界的实体
     */
    public static void onEntityLeaveLevel(@Nonnull Entity entity) {
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!living.world.isRemote) {
                // 清除该实体的所有动态属性
                DynamicAttributeManager.clearAll(living);
            }
        }
    }
}
