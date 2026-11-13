package pers.roinflam.carianstyle.fabric.mixin.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.impl.EntityJoinLevelEventImpl;

/**
 * 玩家加入世界：{@code ServerLevel.addPlayer} 第一行，同 NeoForge。登录、重生、切维度都经这里。
 * <p>
 * {@code addPlayer} 后面（偏移 56）还会走到 {@code PersistentEntitySectionManager.addEntity}，那边跳过服务端玩家，
 * 不会发两次。Fabric 不用 {@code ENTITY_LOAD}：它在加入之后、也给不出 loadedFromDisk。
 * 只在服务端发：现有的加入世界监听都判了端，以后有客户端监听再补 {@code ClientLevel} 那两处。
 * </p>
 */
@Mixin(ServerLevel.class)
public abstract class FabricServerLevelMixin {

    @Inject(method = "addPlayer", at = @At("HEAD"))
    private void carianstyle$playerJoinLevel(ServerPlayer player, CallbackInfo ci) {
        CarianBus.post(new EntityJoinLevelEventImpl(player, (ServerLevel) (Object) this, false));
    }
}
