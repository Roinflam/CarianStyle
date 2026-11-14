package pers.roinflam.carianstyle.fabric.platform;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.carianstyle.platform.PlatformRegistry;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Fabric 上的 {@link PlatformRegistry}：当场建对象、当场写进原版注册表。
 * <p>
 * Fabric 没有延迟注册，声明的先后就是建对象的先后：{@code CarianContent.bootstrap} 按方块、方块实体、实体、物品、
 * 药水效果的顺序声明，后面的工厂用到前面的内容时已经注册好了。必须在模组入口 {@code onInitialize} 里调完，
 * 注册表冻结之后再注册，原版会直接抛异常。同名重复注册也由原版注册表抛异常。
 * </p>
 */
public final class FabricPlatformRegistry implements PlatformRegistry {

    @Override
    @Nonnull
    public <R, T extends R> Supplier<T> register(@Nonnull ResourceKey<? extends Registry<R>> registry, @Nonnull String path,
                                                 @Nonnull Supplier<T> factory) {
        T value = Registry.register(lookup(registry), id(path), factory.get());
        return () -> value;
    }

    /** 返回注册表里的那个 {@link Holder.Reference}，和别处从注册表取到的是同一个对象。 */
    @Override
    @Nonnull
    public <R, T extends R> Holder<R> registerHolder(@Nonnull ResourceKey<? extends Registry<R>> registry,
                                                    @Nonnull String path, @Nonnull Supplier<T> factory) {
        return Registry.registerForHolder(lookup(registry), id(path), factory.get());
    }

    @Nonnull
    private static ResourceLocation id(@Nonnull String path) {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, path);
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static <R> Registry<R> lookup(@Nonnull ResourceKey<? extends Registry<R>> key) {
        Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.get(key.location());
        if (registry == null) {
            throw new IllegalArgumentException("不是内置注册表：" + key.location());
        }
        return registry;
    }
}
