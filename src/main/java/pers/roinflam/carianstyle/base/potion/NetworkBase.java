package pers.roinflam.carianstyle.base.potion;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;
import pers.roinflam.carianstyle.network.NetworkRegistryHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网络同步药水基类
 * <p>
 * 用于需要客户端同步渲染效果的药水（如自定义火焰、隐身等）
 * 优化：只在效果添加/移除/玩家加入时发送网络包
 * </p>
 */
public abstract class NetworkBase extends HideBase {

    /**
     * 全局：记录每个维度中哪些实体有此效果
     * 维度ID -> 实体ID集合
     */
    private final Map<Integer, Set<Integer>> activatedEntities = new ConcurrentHashMap<>();

    protected NetworkBase(boolean isBadEffectIn, int liquidColorIn, String name) {
        super(isBadEffectIn, liquidColorIn, name);
    }

    /**
     * 获取指定维度的激活实体集合
     */
    private Set<Integer> getActivatedInDimension(int dimension) {
        return activatedEntities.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet());
    }

    /**
     * 药水效果被添加时同步到客户端
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPotionAdded(@Nonnull PotionEvent.PotionAddedEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!evt.getPotionEffect().getPotion().equals(this)) {
            return;
        }

        EntityLivingBase entity = evt.getEntityLiving();
        int dimension = entity.world.provider.getDimension();
        int entityId = entity.getEntityId();

        Set<Integer> activated = getActivatedInDimension(dimension);

        // 避免重复同步
        if (activated.contains(entityId)) {
            return;
        }

        activated.add(entityId);
        broadcastToPlayersInDimension(entity.world, entityId, true);
    }

    /**
     * 药水效果被手动移除时同步到客户端
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPotionRemove(@Nonnull PotionEvent.PotionRemoveEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!evt.getPotion().equals(this)) {
            return;
        }

        EntityLivingBase entity = evt.getEntityLiving();
        int dimension = entity.world.provider.getDimension();

        Set<Integer> activated = getActivatedInDimension(dimension);
        if (activated.remove(entity.getEntityId())) {
            broadcastToPlayersInDimension(entity.world, entity.getEntityId(), false);
        }
    }

    /**
     * 药水效果自然过期时同步到客户端
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPotionExpiry(@Nonnull PotionEvent.PotionExpiryEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getPotionEffect() == null || !evt.getPotionEffect().getPotion().equals(this)) {
            return;
        }

        EntityLivingBase entity = evt.getEntityLiving();
        int dimension = entity.world.provider.getDimension();

        Set<Integer> activated = getActivatedInDimension(dimension);
        if (activated.remove(entity.getEntityId())) {
            broadcastToPlayersInDimension(entity.world, entity.getEntityId(), false);
        }
    }

    /**
     * 实体死亡时清理同步状态
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase entity = evt.getEntityLiving();
        int dimension = entity.world.provider.getDimension();

        Set<Integer> activated = getActivatedInDimension(dimension);
        if (activated.remove(entity.getEntityId())) {
            broadcastToPlayersInDimension(entity.world, entity.getEntityId(), false);
        }
    }

    /**
     * 玩家加入服务器时：同步该维度所有已激活效果
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(@Nonnull PlayerEvent.PlayerLoggedInEvent evt) {
        if (evt.player.world.isRemote) {
            return;
        }

        syncAllToPlayer(evt.player);
    }

    /**
     * 玩家切换维度时：同步新维度的所有已激活效果
     */
    @SubscribeEvent
    public void onPlayerChangedDimension(@Nonnull PlayerEvent.PlayerChangedDimensionEvent evt) {
        if (evt.player.world.isRemote) {
            return;
        }

        // 清除旧维度的渲染状态
        Set<Integer> oldActivated = getActivatedInDimension(evt.fromDim);
        for (Integer entityId : oldActivated) {
            NetworkRegistryHandler.RenderingEffect.sendClientCustomPacket(
                    getSerialNumber(), evt.player, entityId, false);
        }

        // 同步新维度的所有效果
        syncAllToPlayer(evt.player);
    }

    /**
     * 玩家重生时：同步该维度所有已激活效果
     */
    @SubscribeEvent
    public void onPlayerRespawn(@Nonnull PlayerEvent.PlayerRespawnEvent evt) {
        if (evt.player.world.isRemote) {
            return;
        }

        syncAllToPlayer(evt.player);
    }

    /**
     * 向单个玩家同步该维度所有已激活的效果
     */
    private void syncAllToPlayer(@Nonnull EntityPlayer player) {
        int dimension = player.world.provider.getDimension();
        Set<Integer> activated = getActivatedInDimension(dimension);

        for (Integer entityId : activated) {
            // 验证实体仍然存在且仍有该效果
            Entity entity = player.world.getEntityByID(entityId);
            if (entity instanceof EntityLivingBase) {
                EntityLivingBase living = (EntityLivingBase) entity;
                if (living.isPotionActive(this)) {
                    NetworkRegistryHandler.RenderingEffect.sendClientCustomPacket(
                            getSerialNumber(), player, entityId, true);
                } else {
                    // 实体不再有该效果，清理记录
                    activated.remove(entityId);
                }
            } else {
                // 实体不存在了，清理记录
                activated.remove(entityId);
            }
        }
    }

    /**
     * 向同维度的所有玩家广播状态
     */
    private void broadcastToPlayersInDimension(@Nonnull net.minecraft.world.World world,
                                               int entityId, boolean add) {
        @Nullable MinecraftServer server = world.getMinecraftServer();
        if (server == null) {
            return;
        }

        int dimension = world.provider.getDimension();

        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player.dimension == dimension) {
                NetworkRegistryHandler.RenderingEffect.sendClientCustomPacket(
                        getSerialNumber(), player, entityId, add);
            }
        }
    }

    /**
     * 药水效果触发（不用于网络同步）
     */
    @Override
    public void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
        // 网络同步已移到事件监听器中
    }

    /**
     * 获取效果的序列号（用于区分不同类型的渲染效果）
     */
    public abstract int getSerialNumber();

    /**
     * 获取当前激活该效果的实体ID列表（客户端用）
     */
    public List<Integer> getActionPotion() {
        return NetworkRegistryHandler.RenderingEffect.getEntitiesID(getSerialNumber());
    }

    /**
     * 检查指定实体是否激活了该效果（客户端用）
     */
    public boolean isAction(int entityId) {
        return getActionPotion().contains(entityId);
    }
}