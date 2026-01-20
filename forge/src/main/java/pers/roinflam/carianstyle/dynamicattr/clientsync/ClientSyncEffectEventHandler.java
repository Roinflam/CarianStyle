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
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClientSyncEffectManager.syncDimensionToPlayer(player);
        }
    }

    /**
     * 实体死亡时清理客户端效果
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        // 清除该实体的所有动态属性（会触发移除回调）
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