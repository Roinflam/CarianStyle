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
 * 1.20.1 断开都走 {@code Minecraft#clearLevel(Screen)}，发在里面 {@code GameRenderer#resetData()} 之后：
 * 和 Forge 发 {@code LoggingOut} 是同一句，玩家和世界都还在，就在渲染线程上同步发。
 * 不用 Fabric API 的连接断开回调：它常在网络线程触发、要排进渲染线程的任务队列，
 * 而这个方法开头的 {@code dropAllTasks()} 会把队列清掉，事件可能丢。
 * </p>
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftDisconnect {

    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;resetData()V",
                    shift = At.Shift.AFTER))
    private void carianstyle$afterResetData(Screen nextScreen, CallbackInfo ci) {
        FabricClientEvents.onDisconnect();
    }
}
