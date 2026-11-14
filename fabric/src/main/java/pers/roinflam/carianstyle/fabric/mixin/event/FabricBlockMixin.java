package pers.roinflam.carianstyle.fabric.mixin.event;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.fabric.RockBlasterExperience;

/**
 * 碎岩连带挖的格子掉的经验减半，见 {@link RockBlasterExperience}。整数除法和 Forge 版的 {@code getExpDrop / 2} 一致。
 * 1.20.1 不发方块掉落事件（Forge 47 没有），这里只管这一件事。
 */
@Mixin(Block.class)
public abstract class FabricBlockMixin {

    @Inject(method = "popExperience", at = @At("HEAD"))
    private void carianstyle$rockBlasterHalf(ServerLevel level, BlockPos pos, int ignored, CallbackInfo ci,
                                             @Local(argsOnly = true) LocalIntRef experience) {
        if (RockBlasterExperience.consume(level, pos)) {
            experience.set(experience.get() / 2);
        }
    }
}
