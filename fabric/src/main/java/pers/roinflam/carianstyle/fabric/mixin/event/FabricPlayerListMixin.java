package pers.roinflam.carianstyle.fabric.mixin.event;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.impl.PlayerLoggedInEventImpl;
import pers.roinflam.carianstyle.event.impl.PlayerLoggedOutEventImpl;

/**
 * 玩家登录 / 下线。两个游戏版本都自己写 mixin：1.20.1 的 Fabric API 没有 {@code JOIN} / {@code LEAVE}，
 * 为两版一致 1.21.1 也不用。
 * <p>
 * 先后（和 NeoForge 一致）：登录是 {@code addNewPlayer} 里的加入世界在前、登录在 {@code placeNewPlayer} 最后；
 * 下线是 {@code remove} 第一行发、之后才存盘（偏移 14）和移出世界。
 * </p>
 */
@Mixin(PlayerList.class)
public abstract class FabricPlayerListMixin {

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void carianstyle$loggedIn(Connection connection, ServerPlayer player, CommonListenerCookie cookie,
                                      CallbackInfo ci) {
        CarianBus.post(new PlayerLoggedInEventImpl(player));
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void carianstyle$loggedOut(ServerPlayer player, CallbackInfo ci) {
        CarianBus.post(new PlayerLoggedOutEventImpl(player));
    }
}
