package pers.roinflam.carianstyle.dynamicattr.clientsync;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;

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
 * {@code MobEffectEvent.Remove / Expired}，各 SyncHandler 收不到任何通知。
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
 * @version 1.1
 */
@Mod.EventBusSubscriber
public class ClientSyncEffectEventHandler {

    /**
     * 玩家登录时同步当前维度的所有客户端效果
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClientSyncEffectManager.syncDimensionToPlayer(player);
        }
    }

    /**
     * 玩家登出时清理其残留的同步条目
     * <p>
     * v1.1新增：动态属性类（火焰 / 隐身）由 {@link #onEntityLeaveLevel} 的
     * {@code clearAll} 顺带清掉，但 MobEffect 类的同步条目没有任何出口，
     * 只能靠 5 秒一次的重同步剪枝。这里主动清一次，避免离线玩家 ID 滞留。
     * </p>
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClientSyncEffectManager.removeAllForEntity(player);
        }
    }

    /**
     * 玩家切换维度时同步新维度的客户端效果
     */
    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 清除客户端旧维度的缓存（通过发送空列表）
            // 然后同步新维度的状态
            ClientSyncEffectManager.syncDimensionToPlayer(player);
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
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClientSyncEffectManager.removeAllForEntity(player);
            ClientSyncEffectManager.syncDimensionToPlayer(player);
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
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        // ⭐ v1.1：先清掉该实体在全部序列号下的同步条目（MobEffect 类唯一的清理入口）
        ClientSyncEffectManager.removeAllForEntity(event.getEntity());

        // 清除该实体的所有动态属性（会触发移除回调，顺带清掉火焰 / 隐身的同步条目）
        DynamicAttributeManager.clearAll(event.getEntity());
    }

    /**
     * 实体从世界中移除时清理
     * 包括实体卸载、维度切换等情况
     */
    @SubscribeEvent
    public static void onEntityLeaveLevel(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
            if (!living.level().isClientSide()) {
                // 清除该实体的所有动态属性
                DynamicAttributeManager.clearAll(living);
            }
        }
    }
}
