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
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.BlockDropsEvent;
import pers.roinflam.carianstyle.event.impl.BlockDropsEventImpl;

/**
 * 方块掉经验：{@code Block.popExperience(ServerLevel, BlockPos, int)} 第一行改经验值。
 * <p>
 * 和 NeoForge 位置不同：NeoForge 在 {@code dropResources} 里截掉落物和经验一起发；原版这里只有经验，
 * 经验为 0 时上游根本不调这个方法，所以也不发，掉落物列表也给不出。本模组唯一的监听只按坐标过滤、改经验，结果一致。
 * 方块状态取这个方块的默认状态（这个方法拿不到挖掉的那个状态，监听也不读它）。
 * 别处直接调 {@code popExperience} 的会多发，无害。
 * </p>
 */
@Mixin(Block.class)
public abstract class FabricBlockMixin {

    @Inject(method = "popExperience", at = @At("HEAD"))
    private void carianstyle$blockDrops(ServerLevel level, BlockPos pos, int ignored, CallbackInfo ci,
                                        @Local(argsOnly = true) LocalIntRef experience) {
        Block self = (Block) (Object) this;
        BlockDropsEvent event = CarianBus.post(
                new BlockDropsEventImpl(level, pos, self.defaultBlockState(), experience.get()));
        experience.set(event.getDroppedExperience());
    }
}
