package pers.roinflam.carianstyle.fabric.mixin.pack;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 重载数据包时，先按仓库的规则把要加载的包 id 补全（补上必选包、排好位置），再交给原版。
 * <p>
 * 原版 {@code reloadResources(ids)} 直接按传进来的 id 打开资源，最后才 {@code setSelected(ids)} 把必选包补回已选列表。
 * {@code /datapack disable "carianstyle:generated"} 之后的那次重载，传进来的 id 里没有本模组的包：
 * 已选列表里它还在，但这一次加载的标签、战利品表里没有它的内容，附魔的获取途径标签就少了一截，下次重载才恢复。
 * NeoForge 在同一处改成先补全，这里照它做。传进来的 id 已经齐全时（平常的 {@code /reload}），补全前后一样。
 * </p>
 */
@Mixin(MinecraftServer.class)
public abstract class FabricMinecraftServerReloadMixin {

    @ModifyVariable(method = "reloadResources", at = @At("HEAD"), argsOnly = true)
    private Collection<String> carianstyle$includeRequiredPacks(Collection<String> ids) {
        List<Pack> packs = ((PackRepositoryInvoker) ((MinecraftServer) (Object) this).getPackRepository())
                .carianstyle$rebuildSelected(ids);
        List<String> out = new ArrayList<>(packs.size());
        for (Pack pack : packs) {
            out.add(pack.getId());
        }
        return out;
    }
}
