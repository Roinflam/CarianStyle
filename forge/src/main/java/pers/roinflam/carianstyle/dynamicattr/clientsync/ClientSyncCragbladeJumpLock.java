package pers.roinflam.carianstyle.dynamicattr.clientsync;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 岩石剑的「无法跳跃」在客户端的那一半
 * <p>
 * 玩家的跳跃是客户端 {@code aiStep} 发起的，服务端设跳跃冷却拦不住。
 * 这里查序列号 20，在玩家 tick 开头（{@code aiStep} 之前）把本地玩家的跳跃冷却顶住。
 * </p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ClientSyncCragbladeJumpLock {

    public static final int CRAGBLADE_SERIAL = 20;

    private ClientSyncCragbladeJumpLock() {
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.player != Minecraft.getInstance().player) {
            return;
        }
        if (ClientSyncEffectManager.shouldRenderEffect(CRAGBLADE_SERIAL, event.player.getId())) {
            EntityLivingUtil.setJumped(event.player);
        }
    }
}
