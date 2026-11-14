package pers.roinflam.carianstyle.fabric.mixin.event;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.impl.PlayerLoggedInEventImpl;
import pers.roinflam.carianstyle.event.impl.PlayerLoggedOutEventImpl;

/**
 * 玩家登录 / 下线。1.20.1 的 Fabric API 没有 {@code JOIN} / {@code LEAVE}，{@code DISCONNECT} 又漏掉局域网主机关服那条路，
 * 所以自己注入，位置同 Forge：登录在 {@code placeNewPlayer} 最后（加入世界之后），下线在 {@code remove} 第一行（存盘之前）。
 */
@Mixin(PlayerList.class)
public abstract class FabricPlayerListMixin {

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void carianstyle$loggedIn(Connection connection, ServerPlayer player, CallbackInfo ci) {
        CarianBus.post(new PlayerLoggedInEventImpl(player));
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void carianstyle$loggedOut(ServerPlayer player, CallbackInfo ci) {
        CarianBus.post(new PlayerLoggedOutEventImpl(player));
    }
}
