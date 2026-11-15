package pers.roinflam.carianstyle.fabric.platform;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.carianstyle.platform.PlatformRegistry;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.function.Supplier;

/**
 * Fabric 上的 {@link PlatformRegistry}：当场建对象、当场写进原版注册表。
 * <p>
 * Fabric 没有延迟注册，声明的先后就是建对象的先后：{@code CarianContent.bootstrap} 按方块、方块实体、实体、物品、
 * 药水效果的顺序声明，附魔随后按清单声明，后面的工厂用到前面的内容时已经注册好了。必须在模组入口
 * {@code onInitialize} 里调完，注册表冻结之后再注册，原版会直接抛异常。同名重复注册也由原版注册表抛异常。
 * </p>
 */
public final class FabricPlatformRegistry implements PlatformRegistry {

    @Override
    public <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String path,
                                                 Supplier<T> factory) {
        T value = Registry.register(lookup(registry), new ResourceLocation(Reference.MOD_ID, path), factory.get());
        return () -> value;
    }

    @SuppressWarnings("unchecked")
    private static <R> Registry<R> lookup(ResourceKey<? extends Registry<R>> key) {
        Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.get(key.location());
        if (registry == null) {
            throw new IllegalArgumentException("不是内置注册表：" + key.location());
        }
        return registry;
    }
}
