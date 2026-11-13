package pers.roinflam.carianstyle.fabric.mixin.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.impl.EntityJoinLevelEventImpl;

/**
 * 玩家以外的实体加入世界：{@code PersistentEntitySectionManager.addEntity(T, boolean)} 第一行，同 NeoForge。
 * 新生成（第二参 false）、读区块和世界生成（true）都经这一处，第二参就是 loadedFromDisk。
 * <p>
 * 服务端玩家跳过：{@code ServerLevel.addPlayer} 开头已经发过（{@code FabricServerLevelMixin}），
 * 原版 {@code addPlayer} 接着又走到这里；NeoForge 那边是改调不发事件的版本，所以也只发一次。
 * 这个管理器只在服务端用，客户端世界用的是另一个，所以只在服务端发。
 * </p>
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class FabricEntitySectionManagerMixin<T extends EntityAccess> {

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void carianstyle$entityJoinLevel(T access, boolean loadedFromDisk, CallbackInfoReturnable<Boolean> cir) {
        if (access instanceof Entity entity && !(entity instanceof ServerPlayer)) {
            CarianBus.post(new EntityJoinLevelEventImpl(entity, entity.level(), loadedFromDisk));
        }
    }
}
