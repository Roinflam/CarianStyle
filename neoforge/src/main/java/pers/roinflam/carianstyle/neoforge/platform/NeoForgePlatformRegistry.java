package pers.roinflam.carianstyle.neoforge.platform;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pers.roinflam.carianstyle.platform.PlatformRegistry;
import pers.roinflam.carianstyle.platform.Services;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * NeoForge 上的 {@link PlatformRegistry}：每个注册表第一次用到时建一个 {@link DeferredRegister}，
 * 返回它给的 {@link DeferredHolder}（既是 {@code Supplier} 也是 {@code Holder}）。
 * <p>
 * 主类构造器在所有内容声明完之后调 {@link #attach}，把这些 {@code DeferredRegister} 挂到模组总线上；
 * 挂上之后再声明会抛异常（注册事件可能已经过去了，晚声明的内容不会进注册表，进游戏才发现缺东西）。
 * {@code DeferredRegister} 按第一次用到的先后挂，先后不影响结果：注册事件按原版注册表的顺序逐个发。
 * </p>
 */
public final class NeoForgePlatformRegistry implements PlatformRegistry {

    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new LinkedHashMap<>();
    private boolean attached;

    /** 把已经建出来的 {@code DeferredRegister} 全部挂到模组总线。只调一次。 */
    public static void attach(@Nonnull IEventBus modEventBus) {
        ((NeoForgePlatformRegistry) Services.registry()).attachAll(modEventBus);
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
    public <R, T extends R> Supplier<T> register(@Nonnull ResourceKey<? extends Registry<R>> registry, @Nonnull String path,
                                                 @Nonnull Supplier<T> factory) {
        return declare(registry, path, factory);
    }

    @Override
    @Nonnull
    public <R, T extends R> Holder<R> registerHolder(@Nonnull ResourceKey<? extends Registry<R>> registry,
                                                    @Nonnull String path, @Nonnull Supplier<T> factory) {
        return declare(registry, path, factory);
    }

    @Nonnull
    private synchronized <R, T extends R> DeferredHolder<R, T> declare(@Nonnull ResourceKey<? extends Registry<R>> registry,
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
