package pers.roinflam.carianstyle.forge.platform;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import pers.roinflam.carianstyle.platform.PlatformRegistry;
import pers.roinflam.carianstyle.platform.Services;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 每个注册表第一次用到时建一个 {@link DeferredRegister}，返回它给的 {@code RegistryObject}。
 * 主类在全部内容声明完后调 {@link #attach}；挂上之后再声明会抛异常，免得晚声明的内容静默进不了注册表。
 */
public final class ForgePlatformRegistry implements PlatformRegistry {

    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new LinkedHashMap<>();
    private boolean attached;

    public static void attach(@Nonnull IEventBus modEventBus) {
        ((ForgePlatformRegistry) Services.registry()).attachAll(modEventBus);
    }

    private synchronized void attachAll(@Nonnull IEventBus modEventBus) {
        if (attached) {
            throw new IllegalStateException("注册器已经挂到模组总线上了");
        }
        attached = true;
        for (DeferredRegister<?> register : registers.values()) {
            register.register(modEventBus);
        }
    }

    @Override
    @Nonnull
    public synchronized <R, T extends R> Supplier<T> register(@Nonnull ResourceKey<? extends Registry<R>> registry,
                                                              @Nonnull String path, @Nonnull Supplier<T> factory) {
        if (attached) {
            throw new IllegalStateException("注册器已经挂上模组总线，不能再声明 " + registry.location() + " / " + path);
        }
        @SuppressWarnings("unchecked")
        DeferredRegister<R> register = (DeferredRegister<R>) registers.computeIfAbsent(registry,
                key -> DeferredRegister.create(registry, Reference.MOD_ID));
        // 同名重复声明由 DeferredRegister 自己抛异常
        return register.register(path, factory);
    }
}
