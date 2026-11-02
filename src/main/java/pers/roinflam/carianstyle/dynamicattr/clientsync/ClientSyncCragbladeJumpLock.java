package pers.roinflam.carianstyle.dynamicattr.clientsync;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 岩石剑的「无法跳跃」在客户端的那一半
 * <p>
 * 玩家的跳跃是客户端 {@code onLivingUpdate} 发起的，服务端设跳跃冷却拦不住。
 * 这里查序列号 20，在玩家 tick 开头（{@code onLivingUpdate} 之前）把本地玩家的跳跃冷却顶住。
 * </p>
 * <p>
 * 1.12.2 版：多一道起跳事件的兜底。万一还是跳起来了（比如别的模组直接调起跳），
 * 在 {@code LivingJumpEvent} 里把起跳速度和疾跑起跳的水平加速撤掉。
 * </p>
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ClientSyncCragbladeJumpLock {

    public static final int CRAGBLADE_SERIAL = 20;

    private ClientSyncCragbladeJumpLock() {
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.side != Side.CLIENT) {
            return;
        }
        if (isLocked(event.player)) {
            EntityLivingUtil.setJumped(event.player);
        }
    }

    /**
     * 起跳兜底：起跳速度压回 0，疾跑起跳加的水平速度反着减回去
     */
    @SubscribeEvent
    public static void onLivingJump(@Nonnull LivingEvent.LivingJumpEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (!entity.world.isRemote || !isLocked(entity)) {
            return;
        }
        entity.motionY = Math.min(entity.motionY, 0.0D);
        if (entity.isSprinting()) {
            // 原版疾跑起跳会顺着朝向加 0.2 的水平速度
            float yaw = entity.rotationYaw * 0.017453292F;
            entity.motionX += MathHelper.sin(yaw) * 0.2F;
            entity.motionZ -= MathHelper.cos(yaw) * 0.2F;
        }
    }

    /**
     * 是不是本地玩家、并且服务端同步过来说它正被岩石剑锁跳
     */
    private static boolean isLocked(@Nonnull EntityLivingBase entity) {
        return entity == Minecraft.getMinecraft().player
                && ClientSyncEffectManager.shouldRenderEffect(CRAGBLADE_SERIAL, entity.getEntityId());
    }
}
