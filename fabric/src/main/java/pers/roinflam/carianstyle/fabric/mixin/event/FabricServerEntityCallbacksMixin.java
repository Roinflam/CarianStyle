package pers.roinflam.carianstyle.fabric.mixin.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.impl.EntityLeaveLevelEventImpl;

/**
 * 实体离开世界：{@code ServerLevel$EntityCallbacks.onTrackingEnd} 末尾，同 NeoForge（它也发在这个方法最后）。
 * 下线、死亡重生移除旧玩家、切维度、普通实体被移除都经这里。不用 Fabric API 的 {@code ENTITY_UNLOAD}：它在方法开头。
 * <p>
 * 世界取外层 {@code ServerLevel}：内部类的外层引用是编译器生成的合成字段，Mojmap 不给它起名，
 * 开发和生产环境都叫中间名 {@code field_26936}（Fabric API 自己的同位置 mixin 也这么取）。
 * </p>
 */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class FabricServerEntityCallbacksMixin {

    @SuppressWarnings("ShadowTarget")
    @Shadow
    @Final
    ServerLevel field_26936;

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void carianstyle$entityLeaveLevel(Entity entity, CallbackInfo ci) {
        CarianBus.post(new EntityLeaveLevelEventImpl(entity, field_26936));
    }
}
