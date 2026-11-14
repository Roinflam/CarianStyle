package pers.roinflam.carianstyle.fabric.mixin.pack;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.fabric.FabricDataPack;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 服务端数据包仓库建好时加上本模组的内置数据包源（{@link FabricDataPack#SOURCE}）。
 * <p>
 * 挂在构造器末尾，按「包源里有原版的 {@link ServerPacksSource}」认出是服务端数据包仓库：
 * 专用服、单人新建世界、单人读档都直接 new 这个仓库，挂 {@code ServerPacksSource.createPackRepository} 会漏掉其中几条路。
 * 原版的包源集合是不可变的，先换成按加入先后排的可变集合再加；Fabric API 自己往这里加模组资源包源也是同样做法，
 * 两边谁先执行都不会丢掉对方加的。
 * </p>
 * <p>
 * 同样会加进 {@code ServerPacksSource.createVanillaTrustedRepository} 建的仓库（客户端认识的已知包、游戏测试用）：
 * 包是必选的，那边也会被选上，只是多一份按当时配置生成的数据，不影响联机时以服务器下发为准。
 * </p>
 */
@Mixin(PackRepository.class)
public abstract class FabricPackRepositoryMixin {

    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void carianstyle$addGeneratedSource(RepositorySource[] in, CallbackInfo ci) {
        boolean serverData = false;
        for (RepositorySource source : sources) {
            if (source instanceof ServerPacksSource) {
                serverData = true;
                break;
            }
        }
        if (!serverData) {
            return;
        }
        Set<RepositorySource> copy = new LinkedHashSet<>(sources);
        copy.add(FabricDataPack.SOURCE);
        sources = copy;
    }
}
