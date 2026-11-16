package pers.roinflam.carianstyle.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import pers.roinflam.carianstyle.enchantment.RockBlasterDigScope;

/**
 * 玩家亲手挖方块的两处（挖掘包、补挖）进出 {@link RockBlasterDigScope}，碎岩者据此只从这一格连带挖；
 * 连带挖在中心那一格确实挖掉之后、作用域外面做。
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow
    @Final
    protected ServerPlayer player;

    @Unique
    private boolean carianstyle$dig(ServerPlayerGameMode self, BlockPos pos, Operation<Boolean> original) {
        Runnable chain;
        boolean broken;
        RockBlasterDigScope.enter(this.player, pos);
        try {
            broken = original.call(self, pos);
        } finally {
            chain = RockBlasterDigScope.exit();
        }
        if (broken && chain != null) {
            chain.run();
        }
        return broken;
    }

    @WrapOperation(
            method = "destroyAndAck",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;destroyBlock(Lnet/minecraft/core/BlockPos;)Z")
    )
    private boolean carianstyle$scopeDirectDig(ServerPlayerGameMode self, BlockPos pos, Operation<Boolean> original) {
        return carianstyle$dig(self, pos, original);
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;destroyBlock(Lnet/minecraft/core/BlockPos;)Z")
    )
    private boolean carianstyle$scopeDelayedDig(ServerPlayerGameMode self, BlockPos pos, Operation<Boolean> original) {
        return carianstyle$dig(self, pos, original);
    }
}
