package pers.roinflam.carianstyle.fabric.mixin.client;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读 {@code ItemRenderer} 的私有字段 {@code blockEntityRenderer}（原版没有取它的方法）。
 * <p>
 * 盾牌这类物品的几何不在烘焙模型里，而是由这个渲染器现画；截取盾牌外形时，物品没有单独登记的内置渲染器，
 * 就退回用它。NeoForge 上同样的事经物品客户端扩展取，不用这个 accessor。
 * </p>
 */
@Mixin(ItemRenderer.class)
public interface ItemRendererAccessor {

    @Accessor("blockEntityRenderer")
    BlockEntityWithoutLevelRenderer carianstyle$getBlockEntityRenderer();
}
