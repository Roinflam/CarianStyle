package pers.roinflam.carianstyle.fabric.mixin.pack;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;
import java.util.List;

/**
 * 调 {@code PackRepository#rebuildSelected}：按一组包 id 算出真正会被选上的包（补上必选包、按位置规则排好），不改仓库状态。
 */
@Mixin(PackRepository.class)
public interface PackRepositoryInvoker {

    @Invoker("rebuildSelected")
    List<Pack> carianstyle$rebuildSelected(Collection<String> ids);
}
