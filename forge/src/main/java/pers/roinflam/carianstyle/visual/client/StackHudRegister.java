package pers.roinflam.carianstyle.visual.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 叠层 HUD 覆盖层注册（客户端，MOD 总线）。
 *
 * @author FlameForge
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class StackHudRegister {

    private StackHudRegister() {
    }

    /**
     * 注册覆盖层。
     *
     * @param event 注册事件
     */
    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("carianstyle_stack_hud", StackHudOverlay.INSTANCE);
    }
}
