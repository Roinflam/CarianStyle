package pers.roinflam.carianstyle.fabric.mixin.pack;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pers.roinflam.carianstyle.annotation.pack.GeneratedDataPack;

/**
 * 原版把没有已知包信息的数据包条目一律登记成实验性，内置包的附魔因此让整个世界被判成实验性，每次读档都弹备份确认。
 * 只把出自内置包的条目改成稳定，已知包信息仍然留空，联机时照旧整条下发给客户端。
 */
@Mixin(RegistryDataLoader.class)
public abstract class FabricRegistryDataLoaderMixin {

    @ModifyVariable(method = "loadElementFromResource", at = @At("HEAD"), argsOnly = true)
    private static RegistrationInfo carianstyle$stableForGeneratedPack(RegistrationInfo info,
                                                                       @Local(argsOnly = true) Resource resource) {
        return resource.source() instanceof GeneratedDataPack ? RegistrationInfo.BUILT_IN : info;
    }
}
