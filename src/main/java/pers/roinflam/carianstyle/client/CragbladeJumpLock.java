package pers.roinflam.carianstyle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 岩石剑的「无法跳跃」在客户端的那一半
 * <p>
 * 玩家的跳跃是客户端自己算的，服务端设跳跃冷却拦不住。岩石剑的效果是真药水，
 * 服务端给玩家加上、续时、到期时原版都会把药水同步包发给本人，这里直接读本地玩家身上的效果，不需要新的网络包：
 * 玩家 tick 开头（{@code onLivingUpdate} 之前）把跳跃冷却顶住；
 * 万一还是跳起来了（比如别的模组直接调起跳），在起跳事件里把起跳速度和疾跑起跳的水平加速撤掉。
 * </p>
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class CragbladeJumpLock {

    private CragbladeJumpLock() {
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.side != Side.CLIENT) {
            return;
        }
        EntityPlayer player = event.player;
        if (isLocked(player)) {
            EntityLivingUtil.setJumped(player);
        }
    }

    @SubscribeEvent
    public static void onLivingJump(@Nonnull LivingEvent.LivingJumpEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (!entity.world.isRemote || !isLocked(entity)) {
            return;
        }
        entity.motionY = Math.min(entity.motionY, 0.0D);
        if (entity.isSprinting()) {
            // 原版疾跑起跳会顺着朝向加 0.2 的水平速度，反着减回去
            float yaw = entity.rotationYaw * 0.017453292F;
            entity.motionX += MathHelper.sin(yaw) * 0.2F;
            entity.motionZ -= MathHelper.cos(yaw) * 0.2F;
        }
    }

    private static boolean isLocked(@Nonnull EntityLivingBase entity) {
        return entity == Minecraft.getMinecraft().player && entity.isPotionActive(CarianStylePotion.CRAGBLADE);
    }
}
