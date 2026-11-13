package pers.roinflam.carianstyle.fabric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.fabric.FabricClientEvents;

/**
 * 客户端断开（退出到标题、被踢、单人关世界）。
 * <p>
 * 发在 {@code Minecraft#disconnect(Screen, boolean)} 里 {@code GameRenderer#resetData()} 之后、
 * {@code gameMode} 置空之前：和 NeoForge 发 {@code LoggingOut} 是同一句，这时玩家和世界都还在，
 * 而且就在渲染线程上同步发。不用 Fabric API 的连接断开回调，是因为它常在网络线程上触发、要排进渲染线程的任务队列，
 * 而这个方法开头的 {@code dropAllTasks()} 会把队列清掉，事件可能整个丢掉；即使没丢，也要等世界卸掉之后才跑。
 * 和 NeoForge 一样每次调这个方法都发，不管当时有没有连接。
 * </p>
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftDisconnect {

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;resetData()V",
                    shift = At.Shift.AFTER))
    private void carianstyle$afterResetData(Screen nextScreen, boolean keepResourcePacks, CallbackInfo ci) {
        FabricClientEvents.onDisconnect();
    }
}
